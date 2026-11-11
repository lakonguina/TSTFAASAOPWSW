package com.khazaddum.forcelist

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Pare-feu réseau, niveau domaine, pour TOUT le téléphone.
 *
 * Un VPN local qui ne capte **que le DNS** : on ne route vers nous que les requêtes
 * vers notre résolveur virtuel ([VIRT_DNS]) ; tout le reste du trafic file en direct,
 * donc une panne de ce service ne coupe pas l'accès réseau. Pour chaque requête DNS :
 *   - domaine contenant un motif bloqué ([Store.blockedWeb]) -> réponse NXDOMAIN
 *     (l'app, quelle qu'elle soit, n'obtient aucune IP) ;
 *   - sinon -> on relaie vers un résolveur amont et on renvoie sa réponse.
 *
 * Limite assumée : ne voit pas le DNS chiffré (DoH/DoT). Le Device Owner force donc
 * le « DNS privé » sur off (voir [PolicyManager]) pour que le DNS passe en clair ici.
 */
class FilterVpnService : VpnService() {

    @Volatile private var running = false
    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stop(); return START_NOT_STICKY }
        if (!running) start()
        return START_STICKY
    }

    private fun start() {
        val pfd = Builder()
            .setSession("ForceList")
            .addAddress(VIRT_ADDR, 32)
            .addDnsServer(VIRT_DNS)
            .addRoute(VIRT_DNS, 32) // seules les requêtes vers notre DNS passent par le tunnel
            .setBlocking(true)
            .establish() ?: return
        tun = pfd
        running = true
        worker = Thread { loop(pfd.fileDescriptor) }.apply { isDaemon = true; start() }
    }

    private fun stop() {
        running = false
        worker?.interrupt(); worker = null
        runCatching { tun?.close() }; tun = null
        stopSelf()
    }

    override fun onDestroy() { stop(); super.onDestroy() }

    private fun loop(fd: FileDescriptor) {
        val store = Store.get(this)
        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)
        val upstream = DatagramSocket().also { protect(it); it.soTimeout = 5000 }
        val buf = ByteArray(32_767)
        try {
            while (running) {
                val n = input.read(buf)
                if (n <= 0) continue
                runCatching { handle(buf, n, store.blockedWeb, output, upstream) }
            }
        } catch (_: Exception) {
            // tun fermé / thread interrompu : fin normale.
        } finally {
            runCatching { upstream.close() }
        }
    }

    /** Traite un paquet IPv4/UDP sortant vers le DNS virtuel : filtre ou relaie. */
    private fun handle(pkt: ByteArray, len: Int, blocked: Set<String>, out: FileOutputStream, upstream: DatagramSocket) {
        if (len < 28) return
        if ((pkt[0].toInt() ushr 4 and 0xF) != 4) return   // IPv4 seulement
        val ihl = (pkt[0].toInt() and 0xF) * 4
        if (pkt[9].toInt() and 0xFF != 17) return           // UDP seulement
        val dnsStart = ihl + 8
        if (dnsStart >= len) return

        val domain = dnsQuestion(pkt, dnsStart, len) ?: return
        val isBlocked = blocked.any { domain.contains(it, ignoreCase = true) }

        // Adresses pour construire la réponse (on inverse source <-> destination).
        val srcIp = pkt.copyOfRange(12, 16)
        val dstIp = pkt.copyOfRange(16, 20)
        val srcPort = ((pkt[ihl].toInt() and 0xFF) shl 8) or (pkt[ihl + 1].toInt() and 0xFF)

        val dnsResponse: ByteArray = if (isBlocked) {
            nxdomain(pkt, dnsStart, len) // réponse « ce domaine n'existe pas »
        } else {
            val query = pkt.copyOfRange(dnsStart, len)
            resolve(query, upstream) ?: return
        }
        out.write(buildUdpReply(dstIp, srcIp, srcPort, dnsResponse))
    }

    /** Extrait le nom de domaine interrogé (section Question, non compressée). */
    private fun dnsQuestion(pkt: ByteArray, dnsStart: Int, len: Int): String? {
        var i = dnsStart + 12 // saute l'en-tête DNS (12 octets)
        val sb = StringBuilder()
        while (i < len) {
            val l = pkt[i].toInt() and 0xFF
            if (l == 0) break
            if (l and 0xC0 != 0) return null // pas de compression attendue dans la question
            if (i + 1 + l > len) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (k in 1..l) sb.append((pkt[i + k].toInt() and 0xFF).toChar())
            i += l + 1
        }
        return sb.toString().ifEmpty { null }
    }

    /** Réponse NXDOMAIN : on reprend la requête, on bascule les drapeaux. */
    private fun nxdomain(pkt: ByteArray, dnsStart: Int, len: Int): ByteArray {
        val dns = pkt.copyOfRange(dnsStart, len)
        dns[2] = (dns[2].toInt() or 0x80).toByte() // QR = 1 (réponse)
        dns[3] = 0x83.toByte()                      // RA = 1, RCODE = 3 (NXDOMAIN)
        dns[6] = 0; dns[7] = 0                       // ANCOUNT = 0
        return dns
    }

    /** Relaie la requête vers le résolveur amont et renvoie sa réponse brute. */
    private fun resolve(query: ByteArray, socket: DatagramSocket): ByteArray? {
        val server = InetAddress.getByName(UPSTREAM_DNS)
        socket.send(DatagramPacket(query, query.size, server, 53))
        val reply = ByteArray(32_767)
        val dp = DatagramPacket(reply, reply.size)
        socket.receive(dp)
        return reply.copyOf(dp.length)
    }

    /** Emballe une charge DNS dans un paquet IPv4/UDP (DNS:53 -> port de l'app). */
    private fun buildUdpReply(srcIp: ByteArray, dstIp: ByteArray, dstPort: Int, dns: ByteArray): ByteArray {
        val udpLen = 8 + dns.size
        val total = 20 + udpLen
        val p = ByteArray(total)
        // En-tête IPv4
        p[0] = 0x45; p[1] = 0
        p[2] = (total ushr 8).toByte(); p[3] = total.toByte()
        p[8] = 64; p[9] = 17 // TTL, protocole UDP
        System.arraycopy(srcIp, 0, p, 12, 4)
        System.arraycopy(dstIp, 0, p, 16, 4)
        val ipChecksum = checksum(p, 0, 20)
        p[10] = (ipChecksum ushr 8).toByte(); p[11] = ipChecksum.toByte()
        // En-tête UDP (checksum laissé à 0 : optionnel en IPv4)
        p[20] = 0; p[21] = 53 // port source = 53
        p[22] = (dstPort ushr 8).toByte(); p[23] = dstPort.toByte()
        p[24] = (udpLen ushr 8).toByte(); p[25] = udpLen.toByte()
        System.arraycopy(dns, 0, p, 28, dns.size)
        return p
    }

    /** Somme de contrôle Internet (complément à un sur 16 bits). */
    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (length % 2 == 1) sum += (data[offset + length - 1].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv() and 0xFFFF
    }

    companion object {
        const val ACTION_STOP = "com.khazaddum.forcelist.VPN_STOP"
        private const val VIRT_ADDR = "10.111.222.1"
        private const val VIRT_DNS = "10.111.222.2"
        private const val UPSTREAM_DNS = "1.1.1.1"
    }
}
