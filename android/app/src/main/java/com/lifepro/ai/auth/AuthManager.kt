package com.lifepro.ai.auth

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.util.UUID

class AuthManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("lifepro_auth_prefs", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var instance: AuthManager? = null

        fun getInstance(context: Context): AuthManager {
            return instance ?: synchronized(this) {
                instance ?: AuthManager(context.applicationContext).also { instance = it }
            }
        }
    }

    var currentUser: UserProfile? = null
        private set

    init {
        loadSession()
    }

    fun isLoggedIn(): Boolean {
        return currentUser != null && currentUser?.role != UserRole.UNSELECTED
    }

    fun isUserRole(): Boolean {
        return currentUser?.role == UserRole.USER
    }

    fun isGuardianRole(): Boolean {
        return currentUser?.role == UserRole.GUARDIAN
    }

    private fun loadSession() {
        val raw = prefs.getString("current_user_json", null) ?: return
        try {
            val json = JSONObject(raw)
            val roleStr = json.optString("role", UserRole.UNSELECTED.name)
            val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.UNSELECTED }
            currentUser = UserProfile(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "User"),
                phone = json.optString("phone", ""),
                email = json.optString("email", ""),
                role = role,
                pairingCode = json.optString("pairingCode", ""),
                connectedGuardianId = json.optString("connectedGuardianId", ""),
                connectedGuardianName = json.optString("connectedGuardianName", ""),
                isLocationSharingEnabled = json.optBoolean("isLocationSharingEnabled", true),
                isCameraAuthorized = json.optBoolean("isCameraAuthorized", true)
            )
        } catch (e: Exception) {
            currentUser = null
        }
    }

    fun saveSession(profile: UserProfile) {
        currentUser = profile
        val json = JSONObject().apply {
            put("id", profile.id)
            put("name", profile.name)
            put("phone", profile.phone)
            put("email", profile.email)
            put("role", profile.role.name)
            put("pairingCode", profile.pairingCode)
            put("connectedGuardianId", profile.connectedGuardianId)
            put("connectedGuardianName", profile.connectedGuardianName)
            put("isLocationSharingEnabled", profile.isLocationSharingEnabled)
            put("isCameraAuthorized", profile.isCameraAuthorized)
        }
        prefs.edit().putString("current_user_json", json.toString()).apply()
    }

    fun setRole(role: UserRole) {
        val current = currentUser ?: UserProfile(
            id = UUID.randomUUID().toString(),
            name = "LifePro Member",
            phone = "+91 98765 43210",
            email = "user@lifepro.ai",
            role = role
        )
        saveSession(current.copy(role = role))
    }

    fun loginWithGoogle(email: String, name: String, role: UserRole): UserProfile {
        val profile = UserProfile(
            id = "google_" + UUID.randomUUID().toString().take(8),
            name = name,
            phone = "",
            email = email,
            role = role,
            pairingCode = "LP-" + (100000..999999).random()
        )
        saveSession(profile)
        return profile
    }

    fun loginWithPhone(phoneNumber: String, name: String, role: UserRole): UserProfile {
        val profile = UserProfile(
            id = "phone_" + UUID.randomUUID().toString().take(8),
            name = name,
            phone = phoneNumber,
            email = "",
            role = role,
            pairingCode = "LP-" + (100000..999999).random()
        )
        saveSession(profile)
        return profile
    }

    fun updateSharingSettings(locationEnabled: Boolean, cameraAuthorized: Boolean) {
        currentUser?.let {
            saveSession(it.copy(
                isLocationSharingEnabled = locationEnabled,
                isCameraAuthorized = cameraAuthorized
            ))
        }
    }

    fun logout() {
        currentUser = null
        prefs.edit().remove("current_user_json").apply()
    }
}
