package org.unphishable.sdk.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import org.unphishable.sdk.model.UnphishableConfig

/**
 * Unphishable SDK — Device-wide phishing protection for Android.
 *
 * Step 1 — Initialize once in Application.onCreate():
 *
 *   Unphishable.init(
 *       app         = this,
 *       apiKey      = "unph_live_xxx",
 *       brandName   = "PalmPay",
 *       trustedPackages = listOf(packageName)
 *   )
 *
 * Step 2 — Add a Secure Mode toggle in your app:
 *
 *   Unphishable.startSecureMode(activity)  // user turns ON
 *   Unphishable.stopSecureMode(context)    // user turns OFF
 *
 * That's it. The SDK handles everything else.
 */
object Unphishable {

    private const val TAG = "Unphishable"
    private const val PREFS_NAME = "unphishable_prefs"
    private const val KEY_SECURE_MODE = "secure_mode_active"

    internal var config: UnphishableConfig? = null

    /**
     * Initialize the SDK.
     * Call once in Application.onCreate() — before anything else.
     *
     * @param app             Your Application instance
     * @param apiKey          Your partner API key (unph_live_...)
     * @param brandName       Your brand name shown on warning notifications
     * @param trustedPackages List of package names whose traffic is never scanned
     *                        Always include your own app: listOf(packageName)
     * @param debug           Enable verbose logging — disable in production
     */
    @JvmStatic
    fun init(
        app: Application,
        apiKey: String,
        brandName: String,
        trustedPackages: List<String> = emptyList(),
        debug: Boolean = false
    ) {
        require(apiKey.isNotBlank()) { "Unphishable: apiKey must not be empty" }
        require(brandName.isNotBlank()) { "Unphishable: brandName must not be empty" }

        config = UnphishableConfig(
            apiKey          = apiKey,
            brandName       = brandName,
            trustedPackages = trustedPackages,
            debug           = debug
        )

        // Auto-restart Secure Mode if it was active before app restart
        if (isSecureModeActive(app)) {
            if (debug) Log.d(TAG, "Restoring Secure Mode after restart")
            startVpnService(app)
        }

        if (debug) Log.d(TAG, "Unphishable SDK initialized — brand: $brandName")
    }

    /**
     * Start Secure Mode — begins device-wide phishing protection.
     *
     * Android requires the user to approve the VPN connection on first launch.
     * This method handles that automatically via the VpnService.prepare() flow.
     *
     * Call from an Activity (needed for the VPN permission dialog).
     *
     * @param activity         The current Activity
     * @param onPermissionNeeded Called with an Intent if VPN permission dialog must be shown.
     *                           Start this intent with startActivityForResult(intent, VPN_REQUEST_CODE)
     *                           Then call startSecureMode() again in onActivityResult.
     */
    @JvmStatic
    fun startSecureMode(
        activity: Activity,
        onPermissionNeeded: (Intent) -> Unit
    ) {
        val cfg = config ?: run {
            Log.e(TAG, "Call Unphishable.init() before startSecureMode()")
            return
        }

        // Check if VPN permission is already granted
        val permIntent = VpnService.prepare(activity)
        if (permIntent != null) {
            // Permission not yet granted — show system VPN dialog
            if (cfg.debug) Log.d(TAG, "VPN permission required — showing dialog")
            onPermissionNeeded(permIntent)
            return
        }

        // Permission already granted — start immediately
        startVpnService(activity)
        saveSecureModeState(activity, true)
        if (cfg.debug) Log.d(TAG, "Secure Mode started ✅")
    }

    /**
     * Stop Secure Mode — turns off device-wide protection.
     * Safe to call even if Secure Mode is not currently active.
     */
    @JvmStatic
    fun stopSecureMode(context: Context) {
        context.startService(
            Intent(context, UnphishableVpnService::class.java).apply {
                action = UnphishableVpnService.ACTION_STOP
            }
        )
        saveSecureModeState(context, false)
        if (config?.debug == true) Log.d(TAG, "Secure Mode stopped")
    }

    /**
     * Returns true if Secure Mode is currently active.
     */
    @JvmStatic
    fun isSecureModeActive(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SECURE_MODE, false)
    }

    /**
     * Returns true if SDK has been initialized.
     */
    @JvmStatic
    fun isInitialized(): Boolean = config != null

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun startVpnService(context: Context) {
        val intent = Intent(context, UnphishableVpnService::class.java)
        context.startForegroundService(intent)
    }

    private fun saveSecureModeState(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SECURE_MODE, active)
            .apply()
    }
}
