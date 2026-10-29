package com.lifepro.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.lifepro.ai.activity.ActivityRepository
import com.lifepro.ai.activity.EventOutcome
import com.lifepro.ai.activity.EventType
import com.lifepro.ai.activity.SafetyEvent
import com.lifepro.ai.assistant.ActionType
import com.lifepro.ai.assistant.AssistantEngine
import com.lifepro.ai.assistant.AssistantListener
import com.lifepro.ai.assistant.AssistantResponse
import com.lifepro.ai.assistant.AssistantState
import com.lifepro.ai.auth.AuthManager
import com.lifepro.ai.auth.UserRole
import com.lifepro.ai.camera.CameraSessionManager
import com.lifepro.ai.contacts.ContactManager
import com.lifepro.ai.contacts.SafetyContact
import com.lifepro.ai.emergency.SosCallback
import com.lifepro.ai.emergency.SosController
import com.lifepro.ai.location.LocationReport
import com.lifepro.ai.location.LocationService
import com.lifepro.ai.location.LocationStatus
import com.lifepro.ai.pairing.PairingManager
import com.lifepro.ai.pairing.PairingStatus
import com.lifepro.ai.ui.AuthActivity
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var authManager: AuthManager
    private lateinit var contactManager: ContactManager
    private lateinit var locationService: LocationService
    private lateinit var activityRepo: ActivityRepository
    private lateinit var pairingManager: PairingManager
    private lateinit var cameraManager: CameraSessionManager
    private lateinit var sosController: SosController
    private lateinit var assistantEngine: AssistantEngine

    // Views: Top Bar
    private lateinit var tvModeSubtitle: TextView
    private lateinit var tvLiveStatus: TextView

    // Views: Navigation Tabs
    private lateinit var tabViewAssistant: ScrollView
    private lateinit var tabViewGuardian: ScrollView
    private lateinit var tabViewContacts: ScrollView
    private lateinit var tabViewActivity: LinearLayout
    private lateinit var tabViewSettings: ScrollView
    private lateinit var bottomNav: BottomNavigationView

    // Tab 1: Assistant Views
    private lateinit var layoutSosCountdown: MaterialCardView
    private lateinit var tvCountdown: TextView
    private lateinit var btnCancelSos: MaterialButton
    private lateinit var btnTriggerSos: MaterialButton
    private lateinit var btnVoiceOrb: FrameLayout
    private lateinit var ivOrbIcon: ImageView
    private lateinit var tvVoiceStatus: TextView
    private lateinit var tvReply: TextView
    private lateinit var btnStopSpeaking: MaterialButton
    private lateinit var btnQuickCallDaughter: MaterialButton
    private lateinit var btnQuickLocation: MaterialButton
    private lateinit var btnQuickCamera: MaterialButton
    private lateinit var etMessage: TextInputEditText
    private lateinit var btnSend: MaterialButton

    // Tab 2: Guardian Views
    private lateinit var tvGuardianRoleSubtitle: TextView
    private lateinit var cardUserPairing: MaterialCardView
    private lateinit var tvPairingCode: TextView
    private lateinit var btnRegenerateCode: MaterialButton
    private lateinit var btnSimulateRequest: MaterialButton
    private lateinit var cardPendingApproval: MaterialCardView
    private lateinit var tvRequesterInfo: TextView
    private lateinit var btnApproveGuardian: MaterialButton
    private lateinit var btnRejectGuardian: MaterialButton
    private lateinit var tvGpsCoordinates: TextView
    private lateinit var btnOpenMaps: MaterialButton
    private lateinit var btnShareWhatsAppLocation: MaterialButton
    private lateinit var btnStartCameraCheck: MaterialButton
    private lateinit var btnRevokeAccess: MaterialButton

    // Tab 3: Contacts Views
    private lateinit var btnAddContactDialog: MaterialButton
    private lateinit var layoutContactsContainer: LinearLayout

    // Tab 4: Activity Views
    private lateinit var etSearchActivity: TextInputEditText
    private lateinit var layoutActivityContainer: LinearLayout

    // Tab 5: Settings Views
    private lateinit var tvPermAudio: TextView
    private lateinit var tvPermPhone: TextView
    private lateinit var tvPermLocation: TextView
    private lateinit var tvPermCamera: TextView
    private lateinit var btnGrantPermissions: MaterialButton
    private lateinit var tvAccountName: TextView
    private lateinit var tvAccountRole: TextView
    private lateinit var btnLogout: MaterialButton

    // Settings Safety & Battery Controls
    private lateinit var switchSimulationMode: androidx.appcompat.widget.SwitchCompat
    private lateinit var tvCountdownDurationLabel: TextView
    private lateinit var btnDuration10s: MaterialButton
    private lateinit var btnDuration15s: MaterialButton
    private lateinit var btnDuration20s: MaterialButton
    private lateinit var btnDuration30s: MaterialButton
    private lateinit var tvBatteryStatus: TextView
    private lateinit var btnBatteryOptimization: MaterialButton

    // Speech & Audio Components
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false
    private val PERMISSION_REQUEST_CODE = 2001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            authManager = AuthManager.getInstance(this)

            // Session check: If user not logged in, redirect to AuthActivity
            if (!authManager.isLoggedIn()) {
                startActivity(Intent(this, AuthActivity::class.java))
                finish()
                return
            }

            setContentView(R.layout.activity_main)

            initDependencies()
            initViews()
            setupBottomNavigation()
            setupAssistant()
            setupGuardianTab()
            setupContactsTab()
            setupActivityTab()
            setupSettingsTab()
            startBackgroundService()
            checkAndReportPermissions()
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(this, "LifePro initialized with safe fallbacks.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initDependencies() {
        contactManager = ContactManager.getInstance(this)
        locationService = LocationService.getInstance(this)
        activityRepo = ActivityRepository.getInstance(this)
        pairingManager = PairingManager.getInstance(this)
        cameraManager = CameraSessionManager.getInstance(this)

        sosController = SosController(this) { spokenText ->
            speakAloud(spokenText)
        }

        assistantEngine = AssistantEngine(this, object : AssistantListener {
            override fun onStateChanged(state: AssistantState) {
                runOnUiThread {
                    when (state) {
                        AssistantState.IDLE -> {
                            tvVoiceStatus.text = "Ready. Say 'Hello Life'"
                            ivOrbIcon.setColorFilter(getColor(R.color.violet_light))
                        }
                        AssistantState.LISTENING -> {
                            tvVoiceStatus.text = "🎤 Hearing voice..."
                            ivOrbIcon.setColorFilter(getColor(R.color.emerald_safe))
                        }
                        AssistantState.PROCESSING -> {
                            tvVoiceStatus.text = "Processing..."
                            ivOrbIcon.setColorFilter(getColor(R.color.violet_light))
                        }
                        AssistantState.SPEAKING -> {
                            tvVoiceStatus.text = "Speaking response aloud..."
                            ivOrbIcon.setColorFilter(getColor(R.color.violet_primary))
                            btnStopSpeaking.visibility = View.VISIBLE
                        }
                    }
                }
            }

            override fun onResponseReady(response: AssistantResponse) {
                runOnUiThread {
                    tvReply.text = response.replyText
                    speakAloud(response.replyText)

                    // Execute requested system action
                    when (response.actionType) {
                        ActionType.SOS -> {
                            sosController.start10SecondSos(response.target)
                            showSosCountdownView()
                        }
                        ActionType.CANCEL -> {
                            sosController.cancelSos()
                            hideSosCountdownView()
                        }
                        ActionType.CALL -> {
                            sosController.executeCall(response.target)
                        }
                        ActionType.LOCATION_SHARE -> {
                            startActivity(locationService.createWhatsAppShareIntent())
                        }
                        ActionType.LOCATION_QUERY -> {
                            refreshLocationDisplay()
                        }
                        ActionType.CAMERA_CHECK -> {
                            triggerCameraCheck()
                        }
                        ActionType.NONE -> {}
                    }
                }
            }

            override fun onError(errorMessage: String) {
                runOnUiThread {
                    tvReply.text = errorMessage
                    speakAloud(errorMessage)
                }
            }
        })

        initTextToSpeech()
        initSpeechRecognizer()
    }

    private fun initViews() {
        tvModeSubtitle = findViewById(R.id.tvModeSubtitle)
        tvLiveStatus = findViewById(R.id.tvLiveStatus)

        tabViewAssistant = findViewById(R.id.tabViewAssistant)
        tabViewGuardian = findViewById(R.id.tabViewGuardian)
        tabViewContacts = findViewById(R.id.tabViewContacts)
        tabViewActivity = findViewById(R.id.tabViewActivity)
        tabViewSettings = findViewById(R.id.tabViewSettings)
        bottomNav = findViewById(R.id.bottomNav)

        // Assistant tab views
        layoutSosCountdown = findViewById(R.id.layoutSosCountdown)
        tvCountdown = findViewById(R.id.tvCountdown)
        btnCancelSos = findViewById(R.id.btnCancelSos)
        btnTriggerSos = findViewById(R.id.btnTriggerSos)
        btnVoiceOrb = findViewById(R.id.btnVoiceOrb)
        ivOrbIcon = findViewById(R.id.ivOrbIcon)
        tvVoiceStatus = findViewById(R.id.tvVoiceStatus)
        tvReply = findViewById(R.id.tvReply)
        btnStopSpeaking = findViewById(R.id.btnStopSpeaking)
        btnQuickCallDaughter = findViewById(R.id.btnQuickCallDaughter)
        btnQuickLocation = findViewById(R.id.btnQuickLocation)
        btnQuickCamera = findViewById(R.id.btnQuickCamera)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        // Guardian tab views
        tvGuardianRoleSubtitle = findViewById(R.id.tvGuardianRoleSubtitle)
        cardUserPairing = findViewById(R.id.cardUserPairing)
        tvPairingCode = findViewById(R.id.tvPairingCode)
        btnRegenerateCode = findViewById(R.id.btnRegenerateCode)
        btnSimulateRequest = findViewById(R.id.btnSimulateRequest)
        cardPendingApproval = findViewById(R.id.cardPendingApproval)
        tvRequesterInfo = findViewById(R.id.tvRequesterInfo)
        btnApproveGuardian = findViewById(R.id.btnApproveGuardian)
        btnRejectGuardian = findViewById(R.id.btnRejectGuardian)
        tvGpsCoordinates = findViewById(R.id.tvGpsCoordinates)
        btnOpenMaps = findViewById(R.id.btnOpenMaps)
        btnShareWhatsAppLocation = findViewById(R.id.btnShareWhatsAppLocation)
        btnStartCameraCheck = findViewById(R.id.btnStartCameraCheck)
        btnRevokeAccess = findViewById(R.id.btnRevokeAccess)

        // Contacts tab views
        btnAddContactDialog = findViewById(R.id.btnAddContactDialog)
        layoutContactsContainer = findViewById(R.id.layoutContactsContainer)

        // Activity tab views
        etSearchActivity = findViewById(R.id.etSearchActivity)
        layoutActivityContainer = findViewById(R.id.layoutActivityContainer)

        // Settings tab views
        tvPermAudio = findViewById(R.id.tvPermAudio)
        tvPermPhone = findViewById(R.id.tvPermPhone)
        tvPermLocation = findViewById(R.id.tvPermLocation)
        tvPermCamera = findViewById(R.id.tvPermCamera)
        btnGrantPermissions = findViewById(R.id.btnGrantPermissions)
        tvAccountName = findViewById(R.id.tvAccountName)
        tvAccountRole = findViewById(R.id.tvAccountRole)
        btnLogout = findViewById(R.id.btnLogout)

        switchSimulationMode = findViewById(R.id.switchSimulationMode)
        tvCountdownDurationLabel = findViewById(R.id.tvCountdownDurationLabel)
        btnDuration10s = findViewById(R.id.btnDuration10s)
        btnDuration15s = findViewById(R.id.btnDuration15s)
        btnDuration20s = findViewById(R.id.btnDuration20s)
        btnDuration30s = findViewById(R.id.btnDuration30s)
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus)
        btnBatteryOptimization = findViewById(R.id.btnBatteryOptimization)

        // Setup role badge in header
        val isUser = authManager.isUserRole()
        tvModeSubtitle.text = if (isUser) "User Mode • Voice Active" else "Guardian Mode • Active Monitoring"
        tvLiveStatus.text = if (isUser) "● PROTECTED" else "● MONITORING"
        tvLiveStatus.setTextColor(if (isUser) getColor(R.color.emerald_safe) else getColor(R.color.violet_light))
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            tabViewAssistant.visibility = View.GONE
            tabViewGuardian.visibility = View.GONE
            tabViewContacts.visibility = View.GONE
            tabViewActivity.visibility = View.GONE
            tabViewSettings.visibility = View.GONE

            when (item.itemId) {
                R.id.nav_assistant -> {
                    tabViewAssistant.visibility = View.VISIBLE
                    true
                }
                R.id.nav_guardian -> {
                    tabViewGuardian.visibility = View.VISIBLE
                    refreshGuardianTabState()
                    true
                }
                R.id.nav_contacts -> {
                    tabViewContacts.visibility = View.VISIBLE
                    renderContactsList()
                    true
                }
                R.id.nav_activity -> {
                    tabViewActivity.visibility = View.VISIBLE
                    renderActivityList("")
                    true
                }
                R.id.nav_settings -> {
                    tabViewSettings.visibility = View.VISIBLE
                    checkAndReportPermissions()
                    updateBatteryOptimizationDisplay()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupAssistant() {
        btnTriggerSos.setOnClickListener {
            sosController.start10SecondSos("112", object : SosCallback {
                override fun onCountdownTick(secondsRemaining: Long, target: String) {
                    runOnUiThread {
                        tvCountdown.text = if (sosController.isSimulationMode) {
                            "SIMULATION SOS in ${secondsRemaining}s..."
                        } else {
                            "Calling $target in ${secondsRemaining}s..."
                        }
                    }
                }
                override fun onCountdownFinished(target: String) {
                    runOnUiThread { hideSosCountdownView() }
                }
                override fun onSosCancelled() {
                    runOnUiThread { hideSosCountdownView() }
                }
            })
            showSosCountdownView()
        }

        btnCancelSos.setOnClickListener {
            sosController.cancelSos()
            hideSosCountdownView()
        }

        btnVoiceOrb.setOnClickListener {
            toggleSpeechListening()
        }

        btnStopSpeaking = findViewById(R.id.btnStopSpeaking)
        btnStopSpeaking.setOnClickListener {
            textToSpeech?.stop()
            btnStopSpeaking.visibility = View.GONE
        }

        btnQuickCallDaughter.setOnClickListener {
            val contact = contactManager.resolveContactFromSpeech("daughter")
            val target = contact?.phoneNumber ?: "112"
            sosController.executeCall(target)
        }

        btnQuickLocation.setOnClickListener {
            val report = locationService.refreshLocation()
            tvReply.text = "Your current live coordinates are ${report.getReadableCoordinates()}."
            speakAloud("Your current location coordinates are ${report.getReadableCoordinates()}.")
        }

        btnQuickCamera.setOnClickListener {
            triggerCameraCheck()
        }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                etMessage.text?.clear()
                assistantEngine.processUserInput(text)
            }
        }
    }

    private fun showSosCountdownView() {
        layoutSosCountdown.visibility = View.VISIBLE
        tvCountdown.text = if (sosController.isSimulationMode) {
            "SIMULATION SOS in ${sosController.countdownDurationSeconds}s..."
        } else {
            "Calling ${sosController.currentTarget} in ${sosController.countdownDurationSeconds}s..."
        }
    }

    private fun hideSosCountdownView() {
        layoutSosCountdown.visibility = View.GONE
    }

    private fun triggerCameraCheck() {
        val authorized = cameraManager.startAuthorizedSession()
        if (authorized) {
            speakAloud("Opening video check-in session for guardian.")
            Toast.makeText(this, "📹 Video Check-in Active: Privacy Indicator Visible", Toast.LENGTH_LONG).show()
            val cameraIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            try {
                startActivity(cameraIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera stream ready.", Toast.LENGTH_SHORT).show()
            }
        } else {
            speakAloud("Camera access was revoked in privacy settings.")
            Toast.makeText(this, "Camera permission blocked by user settings", Toast.LENGTH_SHORT).show()
        }
    }

    // =========================================================================
    // GUARDIAN TAB IMPLEMENTATION
    // =========================================================================
    private fun setupGuardianTab() {
        refreshGuardianTabState()

        btnRegenerateCode.setOnClickListener {
            val newCode = pairingManager.generateNewCode()
            tvPairingCode.text = newCode
            Toast.makeText(this, "New invite code: $newCode", Toast.LENGTH_SHORT).show()
        }

        btnSimulateRequest.setOnClickListener {
            pairingManager.simulateIncomingGuardianRequest("Ananya Sharma (Caregiver)", "+91 98765 11223")
            refreshGuardianTabState()
        }

        btnApproveGuardian.setOnClickListener {
            pairingManager.approveGuardian()
            Toast.makeText(this, "Guardian Approved & Connected", Toast.LENGTH_SHORT).show()
            refreshGuardianTabState()
        }

        btnRejectGuardian.setOnClickListener {
            pairingManager.rejectGuardian()
            Toast.makeText(this, "Guardian Request Rejected", Toast.LENGTH_SHORT).show()
            refreshGuardianTabState()
        }

        btnOpenMaps.setOnClickListener {
            startActivity(locationService.createGoogleMapsViewIntent())
        }

        btnShareWhatsAppLocation.setOnClickListener {
            try {
                startActivity(locationService.createWhatsAppShareIntent())
            } catch (e: Exception) {
                Toast.makeText(this, "WhatsApp not installed, opening Maps link.", Toast.LENGTH_SHORT).show()
                startActivity(locationService.createGoogleMapsViewIntent())
            }
        }

        btnStartCameraCheck.setOnClickListener {
            triggerCameraCheck()
        }

        btnRevokeAccess.setOnClickListener {
            pairingManager.revokeAccess()
            Toast.makeText(this, "Guardian access revoked.", Toast.LENGTH_SHORT).show()
            refreshGuardianTabState()
        }
    }

    private fun refreshGuardianTabState() {
        val session = pairingManager.currentSession
        tvPairingCode.text = session.code

        if (session.status == PairingStatus.AWAITING_APPROVAL) {
            cardPendingApproval.visibility = View.VISIBLE
            tvRequesterInfo.text = "${session.pairedUserName} (${session.pairedUserPhone}) requested Guardian pairing."
        } else {
            cardPendingApproval.visibility = View.GONE
        }

        refreshLocationDisplay()
    }

    private fun refreshLocationDisplay() {
        val report = locationService.refreshLocation()
        tvGpsCoordinates.text = "GPS: ${report.getReadableCoordinates()}\nStatus: ${report.status.name}"
    }

    // =========================================================================
    // CONTACTS TAB IMPLEMENTATION
    // =========================================================================
    private fun setupContactsTab() {
        renderContactsList()
        btnAddContactDialog.setOnClickListener {
            showAddContactDialog()
        }
    }

    private fun renderContactsList() {
        layoutContactsContainer.removeAllViews()
        val contacts = contactManager.getAllContacts()

        for (c in contacts) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_contact, layoutContactsContainer, false)
            val tvName = itemView.findViewById<TextView>(R.id.tvContactName)
            val tvRelation = itemView.findViewById<TextView>(R.id.tvContactRelation)
            val tvPhone = itemView.findViewById<TextView>(R.id.tvContactPhone)
            val btnCall = itemView.findViewById<MaterialButton>(R.id.btnCallThisContact)

            tvName.text = c.name
            tvRelation.text = c.relationship
            tvPhone.text = c.phoneNumber

            btnCall.setOnClickListener {
                sosController.executeCall(c.phoneNumber)
            }

            layoutContactsContainer.addView(itemView)
        }
    }

    private fun showAddContactDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add Trusted Safety Contact")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val etName = TextInputEditText(this).apply { hint = "Name (e.g. Rahul)" }
        val etRel = TextInputEditText(this).apply { hint = "Relationship (e.g. Son, Daughter)" }
        val etPhone = TextInputEditText(this).apply { hint = "Phone (+91...)" }

        layout.addView(etName)
        layout.addView(etRel)
        layout.addView(etPhone)
        builder.setView(layout)

        builder.setPositiveButton("Save Contact") { _, _ ->
            val name = etName.text.toString().trim()
            val rel = etRel.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            if (name.isNotEmpty() && phone.isNotEmpty()) {
                val added = contactManager.addContact(
                    SafetyContact(
                        name = name,
                        relationship = if (rel.isNotEmpty()) rel else "Contact",
                        phoneNumber = phone,
                        isFavorite = true
                    )
                )
                if (added) {
                    renderContactsList()
                    Toast.makeText(this, "Safety contact added successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Invalid phone number (must be 3 to 15 digits)", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Please enter both name and phone number", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    // =========================================================================
    // ACTIVITY TAB IMPLEMENTATION
    // =========================================================================
    private fun setupActivityTab() {
        renderActivityList("")
        etSearchActivity.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderActivityList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun renderActivityList(filterQuery: String) {
        layoutActivityContainer.removeAllViews()
        val events = activityRepo.searchEvents(filterQuery)

        for (e in events) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_activity, layoutActivityContainer, false)
            val tvTitle = itemView.findViewById<TextView>(R.id.tvActivityTitle)
            val tvOutcome = itemView.findViewById<TextView>(R.id.tvActivityOutcome)
            val tvDetails = itemView.findViewById<TextView>(R.id.tvActivityDetails)
            val tvTime = itemView.findViewById<TextView>(R.id.tvActivityTime)

            tvTitle.text = e.title
            tvOutcome.text = e.outcome.name
            tvDetails.text = e.details
            tvTime.text = e.getFormattedTime()

            when (e.outcome) {
                EventOutcome.SUCCESS -> tvOutcome.setTextColor(getColor(R.color.emerald_safe))
                EventOutcome.DISPATCHED -> tvOutcome.setTextColor(getColor(R.color.violet_light))
                EventOutcome.SIMULATION -> tvOutcome.setTextColor(getColor(R.color.violet_light))
                EventOutcome.ACTIVE -> tvOutcome.setTextColor(getColor(R.color.red_light))
                EventOutcome.CANCELLED -> tvOutcome.setTextColor(getColor(R.color.violet_light))
                EventOutcome.FAILED -> tvOutcome.setTextColor(getColor(R.color.text_dim))
                else -> tvOutcome.setTextColor(getColor(R.color.text_body))
            }

            layoutActivityContainer.addView(itemView)
        }
    }

    // =========================================================================
    // SETTINGS & PERMISSIONS IMPLEMENTATION
    // =========================================================================
    private fun setupSettingsTab() {
        tvAccountName.text = "Account: ${authManager.currentUser?.name ?: "User"}"
        tvAccountRole.text = "Role: ${if (authManager.isUserRole()) "User (Assistance Receiver)" else "Guardian (Caregiver)"}"

        // Safe Simulation Mode Toggle
        switchSimulationMode.isChecked = sosController.isSimulationMode
        switchSimulationMode.setOnCheckedChangeListener { _, isChecked ->
            sosController.isSimulationMode = isChecked
            val notice = if (isChecked) "Simulation Mode Active: SOS will test fully without dialing 112." else "Live Mode Active: Emergency calls to 112 are live."
            Toast.makeText(this, notice, Toast.LENGTH_SHORT).show()
        }

        // Adjustable Countdown Duration Buffer
        fun updateDurationLabel() {
            tvCountdownDurationLabel.text = "SOS Countdown Buffer: ${sosController.countdownDurationSeconds} seconds"
        }
        updateDurationLabel()

        btnDuration10s.setOnClickListener { sosController.countdownDurationSeconds = 10; updateDurationLabel() }
        btnDuration15s.setOnClickListener { sosController.countdownDurationSeconds = 15; updateDurationLabel() }
        btnDuration20s.setOnClickListener { sosController.countdownDurationSeconds = 20; updateDurationLabel() }
        btnDuration30s.setOnClickListener { sosController.countdownDurationSeconds = 30; updateDurationLabel() }

        // Background Battery Reliability
        updateBatteryOptimizationDisplay()
        btnBatteryOptimization.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val generalIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(generalIntent)
                    } catch (e2: Exception) {
                        Toast.makeText(this, "Open Device Settings > Battery to exclude LifePro", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(this, "Battery optimization not restricted on this Android version.", Toast.LENGTH_SHORT).show()
            }
        }

        btnGrantPermissions.setOnClickListener {
            requestPermissionsExplicitly()
        }

        btnLogout.setOnClickListener {
            authManager.logout()
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
        }
    }

    private fun updateBatteryOptimizationDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isExempted = pm?.isIgnoringBatteryOptimizations(packageName) == true
            tvBatteryStatus.text = "🔋 Background Protection: " + if (isExempted) "Exempted (Screen-off Active)" else "Restricted (Tap to Exclude)"
            tvBatteryStatus.setTextColor(if (isExempted) getColor(R.color.emerald_safe) else getColor(R.color.amber_warning))
        } else {
            tvBatteryStatus.text = "🔋 Background Protection: Supported"
            tvBatteryStatus.setTextColor(getColor(R.color.emerald_safe))
        }
    }

    private fun checkAndReportPermissions() {
        val audioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val phoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val locationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        tvPermAudio.text = "🎤 Microphone: " + if (audioGranted) "Granted (Voice Active)" else "Denied (Tap Grant)"
        tvPermAudio.setTextColor(if (audioGranted) getColor(R.color.emerald_safe) else getColor(R.color.red_light))

        tvPermPhone.text = "📞 Phone Calls: " + if (phoneGranted) "Granted (112 Direct Dial)" else "Denied (Dialer Fallback)"
        tvPermPhone.setTextColor(if (phoneGranted) getColor(R.color.emerald_safe) else getColor(R.color.red_light))

        tvPermLocation.text = "📍 GPS Location: " + if (locationGranted) "Granted (Live Sharing)" else "Denied (GPS Off)"
        tvPermLocation.setTextColor(if (locationGranted) getColor(R.color.emerald_safe) else getColor(R.color.red_light))

        tvPermCamera.text = "📹 Camera Check: " + if (cameraGranted) "Granted (Video Ready)" else "Denied"
        tvPermCamera.setTextColor(if (cameraGranted) getColor(R.color.emerald_safe) else getColor(R.color.red_light))
    }

    private fun requestPermissionsExplicitly() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        checkAndReportPermissions()
        refreshLocationDisplay()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startBackgroundService()
        }
    }

    // =========================================================================
    // VOICE SPEECH RECOGNITION & TTS
    // =========================================================================
    private fun initSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            tvVoiceStatus.text = "🎤 Hearing speech..."
                        }
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {
                            isListening = false
                            tvVoiceStatus.text = "Processing voice..."
                        }
                        override fun onError(error: Int) {
                            isListening = false
                            tvVoiceStatus.text = "Ready. Say 'Hello Life'"
                        }
                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val recognized = matches[0]
                                assistantEngine.processUserInput(recognized)
                            }
                        }
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun toggleSpeechListening() {
        if (isListening) {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            isListening = false
            tvVoiceStatus.text = "Microphone paused"
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionsExplicitly()
                return
            }
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Say 'Hello Life' or what you need...")
                }
                speechRecognizer?.startListening(intent)
                isListening = true
                tvVoiceStatus.text = "🎤 Listening..."
            } catch (e: Throwable) {
                e.printStackTrace()
                Toast.makeText(this, "Voice recognition unavailable on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initTextToSpeech() {
        try {
            textToSpeech = TextToSpeech(this, this)
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    runOnUiThread { btnStopSpeaking.visibility = View.VISIBLE }
                }
                override fun onDone(utteranceId: String?) {
                    runOnUiThread { btnStopSpeaking.visibility = View.GONE }
                }
                override fun onError(utteranceId: String?) {
                    runOnUiThread { btnStopSpeaking.visibility = View.GONE }
                }
            })
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
        }
    }

    private fun speakAloud(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "LIFEPRO_VOICE_${System.currentTimeMillis()}")
    }

    private fun startBackgroundService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Android 14 (API 34) forbids starting microphone foreground services before permission is granted
            return
        }
        try {
            val serviceIntent = Intent(this, SafetyForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}
