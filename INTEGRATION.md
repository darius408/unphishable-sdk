# Unphishable Android SDK — Integration Guide

Version 1.0.0 | unphishable.org

---

## What this SDK does

Once integrated, your app becomes a device-wide phishing protection tool.
When the user turns on Secure Mode inside your app, every link they click
anywhere on their phone — WhatsApp, SMS, browser, any app — is scanned
in real time against the Unphishable backend.

- **SAFE** → completely silent, link opens normally
- **MEDIUM** → yellow warning notification with Proceed / Go Back
- **HIGH** → red warning notification with full threat breakdown and Proceed / Go Back

Your users are always in control. We warn, we never block.

---

## Requirements

- Android API 26 (Android 8.0) or higher
- Kotlin or Java project
- API key from Unphishable partner dashboard

---

## Step 1 — Add dependencies to your app's build.gradle

```gradle
dependencies {
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.google.code.gson:gson:2.10.1'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
}
Step 2 — Add to your AndroidManifest.xml
Inside <manifest>:
<uses-permission android:name="android.permission.BIND_VPN_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
Inside <application>:
<service
    android:name="org.unphishable.sdk.core.UnphishableVpnService"
    android:permission="android.permission.BIND_VPN_SERVICE"
    android:foregroundServiceType="specialUse"
    android:exported="false">
    <intent-filter>
        <action android:name="android.net.VpnService" />
    </intent-filter>
</service>

<receiver
    android:name="org.unphishable.sdk.ui.UnphishableActionReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="org.unphishable.sdk.ACTION_PROCEED" />
        <action android:name="org.unphishable.sdk.ACTION_GO_BACK" />
    </intent-filter>
</receiver>
Step 3 — Initialize in your Application class
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Unphishable.init(
            app             = this,
            apiKey          = "unph_live_your_key_here",
            brandName       = "YourAppName",
            trustedPackages = listOf(packageName),
            debug           = BuildConfig.DEBUG
        )
    }
}
Step 4 — Add Secure Mode toggle in your UI
class SettingsActivity : AppCompatActivity() {

    private val VPN_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        secureModeSwitch.isChecked = Unphishable.isSecureModeActive(this)

        secureModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Unphishable.startSecureMode(this) { permIntent ->
                    startActivityForResult(permIntent, VPN_REQUEST_CODE)
                }
            } else {
                Unphishable.stopSecureMode(this)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            Unphishable.startSecureMode(this) {}
        } else {
            secureModeSwitch.isChecked = false
        }
    }
}
Step 5 — Build your Security Dashboard screen
Build a dedicated screen inside your app where users can monitor their
protection. All data comes from the Unphishable API using your API key.
Base URL
https://unphishable-backend-production-8cf8.up.railway.app
Authentication
All SDK endpoints require your API key in the request header:
X-API-Key: unph_live_your_key_here
Endpoint 1 — Partner Usage & Stats
GET /sdk/usage
Returns your overall partner stats.
Response:
{
  "partner_id": "abc123",
  "brand_name": "PalmPay",
  "account_status": "active",
  "scan_count": 14823,
  "slots_used": 3200,
  "user_slots": 100000,
  "slots_remaining": 96800,
  "trial_ends_at": "2026-04-11T00:00:00Z"
}
Use this to show:
Total scans performed
Users protected (slots_used)
Subscription status
Endpoint 2 — Full Partner Dashboard
GET /sdk/dashboard
Returns detailed partner view including threats and activity.
Response:
{
  "partner_id": "abc123",
  "brand_name": "PalmPay",
  "account_status": "active",
  "total_scans": 14823,
  "threats_detected": 342,
  "slots_used": 3200,
  "user_slots": 100000,
  "recent_threats": [
    {
      "url": "https://fake-palmpay.netlify.app",
      "domain": "fake-palmpay.netlify.app",
      "risk_level": "HIGH",
      "score": 91,
      "patterns_triggered": ["TLD Swap", "Brand Injection"],
      "last_seen": "2026-03-13T10:22:00Z",
      "hit_count": 7
    }
  ]
}
Use this to show:
Threats detected this month
Most targeted domains
Hit count — how many users clicked the same phishing link
Endpoint 3 — Today's Threats
GET /threats/today
Headers: X-API-Key: unph_live_your_key_here
Returns all HIGH and MEDIUM threats detected in the last 24 hours.
Response:
{
  "threats": [
    {
      "url": "https://fake-palmpay.netlify.app",
      "domain": "fake-palmpay.netlify.app",
      "risk_level": "HIGH",
      "score": 91,
      "patterns_triggered": ["TLD Swap", "Brand Injection", "Redirect Chain"],
      "hit_count": 7,
      "first_seen": "2026-03-13T08:00:00Z",
      "last_seen": "2026-03-13T10:22:00Z"
    }
  ],
  "total": 1
}
Use this to show:
Live threat feed
Which phishing sites are targeting your users right now
How many users hit each threat (hit_count)
Endpoint 4 — Full Threat History
GET /threats/all
Headers: X-API-Key: unph_live_your_key_here
Returns complete threat history paginated.
Query params:
page — page number (default: 1)
limit — results per page (default: 50, max: 100)
Use this to show:
Full scan history
Threat trends over time
Most targeted domains all time
What your Security Dashboard should show
We recommend building your security screen with these sections:
Header
Secure Mode toggle (ON/OFF)
Users protected today
Threats blocked today
My Scan History
Every URL the current user scanned
Verdict badge (SAFE / MEDIUM / HIGH)
Score, SSL status, domain age
Patterns detected
Timestamp
Top Threats This Week
Domain name
Risk level
How many users were targeted (hit_count)
First seen / last seen
Protection Stats
Total scans all time
Total threats caught
Slots used vs total slots
What users see — warning notifications
Secure Mode active (persistent):
🛡️ YourAppName Secure Mode
Protecting you from phishing links
[Turn Off]
MEDIUM risk detected:
⚠️ MEDIUM RISK — suspicious-site.com
Score: 45/100
✅ SSL Valid • ⚠️ Domain age: 2 months
Patterns: Subdomain Flood, Content Brand Hijack
Proceed with caution.
Protected by YourAppName
[Go Back] [Proceed Anyway]
HIGH risk detected:
🚨 HIGH RISK — fake-palmpay.netlify.app
Score: 91/100
❌ SSL Invalid • ⚠️ Domain age: < 1 month
Patterns: TLD Swap, Brand Injection, Redirect Chain
Do not enter personal or financial information.
Protected by YourAppName
[Go Back] [Proceed Anyway]
API key & Support
Get your API key: unphishable.org
Support: newton@unphishable.org
