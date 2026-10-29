package com.lifepro.ai.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat

enum class LocationStatus {
    LIVE_GPS,
    STALE_CACHED,
    NO_GPS,
    PERMISSION_DENIED
}

data class LocationReport(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val status: LocationStatus
) {
    fun getGoogleMapsUrl(): String {
        return "https://maps.google.com/?q=$latitude,$longitude"
    }

    fun getReadableCoordinates(): String {
        return if (status == LocationStatus.LIVE_GPS || status == LocationStatus.STALE_CACHED) {
            "${"%.4f".format(latitude)}° N, ${"%.4f".format(longitude)}° E"
        } else {
            "Coordinates Unavailable"
        }
    }
}

class LocationService private constructor(private val context: Context) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    var lastReport: LocationReport = LocationReport(0.0, 0.0, 0f, 0L, LocationStatus.NO_GPS)
        private set

    companion object {
        @Volatile
        private var instance: LocationService? = null

        fun getInstance(context: Context): LocationService {
            return instance ?: synchronized(this) {
                instance ?: LocationService(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        refreshLocation()
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun refreshLocation(): LocationReport {
        if (!hasPermission()) {
            lastReport = LocationReport(0.0, 0.0, 0f, 0L, LocationStatus.PERMISSION_DENIED)
            return lastReport
        }

        var loc: Location? = null
        try {
            loc = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            // Register for passive or active updates if available
            locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000L, 10f, this)
        } catch (e: Exception) {
            // Ignored
        }

        lastReport = if (loc != null) {
            val isRecent = (System.currentTimeMillis() - loc.time) < 120_000 // 2 minutes
            LocationReport(
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracy = loc.accuracy,
                timestamp = loc.time,
                status = if (isRecent) LocationStatus.LIVE_GPS else LocationStatus.STALE_CACHED
            )
        } else {
            LocationReport(0.0, 0.0, 0f, 0L, LocationStatus.NO_GPS)
        }
        return lastReport
    }

    fun createWhatsAppShareIntent(recipientName: String = "Guardian"): Intent {
        val report = refreshLocation()
        val mapsUrl = report.getGoogleMapsUrl()
        val message = "🚨 Emergency / Safety Location Update from LifePro-AI for $recipientName:\nLive GPS Coordinates: ${report.getReadableCoordinates()}\nOpen on Google Maps: $mapsUrl"

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createGoogleMapsViewIntent(): Intent {
        val report = refreshLocation()
        val uri = if (report.status == LocationStatus.LIVE_GPS || report.status == LocationStatus.STALE_CACHED) {
            Uri.parse("geo:${report.latitude},${report.longitude}?q=${report.latitude},${report.longitude}(User+Location)")
        } else {
            Uri.parse("https://maps.google.com")
        }
        return Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    override fun onLocationChanged(location: Location) {
        lastReport = LocationReport(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            timestamp = location.time,
            status = LocationStatus.LIVE_GPS
        )
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
