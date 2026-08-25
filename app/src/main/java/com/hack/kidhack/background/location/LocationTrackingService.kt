package com.hack.kidhack.background.location

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.hack.kidhack.R
import com.hack.kidhack.background.ChildNotifications
import com.hack.kidhack.core.permission.KidhackPermissions
import com.hack.kidhack.domain.model.LocationPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.time.Instant

class LocationTrackingService : Service() {

    private val queue: LocationQueue by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            scope.launch {
                queue.enqueue(
                    LocationPoint(
                        lat = loc.latitude.toString(),
                        lng = loc.longitude.toString(),
                        recordedAt = Instant.now().toString(),
                    ),
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ChildNotifications.ensureChannel(this)
        ServiceCompat.startForeground(
            this,
            ChildNotifications.LOCATION_ID,
            ChildNotifications.foreground(
                this,
                getString(R.string.child_location_notif_title),
                getString(R.string.child_location_notif_text),
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        startUpdates()
        scope.launch {
            while (isActive) {
                delay(FIVE_MINUTES_MS)
                LocationBatchWorker.enqueueNow(this@LocationTrackingService)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { client.removeLocationUpdates(callback) }
        scope.cancel()
        super.onDestroy()
    }

    private fun startUpdates() {
        val granted = KidhackPermissions.location.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!granted) {
            stopSelf()
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000L)
            .setMinUpdateIntervalMillis(30_000L)
            .build()
        runCatching {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        }
    }

    companion object {
        private const val FIVE_MINUTES_MS = 5 * 60 * 1000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LocationTrackingService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }
}
