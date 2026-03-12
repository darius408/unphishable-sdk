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
    const val CHANNEL_HIGH = "unphishable_high"
    const val CHANNEL_MEDIUM = "unphishable_medium"
    const val ACTION_PROCEED = "org.unphishable.sdk.ACTION_PROCEED"
    const val ACTION_GO_BACK = "org.unphishable.sdk.ACTION_GO_BACK"
    const val EXTRA_URL = "unphishable_url"
    const val EXTRA_NOTIF_ID = "unphishable_notif_id"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_HIGH, "High Risk Warnings",
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Phishing link alerts"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 100, 300)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MEDIUM, "Suspicious Link Warnings",
                NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Suspicious link alerts"
                enableLights(true)
                lightColor = Color.YELLOW
            }
        )
    }

    fun showWarning(context: Context, result: ScanResult, config: UnphishableConfig) {
        val notifId = result.url.hashCode()
        val channelId = if (result.isHigh) CHANNEL_HIGH else CHANNEL_MEDIUM

        val proceedIntent = PendingIntent.getBroadcast(
            context, notifId + 1,
            Intent(ACTION_PROCEED).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_URL, result.url)
                putExtra(EXTRA_NOTIF_ID, notifId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val goBackIntent = PendingIntent.getBroadcast(
            context, notifId + 2,
            Intent(ACTION_GO_BACK).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_URL, result.url)
                putExtra(EXTRA_NOTIF_ID, notifId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val emoji = if (result.isHigh) "🚨" else "⚠️"
        val riskLabel = if (result.isHigh) "HIGH RISK" else "MEDIUM RISK"
        val title = "$emoji $riskLabel — ${result.domain.ifBlank { result.url }}"

        val ssl = when (result.sslStatus.lowercase()) {
            "valid"           -> "✅ SSL Valid"
            "invalid", "error"-> "❌ SSL Invalid"
            "none", "no ssl"  -> "❌ No SSL"
            else              -> "⚠️ SSL Unknown"
        }

        val age = if (result.ageMonths != null) {
            when {
                result.ageMonths < 1 -> "⚠️ Domain age: < 1 month"
                result.ageMonths < 6 -> "⚠️ Domain age: ${result.ageMonths} months"
                else                 -> "✅ Domain age: ${result.ageMonths} months"
            }
        } else "Domain age: Unknown"

        val http = if (result.httpCode != null) "HTTP: ${result.httpCode}" else ""

        val patterns = if (result.patternsTriggered.isNotEmpty())
            "Patterns: ${result.patternsTriggered.take(3).joinToString(", ")}"
        else ""

        val warning = result.warningMessage.ifBlank {
            if (result.isHigh)
                "Do not enter personal or financial information on this site."
            else
                "This link has suspicious patterns. Proceed with caution."
        }

        val bigText = buildString {
            appendLine("Score: ${result.score}/100")
            appendLine(ssl)
            appendLine(age)
            if (http.isNotBlank()) appendLine(http)
            if (patterns.isNotBlank()) appendLine(patterns)
            appendLine()
            appendLine(warning)
            append("Protected by ${config.brandName}")
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText("Score: ${result.score}/100  •  $ssl")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(
                if (result.isHigh) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setColor(if (result.isHigh) Color.RED else Color.parseColor("#FF8F00"))
            .setAutoCancel(false)
            .setOngoing(false)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Go Back", goBackIntent
            )
            .addAction(
                android.R.drawable.ic_menu_send,
                "Proceed Anyway", proceedIntent
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
            if (config.debug) Log.d(TAG, "Warning shown — ${result.riskLevel} — ${result.url}")
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission not granted: ${e.message}")
        }
    }

    fun dismiss(context: Context, notifId: Int) {
        NotificationManagerCompat.from(context).cancel(notifId)
    }
}

class UnphishableActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra(WarningNotificationManager.EXTRA_URL) ?: return
        val notifId = intent.getIntExtra(WarningNotificationManager.EXTRA_NOTIF_ID, 0)

        WarningNotificationManager.dismiss(context, notifId)

        when (intent.action) {
            WarningNotificationManager.ACTION_PROCEED -> {
                val browserIntent = Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(browserIntent)
                } catch (e: Exception) {
                    Log.e("Unphishable", "Could not open URL: ${e.message}")
                }
            }
            WarningNotificationManager.ACTION_GO_BACK -> {
                // User chose safety — URL is not opened
            }
        }
    }
}
