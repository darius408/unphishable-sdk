package org.unphishable.sdk.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.unphishable.sdk.model.ScanResult
import org.unphishable.sdk.model.UnphishableConfig

internal object WarningNotificationManager {

    private const val TAG = "Unphishable:Notify"
    private const val CHANNEL_HIGH   = "unphishable_high_risk"
    private const val CHANNEL_MEDIUM = "unphishable_medium_risk"

    const val ACTION_PROCEED  = "org.unphishable.sdk.ACTION_PROCEED"
    const val ACTION_GO_BACK  = "org.unphishable.sdk.ACTION_GO_BACK"
    const val EXTRA_URL       = "unphishable_url"
    const val EXTRA_NOTIF_ID  = "unphishable_notif_id"
    const val EXTRA_RISK      = "unphishable_risk"
    const val EXTRA_SCORE     = "unphishable_score"
    const val EXTRA_PATTERNS  = "unphishable_patterns"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_HIGH, "Alertes Phishing Élevé",
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alertes pour liens de phishing à haut risque"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 100, 400)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MEDIUM, "Liens Suspects",
                NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Alertes pour liens suspects"
                enableLights(true)
                lightColor = Color.YELLOW
            }
        )
    }

    fun showWarning(context: Context, result: ScanResult, config: UnphishableConfig) {
        val notifId   = result.url.hashCode()
        val channelId = if (result.isHigh) CHANNEL_HIGH else CHANNEL_MEDIUM
        val isHigh    = result.isHigh


        val goBackIntent = PendingIntent.getBroadcast(
            context, notifId + 1,
            Intent(ACTION_GO_BACK).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_URL, result.url)
                putExtra(EXTRA_NOTIF_ID, notifId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Bouton "Continuer quand même"
        val proceedIntent = PendingIntent.getBroadcast(
            context, notifId + 2,
            Intent(ACTION_PROCEED).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_URL, result.url)
                putExtra(EXTRA_NOTIF_ID, notifId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val emoji = if (isHigh) "🚨" else "⚠️"
        val title = if (isHigh) "$emoji Lien de Phishing Détecté !"
                    else        "$emoji Lien Suspect Détecté"

        val bodyText = when {
            result.warningMessage.isNotBlank() -> result.warningMessage
            isHigh -> "Ce lien est dangereux. N'entrez pas vos informations personnelles ou bancaires."
            else   -> "Ce lien présente des caractéristiques suspectes. Soyez prudent."
        }

        val patternText = if (result.patternsTriggered.isNotEmpty())
            "\n\nSignaux détectés : ${result.patternsTriggered.take(3).joinToString(", ")}"
        else ""

        val scoreText = "\nScore de risque : ${result.score}/100"

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(bodyText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$bodyText$scoreText$patternText\n\nProtégé par ${config.brandName}")
            )
            .setPriority(if (isHigh) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setColor(if (isHigh) Color.RED else Color.parseColor("#FF8F00"))
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "🔙 Revenir", goBackIntent)
            .addAction(android.R.drawable.ic_menu_send, "⚠️ Continuer", proceedIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
            if (config.debug) Log.d(TAG, "Alerte affichée : ${result.url} — ${result.riskLevel} (${result.score})")
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission notification refusée : ${e.message}")
        }
    }

    fun dismiss(context: Context, notifId: Int) {
        NotificationManagerCompat.from(context).cancel(notifId)
    }
}

class UnphishableActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val url     = intent.getStringExtra(WarningNotificationManager.EXTRA_URL) ?: return
        val notifId = intent.getIntExtra(WarningNotificationManager.EXTRA_NOTIF_ID, 0)
        WarningNotificationManager.dismiss(context, notifId)

        when (intent.action) {
            WarningNotificationManager.ACTION_PROCEED -> {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                } catch (e: Exception) {
                    Log.e("Unphishable", "Impossible d'ouvrir l'URL : ${e.message}")
                }
            }
            WarningNotificationManager.ACTION_GO_BACK -> {
                Log.d("Unphishable", "Utilisateur a refusé : $url")
            }
        }
    }
}
