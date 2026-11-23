package com.tstfaasaopwsw

/** Low-level read/write of the DNS and IPv4/UDP packets handled by [DnsFilter]. */
object DnsPacket {

    /** Queried domain name (Question section, uncompressed), or null. */
    fun question(pkt: ByteArray, dnsStart: Int, len: Int): String? {
        var i = dnsStart + 12 // skip the DNS header (12 bytes)
        val sb = StringBuilder()
        while (i < len) {
            val l = pkt[i].toInt() and 0xFF
            if (l == 0) break
            if (l and 0xC0 != 0) return null // no compression expected in the question
            if (i + 1 + l > len) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (k in 1..l) sb.append((pkt[i + k].toInt() and 0xFF).toChar())
            i += l + 1
        }
        return sb.toString().ifEmpty { null }
    }

    /** NXDOMAIN reply: reuse the query, flip the flags. */
    fun nxdomain(pkt: ByteArray, dnsStart: Int, len: Int): ByteArray {
        val dns = pkt.copyOfRange(dnsStart, len)
        dns[2] = (dns[2].toInt() or 0x80).toByte() // QR = 1 (reply)
        dns[3] = 0x83.toByte()                      // RA = 1, RCODE = 3 (NXDOMAIN)
        dns[6] = 0; dns[7] = 0                       // ANCOUNT = 0
        return dns
    }

    /** Wraps a DNS payload in an IPv4/UDP packet (DNS:53 -> app port). */
    fun buildUdpReply(srcIp: ByteArray, dstIp: ByteArray, dstPort: Int, dns: ByteArray): ByteArray {
        val udpLen = 8 + dns.size
        val total = 20 + udpLen
        val p = ByteArray(total)
        // IPv4 header
        p[0] = 0x45; p[1] = 0
        p[2] = (total ushr 8).toByte(); p[3] = total.toByte()
        p[8] = 64; p[9] = 17 // TTL, UDP protocol
        System.arraycopy(srcIp, 0, p, 12, 4)
        System.arraycopy(dstIp, 0, p, 16, 4)
        val ipChecksum = checksum(p, 0, 20)
        p[10] = (ipChecksum ushr 8).toByte(); p[11] = ipChecksum.toByte()
        // UDP header (checksum left at 0: optional in IPv4)
        p[20] = 0; p[21] = 53 // source port = 53
        p[22] = (dstPort ushr 8).toByte(); p[23] = dstPort.toByte()
        p[24] = (udpLen ushr 8).toByte(); p[25] = udpLen.toByte()
        System.arraycopy(dns, 0, p, 28, dns.size)
        return p
    }

    /** Internet checksum (16-bit one's complement). */
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
}
