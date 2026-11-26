package com.tstfaasaopwsw

import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Decides, for an outgoing DNS packet, whether to block (NXDOMAIN) or relay. */
object DnsFilter {

    private const val UPSTREAM_DNS = "1.1.1.1"

    /**
     * Handles an IPv4/UDP packet to the virtual DNS: filter or relay, then reply. [blocked]
     * is the small set of controlled patterns (scanned); [imported] is the big imported
     * blocklist, matched by label walk (O(labels)), so tens of thousands of domains cost
     * nothing per query.
     */
    fun handle(pkt: ByteArray, len: Int, blocked: Set<String>, imported: Set<String>, out: FileOutputStream, upstream: DatagramSocket) {
        if (len < 28) return
        if ((pkt[0].toInt() ushr 4 and 0xF) != 4) return   // IPv4 only
        val ihl = (pkt[0].toInt() and 0xF) * 4
        if (pkt[9].toInt() and 0xFF != 17) return           // UDP only
        val dnsStart = ihl + 8
        if (dnsStart >= len) return

        // DNS level = domain only. A URL pattern (e.g. https://x.com/path) blocks its
        // whole host here (x.com); path filtering stays in the in-house browser.
        val domain = (DnsPacket.question(pkt, dnsStart, len) ?: return).lowercase()
        val isBlocked = blocked.any { UrlRules.matchesDomain(domain, it) } || suffixMatch(domain, imported)

        // Swap source <-> destination to build the reply.
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

    /** Relays the query to the upstream resolver and returns its raw reply. */
    private fun resolve(query: ByteArray, socket: DatagramSocket): ByteArray? {
        val server = InetAddress.getByName(UPSTREAM_DNS)
        socket.send(DatagramPacket(query, query.size, server, 53))
        val reply = ByteArray(32_767)
        val dp = DatagramPacket(reply, reply.size)
        socket.receive(dp)
        return reply.copyOf(dp.length)
    }
}
