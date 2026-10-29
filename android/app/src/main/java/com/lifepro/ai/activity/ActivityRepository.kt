package com.lifepro.ai.activity

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class EventType {
    SOS_TRIGGERED,
    SOS_CANCELLED,
    CALL_ATTEMPT,
    LOCATION_SHARED,
    PAIRING_EVENT,
    CAMERA_SESSION,
    MONITORING_TOGGLED
}

enum class EventOutcome {
    SUCCESS,
    CANCELLED,
    FAILED,
    ACTIVE,
    PENDING,
    DISPATCHED,
    SIMULATION
}

data class SafetyEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: EventType,
    val title: String,
    val details: String,
    val outcome: EventOutcome
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("h:mm a, d MMM", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

class ActivityRepository private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("lifepro_activity_prefs", Context.MODE_PRIVATE)
    private val events = mutableListOf<SafetyEvent>()

    companion object {
        @Volatile
        private var instance: ActivityRepository? = null

        fun getInstance(context: Context): ActivityRepository {
            return instance ?: synchronized(this) {
                instance ?: ActivityRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        loadEvents()
    }

    fun getAllEvents(): List<SafetyEvent> {
        return events.sortedByDescending { it.timestamp }
    }

    fun searchEvents(query: String, filterType: EventType? = null): List<SafetyEvent> {
        val q = query.trim().lowercase()
        return events.filter { event ->
            val matchesFilter = filterType == null || event.type == filterType
            val matchesQuery = q.isEmpty() ||
                    event.title.lowercase().contains(q) ||
                    event.details.lowercase().contains(q) ||
                    event.type.name.lowercase().contains(q)
            matchesFilter && matchesQuery
        }.sortedByDescending { it.timestamp }
    }

    fun logEvent(type: EventType, title: String, details: String, outcome: EventOutcome) {
        val event = SafetyEvent(
            type = type,
            title = title,
            details = details,
            outcome = outcome
        )
        events.add(0, event)
        // Keep last 100 events
        if (events.size > 100) events.removeAt(events.size - 1)
        saveEvents()
    }

    private fun loadEvents() {
        events.clear()
        val raw = prefs.getString("events_json_array", null)
        if (raw != null) {
            try {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    events.add(
                        SafetyEvent(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            type = EventType.valueOf(obj.getString("type")),
                            title = obj.getString("title"),
                            details = obj.optString("details", ""),
                            outcome = EventOutcome.valueOf(obj.optString("outcome", EventOutcome.SUCCESS.name))
                        )
                    )
                }
            } catch (e: Exception) {
                seedInitialEvents()
            }
        } else {
            seedInitialEvents()
        }
    }

    private fun seedInitialEvents() {
        logEvent(
            EventType.MONITORING_TOGGLED,
            "Protection Initialized",
            "Screen-off voice guardian activated on Android device",
            EventOutcome.SUCCESS
        )
        logEvent(
            EventType.PAIRING_EVENT,
            "Pairing Code Generated",
            "New secure invite code ready for trusted family guardian",
            EventOutcome.SUCCESS
        )
    }

    private fun saveEvents() {
        val array = JSONArray()
        for (e in events) {
            val obj = JSONObject().apply {
                put("id", e.id)
                put("timestamp", e.timestamp)
                put("type", e.type.name)
                put("title", e.title)
                put("details", e.details)
                put("outcome", e.outcome.name)
            }
            array.put(obj)
        }
        prefs.edit().putString("events_json_array", array.toString()).apply()
    }
}
