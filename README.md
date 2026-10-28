# Life Pro.AI Chatbot 🚶‍♀️🛡️
> An AI-powered personal safety and companion assistant built for real-time protection, reassuring voice dialogue, and automated safety actions.

---

## 🌟 Overview

**Life Pro.AI** acts as your discreet, responsive guardian on your phone. Whether walking home late at night or navigating unfamiliar surroundings, Life Pro.AI listens, speaks with a human voice, and can trigger automated safety actions (like calling emergency contacts or triggering alerts) via structured AI output.

---

## 🏗️ Architecture

```
+------------------------------------+
|         Android Phone App          |
|      (Kotlin + OkHttp + TTS)       |
|  - Listens, displays text & plays  |
|  - Triggers phone actions          |
+-----------------+------------------+
                  |
         HTTP JSON| (e.g. POST /api/chat)
                  v
+-----------------+------------------+
|           Server (Node.js)         |
|         (Render / Cloud Host)      |
|  - Secures API keys                |
|  - Manages prompts & audio streams |
+--------+------------------+--------+
         |                  |
         v                  v
+--------+---------+  +-----+----------+
|  NVIDIA Nemotron |  |   ElevenLabs   |
| (Nebius Factory) |  | (Rachel Voice) |
|   - The Brain    |  |  - High Quality|
|   - JSON Actions |  |    Voice Audio |
+------------------+  +----------------+
```

---

## 📋 The JSON Action Protocol

Nemotron always returns a structured JSON payload so the phone knows what physical action to perform:

```json
{
  "reply": "I am right here with you. Keep walking toward the main street.",
  "action": "none",
  "contact": ""
}
```

Supported actions:
* `none`: Normal conversation and reassurance.
* `call`: Prepares or dials an emergency contact.
* `sos`: Triggers emergency siren / loud alert.
* `camera`: Prepares quick camera snapshot mode.

---

## 📂 Project Structure

```
lifepro-ai/
├── README.md               # Project documentation & Hackathon pitch
├── .gitignore              # Protects secrets & ignores build artifacts
├── server/                 # Node.js backend proxy
│   ├── server.js           # Server with Nebius + ElevenLabs integration
│   ├── package.json        # Server configuration & dependencies
│   ├── .env.example        # Environment variable template
│   └── .env                # Local secrets (NEVER committed to git)
└── android/                # Native Android application
    ├── build.gradle.kts    # Project-level Gradle build file
    ├── settings.gradle.kts # Subproject configuration
    └── app/                # Main Android application module
        ├── build.gradle.kts# App-level Gradle (includes OkHttp)
        └── src/main/
            ├── AndroidManifest.xml # Permissions (INTERNET)
            ├── res/
            │   ├── layout/
            │   │   └── activity_main.xml # Chat UI
            │   └── values/               # Colors, Strings, Theme
            └── java/com/lifepro/ai/
                └── MainActivity.kt       # Network & UI controller
```

---

## 🚀 Quickstart Guide

### 1. Server Setup
1. Open a terminal in `lifepro-ai/server`:
   ```bash
   cd server
   ```
2. Configure `.env` with your API keys:
   ```env
   PORT=3000
   NEBIUS_API_KEY=your_nebius_api_key
   ELEVENLABS_API_KEY=your_elevenlabs_api_key
   ```
3. Start the server:
   ```bash
   node server.js
   ```

### 2. Android App Setup
1. Open the `lifepro-ai/android` directory in **Android Studio**.
2. Wait for Gradle Sync to complete.
3. Open `MainActivity.kt` and set `SERVER_URL` to your server's address:
   * **Real phone on local Wi-Fi**: `http://<YOUR_COMPUTER_IP>:3000/api/chat`
   * **Android Emulator**: `http://10.0.2.2:3000/api/chat`
   * **Deployed Render URL**: `https://<YOUR_APP_NAME>.onrender.com/api/chat`
4. Connect your phone via USB (with **USB Debugging** enabled) and press **Run ▶️**.

---

## 🗺️ Hackathon Roadmap
- [x] **Phase 1**: Backend architecture & secure API proxy design.
- [x] **Phase 2**: Text input UI, network layer, and live AI response rendering.
- [ ] **Phase 3**: ElevenLabs realistic audio streaming integration on Android.
- [ ] **Phase 4**: Voice input (Speech-to-Text) with continuous listening.
- [ ] **Phase 5**: Background foreground service for screen-off protection.
- [ ] **Phase 6**: Action executor (auto-dial emergency contact, SOS alerts).

---

## 🔒 Security Best Practices
* **No API keys on the client**: All secret keys remain strictly on the server.
* **CORS & Cleartext rules**: Configured for seamless development testing and production SSL.
