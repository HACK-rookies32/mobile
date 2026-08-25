package com.hack.kidhack.background.geofence

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.hack.kidhack.domain.repository.GeofenceRepository
import com.hack.kidhack.domain.repository.SessionRepository

class GeofenceRegistrar(
    private val context: Context,
    private val sessionRepository: SessionRepository,
    private val geofenceRepository: GeofenceRepository,
) {
    private val client: GeofencingClient = LocationServices.getGeofencingClient(context)

    @SuppressLint("MissingPermission")
    suspend fun sync() {
        val childId = sessionRepository.current()?.user?.id ?: return
        val fences = runCatching { geofenceRepository.list(childId) }.getOrDefault(emptyList())
        runCatching { client.removeGeofences(GeofenceBroadcastReceiver.pendingIntent(context)) }
        if (fences.isEmpty()) return
        val geofences = fences.mapNotNull { item ->
            val lat = item.lat.toDoubleOrNull() ?: return@mapNotNull null
            val lng = item.lng.toDoubleOrNull() ?: return@mapNotNull null
            Geofence.Builder()
                .setRequestId(item.id.ifBlank { item.name })
                .setCircularRegion(lat, lng, item.radius.toFloat().coerceAtLeast(50f))
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT,
                )
                .build()
        }
        if (geofences.isEmpty()) return
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()
        runCatching {
            client.addGeofences(request, GeofenceBroadcastReceiver.pendingIntent(context))
        }
    }

    fun clear() {
        runCatching { client.removeGeofences(GeofenceBroadcastReceiver.pendingIntent(context)) }
    }
}
