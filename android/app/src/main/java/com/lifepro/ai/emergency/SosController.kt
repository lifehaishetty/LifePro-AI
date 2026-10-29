package com.lifepro.ai.emergency

import android.Manifest
import android.content.Context
import android.content.Intent
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

    private var sosTimer: CountDownTimer? = null
    private val SOS_DURATION_MS = 10000L // 10 seconds
    private val activityRepo = ActivityRepository.getInstance(context)
    private val contactManager = ContactManager.getInstance(context)

    var isCountdownActive = false
        private set
    var currentTarget = "112"
        private set

    fun start10SecondSos(targetNumber: String = "112", callback: SosCallback? = null) {
        if (isCountdownActive) return // Prevent duplicate countdowns

        currentTarget = targetNumber
        isCountdownActive = true

        activityRepo.logEvent(
            EventType.SOS_TRIGGERED,
            "10s Emergency SOS Activated",
            "Target: $targetNumber. 10-second false alarm countdown initiated.",
            EventOutcome.ACTIVE
        )

        speakCallback("Emergency SOS activated. Calling emergency helpline in ten seconds. Say cancel to abort.")

        sosTimer?.cancel()
        sosTimer = object : CountDownTimer(SOS_DURATION_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000) + 1
                callback?.onCountdownTick(seconds, currentTarget)
                if (seconds == 5L) {
                    speakCallback("Five seconds remaining.")
                }
            }

            override fun onFinish() {
                isCountdownActive = false
                callback?.onCountdownFinished(currentTarget)
                speakCallback("Connecting emergency call to $currentTarget now.")
                executeCall(currentTarget)
                activityRepo.logEvent(
                    EventType.CALL_ATTEMPT,
                    "Emergency Call Dispatched",
                    "Dispatched call to $currentTarget after 10s countdown completed.",
                    EventOutcome.SUCCESS
                )
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
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val intent = if (hasCallPermission) {
            Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Sequential fallback: try next contact if primary fails
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
