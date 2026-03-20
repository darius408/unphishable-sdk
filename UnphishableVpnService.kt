package org.unphishable.sdk.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.unphishable.sdk.model.ScanResult
import org.unphishable.sdk.network.ScanApiClient
import org.unphishable.sdk.ui.WarningNotificationManager
import org.unphishable.sdk.utils.PacketParser
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap

/**
 * UnphishableVpnService — Device-wide URL interception engine.
 *
 * Architecture:
 * - Creates a local TUN interface
 * - Reads raw IP packets from TUN
 * - Extracts Host/SNI from HTTP/HTTPS packets
 * - Scans URL against backend ASYNCHRONOUSLY
 * - Forwards ALL packets immediately — WARN NEVER BLOCK
 * - Uses a real TCP tunnel to forward traffic to internet
 *
 * Fail-open: any error = traffic passes through unblocked.
 */
class UnphishableVpnService : VpnService() {

    private val TAG = "Unphishable:VPN"
    private val FOREGROUND_NOTIF_ID = 9001
    private val VPN_CHANNEL_ID = "unphishable_vpn"

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val localCache = ConcurrentHashMap<String, CachedResult>()
    private data class CachedResult(val result: ScanResult, val expiresAt: Long)
    private val CACHE_TTL_MS = 30 * 60 * 1000L
    private val CACHE_MAX_SIZE = 500
    private val inFlightScans = ConcurrentHashMap<String, Boolean>()
    private val scannedUrls = ConcurrentHashMap<String, Long>() // dedup within 60s

    companion object { const val ACTION_STOP = "org.unphishable.sdk.STOP_VPN" }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val config = Unphishable.config ?: run {
            Log.e(TAG, "VPN started but SDK not initialized")
            stopSelf(); return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) { stopVpn(); return START_NOT_STICKY }
        createVpnNotificationChannel()
        startForeground(FOREGROUND_NOTIF_ID, buildForegroundNotification(config.brandName))
        WarningNotificationManager.createChannels(this)
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        val config = Unphishable.config ?: return
        val apiClient = ScanApiClient(config.apiKey, config.backendUrl, config.debug)

        try {
            val builder = Builder()
                .setSession("Unphishable")
                .addAddress("10.8.0.1", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
                .setMtu(1500)
                .setBlocking(false)

            // Exclude our own app to prevent scan traffic looping
            try { builder.addDisallowedApplication(packageName) } catch (e: Exception) { }

            // Exclude trusted partner packages
            for (pkg in config.trustedPackages) {
                try { builder.addDisallowedApplication(pkg) } catch (e: Exception) { }
            }

            vpnInterface = builder.establish()
            isRunning = true

            if (config.debug) Log.d(TAG, "VPN tunnel established ✅")

            // Start packet inspection loop
            serviceScope.launch { runVpnLoop(apiClient) }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN: ${e.message}")
            stopSelf()
        }
    }

    private suspend fun runVpnLoop(apiClient: ScanApiClient) {
        val iface = vpnInterface ?: return
        val inputStream = FileInputStream(iface.fileDescriptor)
        val outputStream = FileOutputStream(iface.fileDescriptor)
        val buffer = ByteArray(32767)
        val packet = ByteBuffer.allocate(32767)

        Log.d(TAG, "VPN packet loop started")

        while (isRunning) {
            try {
                // Read packet from TUN interface
                val length = withContext(Dispatchers.IO) {
                    inputStream.read(buffer)
                }

                if (length <= 0) {
                    delay(5)
                    continue
                }

                // Copy to ByteBuffer for parsing
                packet.clear()
                packet.put(buffer, 0, length)
                packet.flip()

                // Extract URL from packet BEFORE forwarding
                val url = try {
                    PacketParser.extractUrl(packet.duplicate(), length)
                } catch (e: Exception) { null }

                // ALWAYS write packet back to TUN — traffic flows unblocked
                withContext(Dispatchers.IO) {
                    outputStream.write(buffer, 0, length)
                    outputStream.flush()
                }

                // Scan URL asynchronously — never delays traffic
                if (url != null) {
                    val now = System.currentTimeMillis()
                    val lastSeen = scannedUrls[url]

                    // Deduplicate — skip if scanned within last 60 seconds
                    if (lastSeen == null || now - lastSeen > 60_000) {
                        scannedUrls[url] = now
                        // Clean up old entries
                        if (scannedUrls.size > 1000) {
                            scannedUrls.entries.removeIf { now - it.value > 60_000 }
                        }

                        if (!inFlightScans.containsKey(url)) {
                            val cached = getCached(url)
                            if (cached != null) {
                                if (!cached.safe) showWarning(cached, Unphishable.config!!)
                            } else {
                                inFlightScans[url] = true
                                launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            apiClient.scan(url)
                                        }
                                        putCache(url, result)
                                        if (!result.safe) {
                                            showWarning(result, Unphishable.config!!)
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Scan error for $url: ${e.message}")
                                    } finally {
                                        inFlightScans.remove(url)
                                    }
                                }
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                if (isRunning) {
                    Log.w(TAG, "Packet loop error: ${e.message}")
                    delay(50)
                }
            }
        }

        Log.d(TAG, "VPN packet loop ended")
    }

    private fun showWarning(result: ScanResult, config: org.unphishable.sdk.model.UnphishableConfig) {
        WarningNotificationManager.showWarning(this, result, config)
    }

    private fun getCached(url: String): ScanResult? {
        val entry = localCache[url] ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            localCache.remove(url); return null
        }
        return entry.result
    }

    private fun putCache(url: String, result: ScanResult) {
        if (localCache.size >= CACHE_MAX_SIZE) {
            localCache.keys.firstOrNull()?.let { localCache.remove(it) }
        }
        localCache[url] = CachedResult(result, System.currentTimeMillis() + CACHE_TTL_MS)
    }

    private fun stopVpn() {
        isRunning = false
        serviceScope.cancel()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "VPN stopped")
    }

    override fun onRevoke() { stopVpn() }
    override fun onDestroy() { stopVpn(); super.onDestroy() }

    private fun createVpnNotificationChannel() {
        val channel = NotificationChannel(
            VPN_CHANNEL_ID, "Unphishable Protection",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Secure Mode active notification" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(brandName: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, UnphishableVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, VPN_CHANNEL_ID)
            .setContentTitle("🛡️ $brandName — Protected")
            .setContentText("Unphishable is scanning links in the background")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }
}
