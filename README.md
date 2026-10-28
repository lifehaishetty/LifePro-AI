# Life Pro.AI 👁️👵🛡️
> **An AI-powered voice safety companion and guardian monitoring assistant designed specifically for visually challenged individuals and elderly citizens.**

---

## 💡 The Problem

Millions of **visually impaired individuals** and **elderly citizens** living independently face severe safety and accessibility challenges every day:
* **Complex Touchscreens**: Navigating smartphone apps, reading small text, or dialing emergency numbers during panic is nearly impossible for someone who is visually impaired or elderly.
* **Sudden Emergencies**: Falls, cardiac events, disorientation, or sudden medical distress require immediate assistance without needing to unlock a phone or look at a screen.
* **Caregiver Anxiety**: Family members and guardians struggle to monitor vulnerable loved ones 24/7 while respecting their independence.

---

## 🌟 The Solution: Life Pro.AI

**Life Pro.AI** transforms any Android phone into an **always-listening, voice-first guardian**. It eliminates the need to touch or view the screen by offering natural voice interaction, automated emergency response, and caregiver peace of mind.

---

## 🚀 Key Features

### 1. 🎙️ 100% Voice-First Operation ("Hello Life")
* Designed for zero-vision usage: users simply speak to the phone by saying **"Hello Life"**.
* Every AI response is **spoken aloud** in a calm, clear human voice (powered by ElevenLabs / Text-To-Speech) so users never need to look at a display.

### 2. 🚨 Automated 10-Second Emergency SOS (Indian National 112)
* If the user falls or shouts **"Help!"**, **"Call ambulance!"**, or **"Save me!"**, the AI immediately detects the distress.
* It speaks a **10-second voice countdown**: *"Calling Indian Helpline in 10 seconds. Say cancel to abort."*
* If not cancelled (in case of unconsciousness or severe injury), it automatically dials **112 (Indian Emergency Helpline)**.

### 3. 👥 Guardian & Caregiver Mode
* **Dual-Role Design**:
  * **User Mode**: Voice assistant, auto-SOS, and hands-free assistance.
  * **Guardian Mode**: For children, doctors, or caregivers to track live GPS location and receive instant alert notifications.

### 4. 📹 Remote Video Check-In ("FaceTime" for Caregivers)
* Caregivers can initiate a quick camera check-in to visually verify that their elder parent or visually impaired family member is safe and hasn't fallen.

### 5. 🧠 Intelligent Brain: NVIDIA Nemotron on Nebius
* Powered by **`nvidia/llama-3.1-nemotron-70b-instruct`**, the assistant interprets intent, provides patient reassurance, and converts spoken phrases into phone actions:
  ```json
  {
    "reply": "Emergency detected. Initiating 112 Indian SOS dispatch now.",
    "action": "sos",
    "contact": "112"
  }
  ```

---

## 🏗️ Architecture

```
+-----------------------------------------------------------+
|              Android Phone (User / Guardian)              |
|        - Voice-First UI (SpeechRecognizer + TTS)          |
|        - 10-Second Auto-Dialer (Indian SOS 112)           |
|        - Live GPS & Camera Check-in                       |
+-----------------------------+-----------------------------+
                              |
                     HTTP JSON| POST /api/chat
                              v
+-----------------------------+-----------------------------+
|                     Node.js Cloud Server                  |
|        - Secures API Keys (Never exposed on phone)        |
|        - Emergency Action Parser                          |
+---------------+-----------------------------+-------------+
                |                             |
                v                             v
+---------------+-------------+   +-----------+-------------+
|   NVIDIA Nemotron 70B       |   |       ElevenLabs        |
|  (Nebius Token Factory)     |   |   (Human-Realistic TTS) |
|    - The Compassionate      |   |   - Clear voice readout |
|      Safety Brain           |   |     for vision-impaired |
+-----------------------------+   +-------------------------+
```

---

## 📂 Project Structure

```
lifepro-ai/
├── README.md               # Project overview & accessibility mission
├── .gitignore              # Protects secrets & ignores build artifacts
├── server/                 # Node.js backend proxy
│   ├── server.js           # Server with Nemotron & ElevenLabs integration
│   ├── package.json        # Configuration
│   ├── .env.example        # Template
│   └── .env                # Local secrets (Never committed)
└── android/                # Native Android application
    ├── build.gradle.kts
    ├── settings.gradle.kts
    └── app/src/main/
        ├── AndroidManifest.xml # Permissions (Audio, Call, Location, Camera)
        ├── res/layout/activity_main.xml # High-contrast accessible UI
        └── java/com/lifepro/ai/
            └── MainActivity.kt # Voice listener, SOS timer & controller
```

---

## 🔒 Safety & Privacy
* **Zero Client Keys**: All API credentials stay encrypted on the backend.
* **False-Alarm Protection**: 10-second audible countdown stops accidental 112 calls.
* **Discreet Fallback**: Large high-contrast buttons and silent text input for situations where speech is unsafe.
