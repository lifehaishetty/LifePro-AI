package com.lifepro.ai.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.lifepro.ai.activity.ActivityRepository
import com.lifepro.ai.activity.EventOutcome
import com.lifepro.ai.activity.EventType
import com.lifepro.ai.contacts.ContactManager
import com.lifepro.ai.location.LocationService
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

enum class AssistantState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING
}

enum class ActionType {
    NONE,
    SOS,
    CALL,
    LOCATION_SHARE,
    LOCATION_QUERY,
    CAMERA_CHECK,
    CANCEL
}

data class AssistantResponse(
    val replyText: String,
    val actionType: ActionType,
    val target: String = "",
    val isEmergency: Boolean = false
)

interface AssistantListener {
    fun onStateChanged(state: AssistantState)
    fun onResponseReady(response: AssistantResponse)
    fun onError(errorMessage: String)
}

class AssistantEngine(
    private val context: Context,
    private val listener: AssistantListener
) {

    private val httpClient = OkHttpClient()
    private val contactManager = ContactManager.getInstance(context)
    private val locationService = LocationService.getInstance(context)
    private val activityRepo = ActivityRepository.getInstance(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val VERCEL_URL = "https://life-pro-ai.vercel.app/api/chat"
    private var pendingContext: String? = null // For follow-up questions

    /**
     * Main intent handling function.
     * Evaluates speech input locally first for zero-latency emergency detection,
     * then consults the cloud AI server.
     */
    fun processUserInput(userInput: String) {
        val clean = userInput.trim()
        if (clean.isEmpty()) return

        listener.onStateChanged(AssistantState.PROCESSING)
        val lower = clean.lowercase()

        // 1. CANCELLATION
        if (lower.contains("cancel") || lower.contains("false alarm") || lower.contains("stop")) {
            pendingContext = null
            deliverResponse(AssistantResponse(
                replyText = "Emergency cancelled. You are completely safe.",
                actionType = ActionType.CANCEL
            ))
            return
        }

        // 2. CONTEXTUAL FOLLOW-UP RESPONSE
        if (pendingContext == "FALL_DETECTED") {
            pendingContext = null
            if (lower.contains("yes") || lower.contains("help") || lower.contains("call") || lower.contains("hurt")) {
                deliverResponse(AssistantResponse(
                    replyText = "Emergency confirmed. Initiating 10-second countdown for 112 dispatch now.",
                    actionType = ActionType.SOS,
                    target = "112",
                    isEmergency = true
                ))
                return
            } else {
                deliverResponse(AssistantResponse(
                    replyText = "Glad you are okay. I am staying active in the background.",
                    actionType = ActionType.NONE
                ))
                return
            }
        }

        // 3. FALL / INJURY REPORT (Ask clarifying safety question per specs)
        if (lower.contains("fell down") || lower.contains("i fell") || lower.contains("i slipped") || lower.contains("hurt")) {
            pendingContext = "FALL_DETECTED"
            deliverResponse(AssistantResponse(
                replyText = "I heard you fell. Are you hurt? Do you need me to alert your guardian or call 112?",
                actionType = ActionType.NONE
            ))
            return
        }

        // 4. INSTANT EMERGENCY SHOUTS
        if (lower.contains("help") || lower.contains("ambulance") || lower.contains("save me") || lower.contains("danger") || lower.contains("police")) {
            deliverResponse(AssistantResponse(
                replyText = "Emergency distress detected! Starting 10-second countdown for Indian 112 SOS.",
                actionType = ActionType.SOS,
                target = "112",
                isEmergency = true
            ))
            return
        }

        // 5. CALLING COMMANDS (Natural Language Contact Resolution)
        if (lower.startsWith("call") || lower.contains("dial") || lower.contains("phone")) {
            val matchedContact = contactManager.resolveContactFromSpeech(clean)
            if (matchedContact != null) {
                deliverResponse(AssistantResponse(
                    replyText = "Calling your ${matchedContact.relationship}, ${matchedContact.name}.",
                    actionType = ActionType.CALL,
                    target = matchedContact.phoneNumber
                ))
                return
            } else {
                // Ambiguous call (e.g., "call that guy" or unknown person)
                deliverResponse(AssistantResponse(
                    replyText = "Which contact would you like to call? Your favorites are Ananya, Arjun, or 112 Emergency.",
                    actionType = ActionType.NONE
                ))
                return
            }
        }

        // 6. LOCATION SHARING COMMANDS
        if (lower.contains("location") || lower.contains("where am i") || lower.contains("gps")) {
            val report = locationService.refreshLocation()
            if (lower.contains("whatsapp") || lower.contains("send") || lower.contains("share")) {
                deliverResponse(AssistantResponse(
                    replyText = "Opening WhatsApp with your live Google Maps location coordinates.",
                    actionType = ActionType.LOCATION_SHARE,
                    target = report.getGoogleMapsUrl()
                ))
            } else {
                deliverResponse(AssistantResponse(
                    replyText = "Your current live coordinates are ${report.getReadableCoordinates()}.",
                    actionType = ActionType.LOCATION_QUERY,
                    target = report.getGoogleMapsUrl()
                ))
            }
            return
        }

        // 7. CAMERA / FACETIME CHECK-IN
        if (lower.contains("camera") || lower.contains("facetime") || lower.contains("video check")) {
            deliverResponse(AssistantResponse(
                replyText = "Opening authorized guardian video check-in view.",
                actionType = ActionType.CAMERA_CHECK
            ))
            return
        }

        // 8. CLOUD AI INFERENCE (Nebius Nemotron on Vercel)
        queryCloudAi(clean)
    }

    private fun queryCloudAi(message: String) {
        val jsonPayload = JSONObject().apply {
            put("message", message)
            put("location", locationService.refreshLocation().getReadableCoordinates())
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(VERCEL_URL).post(requestBody).build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    // Graceful offline conversational reply
                    deliverResponse(AssistantResponse(
                        replyText = "I'm right here with you. Say 'Call my daughter', 'Where am I', or shout 'Help' anytime.",
                        actionType = ActionType.NONE
                    ))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                mainHandler.post {
                    try {
                        val json = JSONObject(body)
                        val reply = json.optString("reply", "I am right here with you.")
                        val actionStr = json.optString("action", "none")
                        val contact = json.optString("contact", "")

                        val action = when (actionStr.lowercase()) {
                            "sos" -> ActionType.SOS
                            "call" -> ActionType.CALL
                            "camera" -> ActionType.CAMERA_CHECK
                            "location" -> ActionType.LOCATION_QUERY
                            else -> ActionType.NONE
                        }

                        deliverResponse(AssistantResponse(
                            replyText = reply,
                            actionType = action,
                            target = if (contact.isNotEmpty()) contact else "112"
                        ))
                    } catch (e: Exception) {
                        deliverResponse(AssistantResponse(
                            replyText = "Ready to assist. Say 'Hello Life' or shout 'Help' for 112 SOS.",
                            actionType = ActionType.NONE
                        ))
                    }
                }
            }
        })
    }

    private fun deliverResponse(response: AssistantResponse) {
        mainHandler.post {
            listener.onStateChanged(AssistantState.SPEAKING)
            listener.onResponseReady(response)
        }
    }
}
