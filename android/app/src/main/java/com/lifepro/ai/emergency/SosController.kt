package com.lifepro.ai.emergency

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.CountDownTimer
import androidx.core.content.ContextCompat
import com.lifepro.ai.activity.ActivityRepository
import com.lifepro.ai.activity.EventOutcome
import com.lifepro.ai.activity.EventType
import com.lifepro.ai.contacts.ContactManager

interface SosCallback {
    fun onCountdownTick(secondsRemaining: Long, target: String)
    fun onCountdownFinished(target: String)
    fun onSosCancelled()
}

class SosController(
    private val context: Context,
    private val speakCallback: (String) -> Unit
) {

    private val prefs: SharedPreferences = context.getSharedPreferences("lifepro_sos_prefs", Context.MODE_PRIVATE)
    private var sosTimer: CountDownTimer? = null
    private val activityRepo = ActivityRepository.getInstance(context)
    private val contactManager = ContactManager.getInstance(context)

    var isCountdownActive = false
        private set
    var currentTarget = "112"
        private set

    // Configurable countdown duration (default 10s, adjustable to 5, 10, 15, 20, 30s)
    var countdownDurationSeconds: Int
        get() = prefs.getInt("countdown_duration_seconds", 10)
        set(value) {
            prefs.edit().putInt("countdown_duration_seconds", value.coerceIn(5, 60)).apply()
        }

    // Controlled simulation / test mode toggle
    var isSimulationMode: Boolean
        get() = prefs.getBoolean("is_simulation_mode", false)
        set(value) {
            prefs.edit().putBoolean("is_simulation_mode", value).apply()
        }

    // Configurable emergency helpline (default 112, can be customized e.g. 911, 999)
    var emergencyHelplineNumber: String
        get() = prefs.getString("emergency_helpline_number", "112") ?: "112"
        set(value) {
            prefs.edit().putString("emergency_helpline_number", value.trim()).apply()
        }

    // Duplicate-event debouncing protection
    private var lastTriggerTimestamp = 0L
    private val DEBOUNCE_COOLDOWN_MS = 4000L

    /**
     * Backward-compatible alias for existing callers.
     */
    fun start10SecondSos(targetNumber: String = emergencyHelplineNumber, callback: SosCallback? = null) {
        startSosCountdown(targetNumber, callback)
    }

    /**
     * Initiates the emergency SOS countdown with configurable duration and duplicate protection.
     */
    fun startSosCountdown(targetNumber: String = emergencyHelplineNumber, callback: SosCallback? = null) {
        val now = System.currentTimeMillis()
        if (isCountdownActive) return // Already running

        if (now - lastTriggerTimestamp < DEBOUNCE_COOLDOWN_MS) {
            // Duplicate event within cooldown window suppressed
            return
        }
        lastTriggerTimestamp = now

        currentTarget = if (targetNumber.isNotEmpty()) targetNumber else emergencyHelplineNumber
        isCountdownActive = true

        val totalDurationMs = countdownDurationSeconds * 1000L

        activityRepo.logEvent(
            EventType.SOS_TRIGGERED,
            if (isSimulationMode) "Simulation SOS Activated" else "${countdownDurationSeconds}s Emergency SOS Activated",
            if (isSimulationMode) "Controlled test mode active. No live emergency call will be placed."
            else "Target: $currentTarget. False alarm buffer: ${countdownDurationSeconds}s.",
            EventOutcome.ACTIVE
        )

        val spokenWarning = if (isSimulationMode) {
            "Simulation mode activated. Testing emergency sequence for $currentTarget in $countdownDurationSeconds seconds. Say cancel to abort."
        } else {
            "Emergency SOS activated. Calling emergency helpline in $countdownDurationSeconds seconds. Say cancel to abort."
        }
        speakCallback(spokenWarning)

        sosTimer?.cancel()
        sosTimer = object : CountDownTimer(totalDurationMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000) + 1
                callback?.onCountdownTick(seconds, currentTarget)
                if (seconds == 5L && countdownDurationSeconds > 5) {
                    speakCallback("Five seconds remaining.")
                }
            }

            override fun onFinish() {
                isCountdownActive = false
                callback?.onCountdownFinished(currentTarget)

                if (isSimulationMode) {
                    speakCallback("Simulation mode: Emergency countdown completed successfully. No actual phone call was placed.")
                    activityRepo.logEvent(
                        EventType.CALL_ATTEMPT,
                        "Simulation Test Completed",
                        "Simulated dispatch for $currentTarget completed safely without dialing.",
                        EventOutcome.SIMULATION
                    )
                } else {
                    speakCallback("Connecting emergency call to $currentTarget now.")
                    executeCall(currentTarget)
                    activityRepo.logEvent(
                        EventType.CALL_ATTEMPT,
                        "Emergency Call Dispatched",
                        "Dispatched call to $currentTarget after ${countdownDurationSeconds}s countdown completed.",
                        EventOutcome.DISPATCHED
                    )
                }
            }
        }.start()
    }

    fun cancelSos(callback: SosCallback? = null) {
        if (!isCountdownActive) return
        sosTimer?.cancel()
        sosTimer = null
        isCountdownActive = false

        activityRepo.logEvent(
            EventType.SOS_CANCELLED,
            "SOS Cancelled by User",
            "Emergency countdown cancelled safely. False alarm prevented.",
            EventOutcome.CANCELLED
        )

        speakCallback("Emergency SOS cancelled. You are safe.")
        callback?.onSosCancelled()
    }

    fun executeCall(number: String) {
        if (isSimulationMode) {
            speakCallback("Simulation mode: Call to $number simulated successfully.")
            return
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val cleanNumber = number.replace(" ", "").replace("-", "")

        val intent = if (hasCallPermission) {
            Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // If primary action fails (e.g. SecurityException or dialer issue), try fallback
            trySequentialFallback()
        }
    }

    private fun trySequentialFallback() {
        val fallbackQueue = contactManager.getEmergencyFallbackQueue().filter { it.phoneNumber != currentTarget }
        val nextContact = fallbackQueue.firstOrNull()
        if (nextContact != null) {
            speakCallback("Calling backup emergency contact ${nextContact.name}.")
            executeCall(nextContact.phoneNumber)
        }
    }
}
