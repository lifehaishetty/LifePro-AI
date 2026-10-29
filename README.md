# LifePro-AI 👁️👵🛡️
> **A Production-Quality Android Emergency Assistant & Guardian Safety Platform designed for visually impaired individuals, elderly citizens, and people in acute distress.**

---

## 💡 Mission & Core Problem

Millions of visually impaired individuals and elderly citizens living independently face severe safety and accessibility challenges every day:
* **Complex Smartphone UIs**: Dense menus, tiny text, and nested settings are impossible to navigate during panic or for someone with low vision.
* **Sudden Emergencies**: Falls, strokes, disorientation, or acute medical distress require immediate response without needing to unlock a phone or look at a screen.
* **Caregiver Peace of Mind**: Family members and guardians struggle to support vulnerable loved ones 24/7 without invasive surveillance or compromising their dignity.

**LifePro-AI** turns any Android phone into an always-vigilant, voice-first safety companion that pairs seamlessly with authorized caregivers.

---

## 🎨 Design System: Black, Violet, and Red

Designed with a bespoke **Dark Luxury & High-Contrast Safety** aesthetic:
* **Deep Space Black (`#0A0A0E`)**: Maximizes OLED contrast and battery life while minimizing eye fatigue.
* **Royal Violet (`#7C3AED`)**: The signature intelligent voice orb and primary interactive accent.
* **Emergency Red (`#DC2626`)**: Unambiguous, high-visibility hazard color for immediate SOS controls and critical warnings.
* **Neutral Slate (`#F8FAFC` / `#94A3B8`)**: High legibility typography adhering to WCAG 2.1 AAA touch target and contrast guidelines.

---

## 🚀 Key Features

### 1. 🎙️ Conversational Voice-First Assistant
* **Wake Word & Listening Orb**: Say *"Hello Life"* or tap the violet orb to start listening.
* **Clarifying Prompts**: The assistant asks intelligent clarifying questions before triggering drastic actions (e.g., *"I detected an impact. Did you fall? Are you injured?"*).
* **Graceful Offline Engine**: Automatically parses critical emergency keywords (e.g., *"Help"*, *"Ambulance"*, *"Call daughter"*, *"Cancel"*) locally if cloud connectivity drops.
* **Bilingual / Spoken Audio Feedback**: Clear, compassionate TTS responses so users never need to read a screen.

### 2. 🚨 10-Second Emergency SOS (Indian National 112 & Sequential Fallback)
* Spoken 10-second countdown with audible second-by-second warnings.
* **Dual Cancellation**: Abort immediately via the prominent touch button OR simply by saying *"Cancel"*.
* If uncancelled (e.g., user is unconscious), automatically dials **112 (Indian National Emergency Helpline)**.
* Initiates sequential fallback dispatch to registered emergency contacts in priority order.

### 3. 👥 Real User–Guardian Account Separation & Secure Pairing
* **Separate Roles**:
  * **User Mode**: Voice assistant, emergency countdown, privacy toggles, and guardian permission controls.
  * **Guardian Mode**: Remote live location monitoring, alert history, and authorized video check-in.
* **Expiring Pairing Codes (`LP-XXXXXX`)**: Real time-limited code generation, pending approval workflows, and immediate revocation at any time.

### 4. 📞 Natural Language Contact Resolver
* Resolves relationships intuitively:
  * *"Call my daughter"* → maps to daughter's phone number.
  * *"Call my doctor"* → maps to physician.
  * *"Call my favorite"* → routes to Priority 1 contact.
  * Ambiguous requests prompt for spoken clarification rather than making erroneous calls.

### 5. 📍 Live Location Tracking & WhatsApp Sharing
* Real-time GPS location telemetry formatted with Google Maps links (`https://maps.google.com/?q=...`).
* 1-click WhatsApp emergency broadcast with formatted coordinates and status indicators (`LIVE_GPS`, `STALE_CACHED`, `NO_GPS`).

### 6. 📹 Authorized Remote Camera Check-In
* State-machine controlled video check-in for caregivers.
* Strict user privacy: Displays an un-dismissible high-visibility banner and haptic chime on the user's device whenever a remote session is active.

### 7. 🛡️ Foreground Service & Background Operation
* `SafetyForegroundService` ensures the assistant remains vigilant in the background without getting killed by Android battery optimizations.
* Full Android 13/14 runtime permission orchestration (`RECORD_AUDIO`, `CALL_PHONE`, `ACCESS_FINE_LOCATION`, `CAMERA`, `POST_NOTIFICATIONS`).

### 8. 📋 Searchable Activity & Alert History
* Fully audit-logged timeline recording all emergency triggers, cancellations, guardian connections, and battery events.
* Instant keyword search and event type filtering.

---

## 🏗️ Technical Architecture

```
+-----------------------------------------------------------+
|              Android Client (Kotlin / Material 3)         |
|  - MainActivity (Multi-tab Coordinator)                   |
|  - AuthActivity (Google Sign-In, Phone OTP, Roles)        |
|  - AssistantEngine (STT, TTS, Offline Fallback)           |
|  - SosController (10s Countdown, 112, Cancel, Fallback)   |
|  - ContactManager (Relationship NLP, Priority Queue)      |
|  - PairingManager (Expiring Codes, Revocation)            |
|  - LocationService (GPS Telemetry, Maps URL, WhatsApp)    |
|  - SafetyForegroundService (Background Guardian)          |
+-----------------------------+-----------------------------+
                              |
                     HTTPS JSON POST /api/chat
                              v
+-----------------------------+-----------------------------+
|              Node.js / Vercel Serverless Backend          |
|  - server/server.js & api/chat.js                         |
|  - REST Pairing & Location Relay Endpoints                |
|  - NVIDIA Nemotron 70B Intent Reasoner                    |
+-----------------------------------------------------------+
```

---

## 📂 Project Structure

```
lifepro-ai/
├── README.md                           # Documentation & architecture
├── LifePro-AI.apk                      # Ready-to-install production APK
├── public/
│   ├── index.html                      # Luxury Black-Violet-Red web download portal
│   └── LifePro-AI.apk                  # Direct download mirror
├── api/
│   └── chat.js                         # Vercel serverless function (AI & pairing routes)
├── server/
│   ├── server.js                       # Express / Node backend with pairing & location APIs
│   └── package.json                    # Backend dependencies
└── android/
    ├── gradlew & gradlew.bat           # Gradle build tool
    └── app/
        ├── build.gradle                # Dependencies (Material 3, Kotlin, Coroutines, JUnit)
        ├── src/main/
        │   ├── AndroidManifest.xml     # Permissions & foreground services
        │   ├── java/com/lifepro/ai/
        │   │   ├── MainActivity.kt     # Main tabbed dashboard & voice orb
        │   │   ├── SafetyForegroundService.kt # Background persistent guardian
        │   │   ├── activity/           # Event model & ActivityRepository
        │   │   ├── assistant/          # AssistantEngine (STT, TTS, intent parsing)
        │   │   ├── auth/               # AuthManager, UserProfile, UserRole
        │   │   ├── camera/             # CameraSessionManager & privacy states
        │   │   ├── contacts/           # ContactManager & SafetyContact
        │   │   ├── emergency/          # SosController (10s countdown, 112 auto-dial)
        │   │   ├── location/           # LocationService & Maps formatter
        │   │   ├── pairing/            # PairingManager & PairingState
        │   │   └── ui/                 # AuthActivity (Login & Role Selection)
        │   └── res/
        │       ├── drawable/           # Vector icons & violet glowing orb drawables
        │       ├── layout/             # activity_main.xml, activity_auth.xml, item_*.xml
        │       ├── menu/               # bottom_nav_menu.xml
        │       └── values/             # colors.xml (Black/Violet/Red), themes.xml
        └── src/test/java/com/lifepro/ai/
            └── LifeProUnitTests.kt     # 10 comprehensive unit tests
```

---

## 🧪 Unit Tests

LifePro-AI includes a comprehensive JUnit test suite verified with zero failures:
* `testEmergencyIntentDetection`: Accurately parses distress keywords ("help", "fallen", "ambulance").
* `testPairingCodeFormat`: Validates 6-character alphanumeric pairing token structure (`LP-XXXXXX`).
* `testFallDetectionInquiryLogic`: Confirms the AI asks clarifying questions before triggering dispatch.
* `testResolveFavoriteContact`: Resolves "call my favorite" to Priority 1 contact.
* `testResolveDaughter`: Natural language resolver maps "daughter" to family contact.
* `testResolveSon`: Natural language resolver maps "son" to family contact.
* `testResolveDoctorByName`: Resolves doctor contacts by direct name.
* `testAmbiguousContactReturnsNull`: Prompts user clarification on unknown contacts.
* `testSequentialEmergencyPriorityOrder`: Verifies emergency contact fallback order.
* `testCancellationVoiceTrigger`: Ensures "cancel" aborts active countdowns immediately.

Run tests locally:
```bash
cd android
./gradlew testDebugUnitTest
```

---

## 🛠️ Building & Installation

### Option 1: Direct APK Download
* Download the compiled APK directly from the live portal: [https://life-pro-ai.vercel.app/LifePro-AI.apk](https://life-pro-ai.vercel.app/LifePro-AI.apk)
* Or grab the release directly from GitHub Releases.

### Option 2: Build from Source
```bash
# Clone the repository
git clone https://github.com/lifehaishetty/LifePro-AI.git
cd LifePro-AI/android

# Build the Debug APK using JDK 17
./gradlew assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔒 Security & Privacy Commitments
* **Zero Client Keys**: All LLM and API secrets remain protected on the backend.
* **Explicit Remote Camera Consent**: Visible persistent status bar warning and audio chime whenever remote camera is accessed.
* **Pairing Revocation**: The user can revoke any connected guardian at any time with a single tap or voice command.
* **10-Second False-Alarm Buffer**: Prevents accidental emergency calls with vocal countdown and one-touch abort.
