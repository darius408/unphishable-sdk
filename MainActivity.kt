package com.test.unphishable

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import org.unphishable.sdk.core.Unphishable

class MainActivity : ComponentActivity() {

    companion object {
        private const val VPN_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Unphishable.init(
            app             = application,
            apiKey          = "unph_live_77c2fdc9c634285db82752f7fb2a0512",
            brandName       = "Nz-enterprise",
            trustedPackages = listOf(packageName),
            debug           = true
        )

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 80, 60, 0)
        }

        layout.addView(TextView(this).apply {
            text     = "🛡️ Unphishable SDK Test\n\nVPN démarré...\nOuvre une URL suspecte pour tester."
            textSize = 18f
            setPadding(0, 0, 0, 60)
        })

        setContentView(layout)

        requestVpnPermission()
    }

    private fun requestVpnPermission() {
        Unphishable.startSecureMode(this) { permIntent ->
            @Suppress("DEPRECATION")
            startActivityForResult(permIntent, VPN_REQUEST_CODE)
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            requestVpnPermission()
        }
    }
}