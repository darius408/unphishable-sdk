package org.unphishable.sdk.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import org.unphishable.sdk.model.ScanHistoryEntry
import org.unphishable.sdk.model.UnphishableConfig
import java.util.Collections

object Unphishable {

    private const val TAG = "Unphishable"
    private const val PREFS_NAME = "unphishable_prefs"
    private const val KEY_SECURE_MODE = "secure_mode_active"
    private const val KEY_TOTAL_SCANS = "total_scans"
    private const val KEY_THREATS_BLOCKED = "threats_blocked"

    internal var config: UnphishableConfig? = null

    // Historique en mémoire (max 100 entrées)
    private val _scanHistory = Collections.synchronizedList(mutableListOf<ScanHistoryEntry>())
    val scanHistory: List<ScanHistoryEntry> get() = _scanHistory.toList()

    /**
     * Initialise le SDK. À appeler dans Application.onCreate() ou MainActivity.onCreate().
     */
    @JvmStatic
    fun init(
        app: Application,
        apiKey: String,
        brandName: String,
        trustedPackages: List<String> = emptyList(),
        debug: Boolean = true
    ) {
        require(apiKey.isNotBlank()) { "Unphishable: apiKey ne doit pas être vide" }
        require(brandName.isNotBlank()) { "Unphishable: brandName ne doit pas être vide" }

        config = UnphishableConfig(
            apiKey = apiKey,
            brandName = brandName,
            trustedPackages = trustedPackages,
            debug = debug
        )

        // Redémarrage automatique si le mode sécurisé était actif
        if (isSecureModeActive(app)) {
            if (debug) Log.d(TAG, "Restauration du Mode Sécurisé après redémarrage")
            startVpnService(app)
        }

        if (debug) Log.d(TAG, "SDK Unphishable initialisé — brand: $brandName, debug: $debug")
    }

    /**
     * Démarre le Mode Sécurisé (protection anti-phishing).
     * Android demande la permission VPN au premier lancement.
     *
     * @param activity L'activité courante
     * @param onPermissionNeeded Appelé avec l'Intent si le dialogue VPN doit être affiché
     */
    @JvmStatic
    fun startSecureMode(
        activity: Activity,
        onPermissionNeeded: (Intent) -> Unit
    ) {
        val cfg = config ?: run {
            Log.e(TAG, "Appelez Unphishable.init() avant startSecureMode()")
            return
        }

        val permIntent = VpnService.prepare(activity)
        if (permIntent != null) {
            if (cfg.debug) Log.d(TAG, "Permission VPN requise — affichage dialogue")
            onPermissionNeeded(permIntent)
            return
        }

        startVpnService(activity)
        saveSecureModeState(activity, true)
        if (cfg.debug) Log.d(TAG, "Mode Sécurisé démarré ✅")
    }

    /**
     * Arrête le Mode Sécurisé.
     */
    @JvmStatic
    fun stopSecureMode(context: Context) {
        context.startService(
            Intent(context, UnphishableVpnService::class.java).apply {
                action = UnphishableVpnService.ACTION_STOP
            }
        )
        saveSecureModeState(context, false)
        if (config?.debug == true) Log.d(TAG, "Mode Sécurisé arrêté")
    }

    @JvmStatic
    fun isSecureModeActive(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SECURE_MODE, false)
    }

    @JvmStatic
    fun isInitialized(): Boolean = config != null

    fun getTotalScans(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_TOTAL_SCANS, 0)
    }

    fun getThreatsBlocked(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_THREATS_BLOCKED, 0)
    }

    // Vide l'historique en mémoire (appelé au démarrage du service VPN)
    internal fun clearHistory() {
        _scanHistory.clear()
    }

    internal fun recordScan(context: Context, entry: ScanHistoryEntry) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_TOTAL_SCANS, prefs.getInt(KEY_TOTAL_SCANS, 0) + 1)
            .apply()

        if (entry.riskLevel != "SAFE") {
            prefs.edit()
                .putInt(KEY_THREATS_BLOCKED, prefs.getInt(KEY_THREATS_BLOCKED, 0) + 1)
                .apply()
        }

        _scanHistory.add(0, entry)
        if (_scanHistory.size > 100) _scanHistory.removeAt(_scanHistory.size - 1)
    }

    internal fun startVpnService(context: Context) {
        context.startForegroundService(
            Intent(context, UnphishableVpnService::class.java)
        )
    }

    private fun saveSecureModeState(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SECURE_MODE, active).apply()
    }
}
