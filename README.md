# 🛡️ Unphishable SDK — Guide d'intégration

Ce guide décrit les fichiers ajoutés et modifiés pour intégrer le SDK Unphishable dans un projet Android existant.

---

## 📋 Prérequis

| Élément | Version requise |
|---|---|
| Android Studio | Hedgehog ou supérieur |
| Android SDK Build-Tools | 36.1.0 |
| compileSdk | 35 |
| minSdk | 26 |
| Kotlin | 2.0.21 |
| AGP | 8.7.3 |

---

## 📁 Fichiers à ajouter

Ces fichiers **n'existaient pas** dans le projet original. Il faut les créer aux emplacements indiqués.

### 1. `PhishingAlertActivity.kt`
```
app/src/main/java/com/test/unphishable/PhishingAlertActivity.kt
```
> Activité plein écran affichée quand l'utilisateur tape sur une notification de phishing.
> Affiche l'URL détectée, le score de risque, les signaux détectés et deux boutons : **Retour** / **Continuer**.

---

### 2. `UnphishableAccessibilityService.kt`
```
app/src/main/java/org/unphishable/sdk/core/UnphishableAccessibilityService.kt
```
> Service d'accessibilité Android qui lit l'URL affichée dans la barre d'adresse de Chrome et autres navigateurs.
> Envoie chaque URL détectée au `UnphishableVpnService` pour analyse sans bloquer internet.

---

### 3. `accessibility_service_config.xml`
```
app/src/main/res/xml/accessibility_service_config.xml
```
> Fichier de configuration XML requis par Android pour déclarer le service d'accessibilité.
> Le dossier `res/xml/` doit être créé s'il n'existe pas.

---

## ✏️ Fichiers à modifier

Ces fichiers existaient déjà dans le projet original et doivent être **remplacés** par les nouvelles versions.

### 1. `MainActivity.kt`
```
app/src/main/java/com/test/unphishable/MainActivity.kt
```
> ⚠️ Remplacer la clé API et le brandName par les vôtres :
> ```kotlin
> Unphishable.init(
>     app       = application,
>     apiKey    = "VOTRE_CLE_API",
>     brandName = "VOTRE_MARQUE",
>     ...
> )
> ```

---

### 2. `Unphishable.kt`
```
app/src/main/java/org/unphishable/sdk/core/Unphishable.kt
```
> Ajout des méthodes `clearHistory()` et `recordScan()` pour gérer l'historique des scans.

---

### 3. `UnphishableVpnService.kt`
```
app/src/main/java/org/unphishable/sdk/core/UnphishableVpnService.kt
```
> Corrections majeures :
> - `.setBlocking(false)` — internet ne se coupe plus
> - Navigateurs exclus du tunnel VPN (détectés via AccessibilityService)
> - Ajout de `ACTION_SCAN_URL` pour communication avec l'AccessibilityService
> - Réémission immédiate des paquets avant analyse

---

### 4. `PacketParser.kt`
```
app/src/main/java/org/unphishable/sdk/utils/PacketParser.kt
```
> Ajout d'une surcharge `extractUrl(ByteBuffer, Int)` pour compatibilité étendue.

---

### 5. `AndroidManifest.xml`
```
app/src/main/AndroidManifest.xml
```
> Ajouts nécessaires :
> - Permission `FOREGROUND_SERVICE_DATA_SYNC` (Android 14+)
> - Permission `BIND_ACCESSIBILITY_SERVICE`
> - Déclaration de `PhishingAlertActivity`
> - Déclaration de `UnphishableAccessibilityService` avec sa config XML

---

### 6. `strings.xml`
```
app/src/main/res/values/strings.xml
```
> Ajout de la description du service d'accessibilité :
> ```xml
> <string name="accessibility_service_description">
>     Unphishable surveille les URLs dans votre navigateur pour vous protéger du phishing.
> </string>
> ```

---

### 7. `build.gradle.kts`
```
app/build.gradle.kts
```
> Ajouts dans le bloc `android` :
> ```kotlin
> compileSdk = 35
> buildToolsVersion = "36.1.0"
> ```
> Nouvelles dépendances :
> ```kotlin
> implementation("com.squareup.okhttp3:okhttp:4.12.0")
> implementation("com.google.code.gson:gson:2.10.1")
> implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
> implementation(libs.androidx.appcompat)
> ```

---

### 8. `libs.versions.toml`
```
gradle/libs.versions.toml
```
> Mise à jour des versions AGP et ajout de la dépendance `appcompat`.

---

## ⚙️ Activation après installation

Après compilation et installation sur le téléphone, deux étapes sont nécessaires :

**Étape 1 — Activer le Mode Sécurisé**
Lancer l'application et accepter la permission VPN qui s'affiche.

**Étape 2 — Activer le Service d'Accessibilité**
```
Paramètres Android
  → Accessibilité
  → Services installés
  → Unphishable Protection
  → Activer
```
> ⚠️ Sans cette étape, les URLs visitées dans Chrome ne seront pas analysées.

---

## 🔔 Permissions requises

| Permission | Utilisation |
|---|---|
| `INTERNET` | Envoi des URLs à l'API Unphishable |
| `FOREGROUND_SERVICE` | Maintien du service VPN actif |
| `FOREGROUND_SERVICE_DATA_SYNC` | Requis Android 14+ pour le VPN |
| `POST_NOTIFICATIONS` | Affichage des alertes phishing |
| `BIND_VPN_SERVICE` | Création du tunnel VPN local |
| `BIND_ACCESSIBILITY_SERVICE` | Lecture des URLs dans les navigateurs |

---

## 🏗️ Architecture

```
Navigateur Chrome (URL tapée)
        ↓
UnphishableAccessibilityService
(lit la barre d'adresse)
        ↓
UnphishableVpnService.scanUrl()
        ↓
ScanApiClient → api.unphishable.org
        ↓
Résultat : SAFE / MEDIUM / HIGH
        ↓
SAFE   → rien
MEDIUM → notification ⚠️
HIGH   → notification 🚨 + PhishingAlertActivity
```

---

## 📞 Support

Pour toute question sur la clé API ou les URLs de test, contactez l'équipe Unphishable.
