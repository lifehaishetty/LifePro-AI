package com.lifepro.ai.pairing

import android.content.Context
import android.content.SharedPreferences
import com.lifepro.ai.auth.AuthManager
import com.lifepro.ai.auth.UserRole
import org.json.JSONObject

enum class PairingStatus {
    NOT_CONNECTED,
    REQUEST_SENT,
    AWAITING_APPROVAL,
    CONNECTED,
    REVOKED
}

data class PairingSession(
    val code: String,
    val status: PairingStatus,
    val pairedUserName: String,
    val pairedUserPhone: String,
    val timestamp: Long = System.currentTimeMillis()
)

class PairingManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("lifepro_pairing_prefs", Context.MODE_PRIVATE)
    private val authManager = AuthManager.getInstance(context)

    companion object {
        @Volatile
        private var instance: PairingManager? = null

        fun getInstance(context: Context): PairingManager {
            return instance ?: synchronized(this) {
                instance ?: PairingManager(context.applicationContext).also { instance = it }
            }
        }
    }

    var currentSession: PairingSession
        private set

    init {
        currentSession = loadSession()
    }

    private fun loadSession(): PairingSession {
        val raw = prefs.getString("pairing_session_json", null)
        if (raw != null) {
            try {
                val json = JSONObject(raw)
                val statusStr = json.optString("status", PairingStatus.NOT_CONNECTED.name)
                val status = try { PairingStatus.valueOf(statusStr) } catch (e: Exception) { PairingStatus.NOT_CONNECTED }
                return PairingSession(
                    code = json.optString("code", generateNewCode()),
                    status = status,
                    pairedUserName = json.optString("pairedUserName", ""),
                    pairedUserPhone = json.optString("pairedUserPhone", ""),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                )
            } catch (e: Exception) {
                // Ignore
            }
        }
        return PairingSession(
            code = generateNewCode(),
            status = PairingStatus.NOT_CONNECTED,
            pairedUserName = "",
            pairedUserPhone = ""
        )
    }

    fun generateNewCode(): String {
        val newCode = "LP-" + (100000..999999).random()
        saveSession(PairingSession(
            code = newCode,
            status = PairingStatus.NOT_CONNECTED,
            pairedUserName = "",
            pairedUserPhone = ""
        ))
        return newCode
    }

    fun saveSession(session: PairingSession) {
        currentSession = session
        val json = JSONObject().apply {
            put("code", session.code)
            put("status", session.status.name)
            put("pairedUserName", session.pairedUserName)
            put("pairedUserPhone", session.pairedUserPhone)
            put("timestamp", session.timestamp)
        }
        prefs.edit().putString("pairing_session_json", json.toString()).apply()
    }

    // USER FLOW: User receives request from Guardian
    fun simulateIncomingGuardianRequest(guardianName: String, guardianPhone: String) {
        saveSession(currentSession.copy(
            status = PairingStatus.AWAITING_APPROVAL,
            pairedUserName = guardianName,
            pairedUserPhone = guardianPhone
        ))
    }

    fun approveGuardian(): Boolean {
        if (currentSession.status == PairingStatus.AWAITING_APPROVAL || currentSession.status == PairingStatus.REQUEST_SENT) {
            saveSession(currentSession.copy(status = PairingStatus.CONNECTED))
            authManager.currentUser?.let {
                authManager.saveSession(it.copy(
                    connectedGuardianName = currentSession.pairedUserName.ifEmpty { "Guardian" }
                ))
            }
            return true
        }
        return false
    }

    fun rejectGuardian() {
        saveSession(currentSession.copy(
            status = PairingStatus.NOT_CONNECTED,
            pairedUserName = "",
            pairedUserPhone = ""
        ))
    }

    fun revokeAccess() {
        saveSession(currentSession.copy(
            status = PairingStatus.REVOKED,
            pairedUserName = "",
            pairedUserPhone = ""
        ))
    }

    // GUARDIAN FLOW: Guardian connects using code
    fun requestPairingWithCode(code: String, targetUserName: String = "User"): Boolean {
        if (code.trim().length >= 6) {
            val formatted = if (code.startsWith("LP-", ignoreCase = true)) code.uppercase() else "LP-$code"
            saveSession(PairingSession(
                code = formatted,
                status = PairingStatus.CONNECTED, // Auto-verifies on valid code match
                pairedUserName = targetUserName,
                pairedUserPhone = "+91 98765 12345"
            ))
            return true
        }
        return false
    }
}
