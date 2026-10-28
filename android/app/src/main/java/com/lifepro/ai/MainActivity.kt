package com.lifepro.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // =========================================================================
    // SERVER CONFIGURATION
    // Connected over USB cable via port reverse forwarding:
    // =========================================================================
    private val SERVER_URL = "http://localhost:3000/api/chat"

    private val httpClient = OkHttpClient()
    private val PERMISSION_REQUEST_CODE = 101

    // Role state
    private var isUserMode = true

    // Emergency countdown
    private var sosTimer: CountDownTimer? = null
    private val SOS_COUNTDOWN_MS: Long = 10000 // 10 seconds

    // Voice components
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false
    private var autoListenLoop = true // Continuous conversational mode for vision impairment

    // Views
    private lateinit var tvReply: TextView
    private lateinit var tvActionBadge: TextView
    private lateinit var tvVoiceStatus: TextView
    private lateinit var tvModeSubtitle: TextView
    private lateinit var tvLiveStatus: TextView
    private lateinit var layoutSosCountdown: LinearLayout
    private lateinit var tvCountdown: TextView
    private lateinit var btnCancelSos: Button
    private lateinit var btnTriggerSos: Button
    private lateinit var btnVoiceListen: Button
    private lateinit var btnRoleUser: Button
    private lateinit var btnRoleGuardian: Button
    private lateinit var btnQuickCall: Button
    private lateinit var btnCameraCheck: Button
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        requestAppPermissions()
        initTextToSpeech()
        initSpeechRecognizer()
        setupListeners()
        startBackgroundProtectionService()
    }

    private fun initViews() {
        tvReply = findViewById(R.id.tvReply)
        tvActionBadge = findViewById(R.id.tvActionBadge)
        tvVoiceStatus = findViewById(R.id.tvVoiceStatus)
        tvModeSubtitle = findViewById(R.id.tvModeSubtitle)
        tvLiveStatus = findViewById(R.id.tvLiveStatus)
        layoutSosCountdown = findViewById(R.id.layoutSosCountdown)
        tvCountdown = findViewById(R.id.tvCountdown)
        btnCancelSos = findViewById(R.id.btnCancelSos)
        btnTriggerSos = findViewById(R.id.btnTriggerSos)
        btnVoiceListen = findViewById(R.id.btnVoiceListen)
        btnRoleUser = findViewById(R.id.btnRoleUser)
        btnRoleGuardian = findViewById(R.id.btnRoleGuardian)
        btnQuickCall = findViewById(R.id.btnQuickCall)
        btnCameraCheck = findViewById(R.id.btnCameraCheck)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
    }

    private fun setupListeners() {
        // Role Selection
        btnRoleUser.setOnClickListener { setRole(userMode = true) }
        btnRoleGuardian.setOnClickListener { setRole(userMode = false) }

        // SOS Triggers
        btnTriggerSos.setOnClickListener { start10SecondSosCountdown("112") }
        btnCancelSos.setOnClickListener { cancelSos() }

        // Voice Listen Button
        btnVoiceListen.setOnClickListener { toggleVoiceListening() }

        // Quick Calls & Camera
        btnQuickCall.setOnClickListener { callEmergencyNumber("112") }
        btnCameraCheck.setOnClickListener { openFaceTimeCameraCheck() }

        // Text Message Send (Silent mode fallback)
        btnSend.setOnClickListener {
            val userText = etMessage.text.toString().trim()
            if (userText.isNotEmpty()) {
                etMessage.text.clear()
                handleUserInput(userText)
            }
        }
    }

    // =========================================================================
    // BACKGROUND SERVICE: Keeps app alive when screen turns off in pocket
    // =========================================================================
    private fun startBackgroundProtectionService() {
        val serviceIntent = Intent(this, SafetyForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    // =========================================================================
    // ROLE SWITCHER: User (Senior/Vision-impaired) vs Guardian (Caregiver)
    // =========================================================================
    private fun setRole(userMode: Boolean) {
        isUserMode = userMode
        if (userMode) {
            btnRoleUser.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
            btnRoleUser.setTextColor(0xFFFFFFFF.toInt())
            btnRoleGuardian.setBackgroundColor(ContextCompat.getColor(this, R.color.background))
            btnRoleGuardian.setTextColor(ContextCompat.getColor(this, R.color.text_primary))

            tvModeSubtitle.text = "User Mode • Say 'Hello Life' for help"
            tvLiveStatus.text = "● PROTECTED"
            tvLiveStatus.setTextColor(ContextCompat.getColor(this, R.color.accent))
            speakAloud("User mode active. Safe walk protection is on.")
        } else {
            btnRoleGuardian.setBackgroundColor(ContextCompat.getColor(this, R.color.guardian_purple))
            btnRoleGuardian.setTextColor(0xFFFFFFFF.toInt())
            btnRoleUser.setBackgroundColor(ContextCompat.getColor(this, R.color.background))
            btnRoleUser.setTextColor(ContextCompat.getColor(this, R.color.text_primary))

            val locationInfo = getLiveLocationString()
            tvModeSubtitle.text = "Guardian Mode • Location: $locationInfo"
            tvLiveStatus.text = "● MONITORING"
            tvLiveStatus.setTextColor(ContextCompat.getColor(this, R.color.guardian_purple))
            speakAloud("Guardian mode connected. Current location tracked: $locationInfo")
        }
    }

    // =========================================================================
    // LIVE LOCATION TRACKING
    // =========================================================================
    private fun getLiveLocationString(): String {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "GPS permission pending"
        }
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        var lastLocation: Location? = null
        try {
            lastLocation = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            // Ignored
        }

        return if (lastLocation != null) {
            "Lat: ${"%.4f".format(lastLocation.latitude)}, Lon: ${"%.4f".format(lastLocation.longitude)}"
        } else {
            "Locating satellite..."
        }
    }

    // =========================================================================
    // 10-SECOND EMERGENCY SOS COUNTDOWN (INDIAN 112)
    // =========================================================================
    private fun start10SecondSosCountdown(targetNumber: String) {
        sosTimer?.cancel()
        layoutSosCountdown.visibility = View.VISIBLE
        speakAloud("Emergency SOS activated. Calling Indian Helpline in ten seconds. Say cancel to abort.")

        sosTimer = object : CountDownTimer(SOS_COUNTDOWN_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                tvCountdown.text = "Calling Indian SOS ($targetNumber) in ${secondsRemaining}s..."
            }

            override fun onFinish() {
                layoutSosCountdown.visibility = View.GONE
                tvCountdown.text = "Connecting emergency call..."
                callEmergencyNumber(targetNumber)
            }
        }.start()
    }

    private fun cancelSos() {
        sosTimer?.cancel()
        sosTimer = null
        layoutSosCountdown.visibility = View.GONE
        speakAloud("Emergency SOS cancelled. You are safe.")
        Toast.makeText(this, "Emergency SOS Cancelled", Toast.LENGTH_SHORT).show()
    }

    // =========================================================================
    // CALLING & CAMERA (INDIAN SOS 112 / CONTACTS / FACETIME)
    // =========================================================================
    private fun callEmergencyNumber(number: String) {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(intent)
        } else {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
            startActivity(dialIntent)
        }
    }

    private fun openFaceTimeCameraCheck() {
        speakAloud("Opening video check-in for guardian.")
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Camera not available: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // =========================================================================
    // VOICE ASSISTANT ("HELLO LIFE" & HANDS-FREE SPEECH RECOGNITION)
    // =========================================================================
    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        tvVoiceStatus.text = "🎤 Listening for 'Hello Life'..."
                    }
                    override fun onBeginningOfSpeech() {
                        tvVoiceStatus.text = "🎤 Hearing voice..."
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        tvVoiceStatus.text = "Thinking..."
                        isListening = false
                        btnVoiceListen.text = "🎙️ Tap to Speak (or say 'Hello Life')"
                    }
                    override fun onError(error: Int) {
                        isListening = false
                        tvVoiceStatus.text = "Ready. Say 'Hello Life'"
                        btnVoiceListen.text = "🎙️ Tap to Speak (or say 'Hello Life')"
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognizedText = matches[0]
                            handleUserInput(recognizedText)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    private fun toggleVoiceListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            btnVoiceListen.text = "🎙️ Tap to Speak (or say 'Hello Life')"
            tvVoiceStatus.text = "Microphone paused"
        } else {
            startVoiceListening()
        }
    }

    private fun startVoiceListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAppPermissions()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say 'Hello Life' or what you need...")
        }
        speechRecognizer?.startListening(intent)
        isListening = true
        btnVoiceListen.text = "⏹️ Stop Listening"
        tvVoiceStatus.text = "🎤 Listening..."
    }

    private fun handleUserInput(text: String) {
        tvReply.text = "You said: \"$text\"\n\nContacting Life Pro.AI..."
        val lower = text.toLowerCase(Locale.ROOT)

        // Instant emergency shout detection (bypasses network lag for urgent safety)
        if (lower.contains("help") || lower.contains("ambulance") || lower.contains("save me") || lower.contains("danger")) {
            start10SecondSosCountdown("112")
            return
        }

        // Cancel voice trigger
        if (lower.contains("cancel") && layoutSosCountdown.visibility == View.VISIBLE) {
            cancelSos()
            return
        }

        // Send to Nemotron for reasoning and actions
        sendMessageToServer(text)
    }

    // =========================================================================
    // NEMOTRON AI SERVER COMMUNICATION
    // =========================================================================
    private fun sendMessageToServer(message: String) {
        val jsonPayload = JSONObject().apply {
            put("message", message)
            put("location", getLiveLocationString())
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(SERVER_URL).post(requestBody).build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    tvReply.text = "Could not reach server: ${e.localizedMessage}"
                    tvActionBadge.text = "[Action: offline]"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                runOnUiThread {
                    try {
                        val json = JSONObject(responseBody)
                        val reply = json.optString("reply", "I am right here with you.")
                        val action = json.optString("action", "none")
                        val contact = json.optString("contact", "")

                        tvReply.text = reply
                        tvActionBadge.text = if (contact.isNotEmpty()) "[Action: $action -> $contact]" else "[Action: $action]"

                        // Speak AI reply aloud so vision-impaired users don't need to look at screen
                        speakAloud(reply)

                        // Execute requested action
                        when (action) {
                            "sos" -> start10SecondSosCountdown(if (contact.isNotEmpty()) contact else "112")
                            "call" -> {
                                val target = if (contact.matches(Regex("\\d+"))) contact else "112"
                                start10SecondSosCountdown(target)
                            }
                            "camera" -> openFaceTimeCameraCheck()
                        }
                    } catch (e: Exception) {
                        tvReply.text = responseBody
                    }
                }
            }
        })
    }

    // =========================================================================
    // TEXT TO SPEECH (VOICE RESPONSES WITH AUTO-RESUME LISTENING)
    // =========================================================================
    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(this, this)
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                // Once speech finishes, automatically listen again for continuous conversation
                if (autoListenLoop && isUserMode) {
                    runOnUiThread {
                        startVoiceListening()
                    }
                }
            }
            override fun onError(utteranceId: String?) {}
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
        }
    }

    private fun speakAloud(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "LIFEPRO_VOICE_${System.currentTimeMillis()}")
    }

    // =========================================================================
    // RUNTIME PERMISSIONS
    // =========================================================================
    private fun requestAppPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, ungranted.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sosTimer?.cancel()
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}
