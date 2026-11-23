package com.tstfaasaopwsw

import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Décide, pour un paquet DNS sortant, s'il faut bloquer (NXDOMAIN) ou relayer. */
object DnsFilter {

    private const val UPSTREAM_DNS = "1.1.1.1"

    /** Traite un paquet IPv4/UDP vers le DNS virtuel : filtre ou relaie, puis répond. */
    fun handle(pkt: ByteArray, len: Int, blocked: Set<String>, out: FileOutputStream, upstream: DatagramSocket) {
        if (len < 28) return
        if ((pkt[0].toInt() ushr 4 and 0xF) != 4) return   // IPv4 seulement
        val ihl = (pkt[0].toInt() and 0xF) * 4
        if (pkt[9].toInt() and 0xFF != 17) return           // UDP seulement
        val dnsStart = ihl + 8
        if (dnsStart >= len) return

        // Niveau DNS = domaine seulement. Un motif URL (ex. https://x.com/path) bloque
        // ici son hôte entier (x.com) ; le filtrage par chemin reste au navigateur maison.
        val domain = (DnsPacket.question(pkt, dnsStart, len) ?: return).lowercase()
        val isBlocked = blocked.any { UrlRules.matchesDomain(domain, it) }

        // On inverse source <-> destination pour construire la réponse.
        val srcIp = pkt.copyOfRange(12, 16)
        val dstIp = pkt.copyOfRange(16, 20)
        val srcPort = ((pkt[ihl].toInt() and 0xFF) shl 8) or (pkt[ihl + 1].toInt() and 0xFF)

        val dnsResponse: ByteArray = if (isBlocked) {
            DnsPacket.nxdomain(pkt, dnsStart, len)
        } else {
            resolve(pkt.copyOfRange(dnsStart, len), upstream) ?: return
        }
        out.write(DnsPacket.buildUdpReply(dstIp, srcIp, srcPort, dnsResponse))
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
}
