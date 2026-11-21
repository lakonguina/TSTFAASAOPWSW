package com.khazaddum.forcelist

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket

/**
 * Pare-feu réseau, niveau domaine, pour TOUT le téléphone.
 *
 * Un VPN local qui ne capte **que le DNS** : on ne route vers nous que les requêtes
 * vers notre résolveur virtuel ([VIRT_DNS]) ; tout le reste file en direct, donc une
 * panne de ce service ne coupe pas le réseau. Chaque requête DNS est filtrée ou relayée
 * par [DnsFilter] (NXDOMAIN si le domaine contient un motif de [Store.blockedWeb]).
 *
 * Limite assumée : ne voit pas le DNS chiffré (DoH/DoT). [FirewallPolicy] force donc
 * le « DNS privé » en mode opportuniste pour que le DNS repasse en clair ici.
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
            .setSession("Khazad-dûm")
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
                runCatching { DnsFilter.handle(buf, n, store.blockedWeb, output, upstream) }
            }
        } catch (_: Exception) {
            // tun fermé / thread interrompu : fin normale.
        } finally {
            runCatching { upstream.close() }
        }
    }

    companion object {
        const val ACTION_STOP = "com.khazaddum.forcelist.VPN_STOP"
        private const val VIRT_ADDR = "10.111.222.1"
        private const val VIRT_DNS = "10.111.222.2"
    }
}
