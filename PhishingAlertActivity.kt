package com.test.unphishable

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.test.unphishable.ui.theme.TestAppTheme
import org.unphishable.sdk.ui.WarningNotificationManager

class PhishingAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url      = intent.getStringExtra(WarningNotificationManager.EXTRA_URL) ?: ""
        val risk     = intent.getStringExtra(WarningNotificationManager.EXTRA_RISK) ?: "HIGH"
        val score    = intent.getIntExtra(WarningNotificationManager.EXTRA_SCORE, 0)
        val patterns = intent.getStringArrayExtra(WarningNotificationManager.EXTRA_PATTERNS)
            ?.toList() ?: emptyList()
        val notifId  = intent.getIntExtra(WarningNotificationManager.EXTRA_NOTIF_ID, 0)

        setContent {
            TestAppTheme {
                PhishingAlertScreen(
                    url = url,
                    riskLevel = risk,
                    score = score,
                    patterns = patterns,
                    onGoBack = {
                        WarningNotificationManager.dismiss(this, notifId)
                        finish()
                    },
                    onProceed = {
                        WarningNotificationManager.dismiss(this, notifId)
                        try {
                            startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (e: Exception) { /* ignore */ }
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun PhishingAlertScreen(
    url: String,
    riskLevel: String,
    score: Int,
    patterns: List<String>,
    onGoBack: () -> Unit,
    onProceed: () -> Unit
) {
    val isHigh = riskLevel == "HIGH"
    val bgColor = if (isHigh) Color(0xFFB71C1C) else Color(0xFFE65100)
    val emoji   = if (isHigh) "🚨" else "⚠️"
    val title   = if (isHigh) "Danger — Phishing Détecté !" else "Lien Suspect"
    val message = if (isHigh)
        "Ce lien est très probablement un site de phishing. Il pourrait voler vos mots de passe, données bancaires ou informations personnelles."
    else
        "Ce lien présente plusieurs caractéristiques suspectes. Procédez avec extrême prudence."

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(emoji, fontSize = 72.sp)

            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                textAlign = TextAlign.Center
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.15f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "URL détectée :",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        url,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                message,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            // Score de risque
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.15f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Score de risque", color = Color.White, fontSize = 14.sp)
                    Text(
                        "$score / 100",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            // Signaux détectés
            if (patterns.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.15f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Signaux détectés :",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        patterns.take(4).forEach { pattern ->
                            Text(
                                "• $pattern",
                                color = Color.White,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Boutons
            Button(
                onClick = onGoBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = bgColor
                )
            ) {
                Text("🔙 Retourner en sécurité", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            TextButton(
                onClick = onProceed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Ignorer l'avertissement et continuer",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
        }
    }
}
