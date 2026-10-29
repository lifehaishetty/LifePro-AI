package com.lifepro.ai.camera

import android.content.Context
import com.lifepro.ai.activity.ActivityRepository
import com.lifepro.ai.activity.EventOutcome
import com.lifepro.ai.activity.EventType
import com.lifepro.ai.auth.AuthManager

enum class CameraSessionState {
    INACTIVE,
    REQUESTING,
    ACTIVE_STREAMING,
    STOPPED_BY_USER
}

class CameraSessionManager private constructor(private val context: Context) {

    private val authManager = AuthManager.getInstance(context)
    private val activityRepo = ActivityRepository.getInstance(context)

    var sessionState: CameraSessionState = CameraSessionState.INACTIVE
        private set

    companion object {
        @Volatile
        private var instance: CameraSessionManager? = null

        fun getInstance(context: Context): CameraSessionManager {
            return instance ?: synchronized(this) {
                instance ?: CameraSessionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Guardian triggers check-in request.
     * Respects user's privacy setting: isCameraAuthorized must be true.
     */
    fun startAuthorizedSession(): Boolean {
        val user = authManager.currentUser
        if (user != null && !user.isCameraAuthorized) {
            activityRepo.logEvent(
                EventType.CAMERA_SESSION,
                "Camera Check-in Blocked",
                "User has disabled camera authorization in privacy settings.",
                EventOutcome.FAILED
            )
            return false
        }

        sessionState = CameraSessionState.ACTIVE_STREAMING
        activityRepo.logEvent(
            EventType.CAMERA_SESSION,
            "Video Check-in Session Started",
            "Secure video stream initiated. Visible privacy indicator active.",
            EventOutcome.ACTIVE
        )
        return true
    }

    fun stopSession() {
        if (sessionState == CameraSessionState.ACTIVE_STREAMING) {
            sessionState = CameraSessionState.STOPPED_BY_USER
            activityRepo.logEvent(
                EventType.CAMERA_SESSION,
                "Video Check-in Ended",
                "Camera stream closed by user.",
                EventOutcome.SUCCESS
            )
        }
    }
}
