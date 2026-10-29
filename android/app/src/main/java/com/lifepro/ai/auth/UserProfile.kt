package com.lifepro.ai.auth

enum class UserRole {
    USER,       // The person receiving assistance (elderly / visually impaired)
    GUARDIAN,   // The family member, doctor, or caregiver
    UNSELECTED
}

data class UserProfile(
    val id: String,
    val name: String,
    val phone: String,
    val email: String,
    val role: UserRole,
    val pairingCode: String = "",
    val connectedGuardianId: String = "",
    val connectedGuardianName: String = "",
    val isLocationSharingEnabled: Boolean = true,
    val isCameraAuthorized: Boolean = true
)
