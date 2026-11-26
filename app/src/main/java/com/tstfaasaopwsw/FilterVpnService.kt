package com.tstfaasaopwsw

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket

/**
 * Network firewall, domain level, for the WHOLE phone.
 *
 * A local VPN that captures **DNS only**: we route to ourselves only the queries to our
 * virtual resolver ([VIRT_DNS]); everything else goes direct, so a failure of this service
 * does not cut the network. Each DNS query is filtered or relayed by [DnsFilter] (NXDOMAIN
 * if the domain is one of [Store.controlledDomains] — any site we control, closed to every
 * app but our own VPN-excluded browser).
 *
 * Accepted limit: it does not see encrypted DNS (DoH/DoT). [FirewallPolicy] therefore
 * forces "Private DNS" to opportunistic mode so DNS goes back to cleartext here.
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
        val builder = Builder()
            .setSession("TSTFAASAOPWSW")
            .addAddress(VIRT_ADDR, 32)
            .addDnsServer(VIRT_DNS)
            .addRoute(VIRT_DNS, 32) // only queries to our DNS go through the tunnel
            .setBlocking(true)
        // We exclude ourselves: our browser already filters by URL, and our own traffic
        // (browser, updater) must not be captured by the tunnel (otherwise it is cut off).
        runCatching { builder.addDisallowedApplication(packageName) }
        val pfd = builder.establish() ?: return
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
                runCatching { DnsFilter.handle(buf, n, store.controlledDomains(), store.allImportedDomains(), output, upstream) }
            }
        } catch (_: Exception) {
            // tun closed / thread interrupted: normal end.
        } finally {
            runCatching { upstream.close() }
        }
    }

    companion object {
        const val ACTION_STOP = "com.tstfaasaopwsw.VPN_STOP"
        private const val VIRT_ADDR = "10.111.222.1"
        private const val VIRT_DNS = "10.111.222.2"
    }
}
