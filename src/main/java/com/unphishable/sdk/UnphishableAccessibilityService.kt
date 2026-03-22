package org.unphishable.sdk.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * UnphishableAccessibilityService
 *
 * Lit l'URL affichée dans la barre d'adresse de Chrome (et autres navigateurs)
 * sans intercepter le réseau — internet n'est donc jamais bloqué.
 *
 * Quand l'URL change, elle est envoyée au VPN service pour analyse.
 */
class UnphishableAccessibilityService : AccessibilityService() {

    private val TAG = "Unphishable:A11y"
    private var lastScannedUrl = ""

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                         AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 500
            // Surveiller tous les navigateurs connus
            packageNames = UnphishableVpnService.BROWSER_PACKAGES.toTypedArray()
        }
        Log.d(TAG, "Service Accessibilité connecté ✅")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        try {
            val url = extractUrlFromEvent(event) ?: return

            // Ne pas rescanner la même URL
            if (url == lastScannedUrl) return
            lastScannedUrl = url

            Log.d(TAG, "URL navigateur détectée : $url")

            // Envoyer l'URL au VPN service pour analyse
            val vpnServiceIntent = Intent(this, UnphishableVpnService::class.java).apply {
                action = UnphishableVpnService.ACTION_SCAN_URL
                putExtra(UnphishableVpnService.EXTRA_URL_TO_SCAN, url)
            }
            startService(vpnServiceIntent)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur accessibility event : ${e.message}")
        }
    }

    private fun extractUrlFromEvent(event: AccessibilityEvent): String? {
        val rootNode = rootInActiveWindow ?: return null
        return try {
            findUrlInNode(rootNode)
        } finally {
            rootNode.recycle()
        }
    }

    private fun findUrlInNode(node: AccessibilityNodeInfo): String? {
        // Chercher la barre d'adresse de Chrome par son ID de ressource
        val urlBarIds = listOf(
            "com.android.chrome:id/url_bar",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.microsoft.emmx:id/url_bar",
            "com.brave.browser:id/url_bar",
            "com.opera.browser:id/url_field",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text"
        )

        for (id in urlBarIds) {
            val nodes = node.findAccessibilityNodeInfosByViewId(id)
            if (nodes != null && nodes.isNotEmpty()) {
                val text = nodes[0].text?.toString()
                nodes.forEach { it.recycle() }
                if (!text.isNullOrBlank() && (text.startsWith("http") || text.contains("."))) {
                    val url = if (text.startsWith("http")) text else "https://$text"
                    return url
                }
            }
        }
        return null
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Accessibilité interrompu")
    }
}
