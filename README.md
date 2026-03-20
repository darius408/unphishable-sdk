# 🛡️ Unphishable Android SDK

Phishing detection for African fintech apps. Warns users before they lose money.

---

## How it works

1. Partner registers at [partners.unphishable.org](https://partners.unphishable.org)
2. Chooses user slots → pays 20% upfront → receives API key
3. Adds SDK to their Android app — 3 lines of code
4. Users are protected automatically — no action needed from them

---

## Integration

```gradle
// build.gradle
repositories { maven { url 'https://jitpack.io' } }
implementation 'com.github.NewtonAchonduh:unphishable-android-sdk:1.0.0'
```

```kotlin
// Application.onCreate()
Unphishable.init(app = this, apiKey = "YOUR_KEY", brandName = "YourApp")

// MainActivity
Unphishable.startSecureMode(this)
```

That's it. Your users are protected.

---

## Monitor

Login at [partners.unphishable.org](https://partners.unphishable.org) to see scans, threats and slot usage in real time.

---

## Pricing

$10,000 / 100,000 users / year · Minimum 1,000 slots ($100/year)

---

## Get started

📧 founderunphishable@gmail.com
📱 +237 672739883
🌐 unphishable.org

Built in Douala, Cameroon 🇨🇲
