package com.hack.kidhack.background.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import com.hack.kidhack.domain.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Observes GPS provider enabled state and reports `POST /devices/gps-status`.
 *
 * A normal app cannot force GPS on (GAP-12 / REQ-M-10). This monitor never
 * writes secure settings, never uses DeviceAdmin, and only reports the OS
 * provider flag so the server can notify the parent.
 */
class GpsStatusMonitor(
    private val context: Context,
    private val locationRepository: LocationRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastEnabled: Boolean? = null
    private var receiver: BroadcastReceiver? = null

    fun start() {
        if (receiver != null) return
        report(currentEnabled())
        val filter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        val next = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                report(currentEnabled())
            }
        }
        receiver = next
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(next, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(next, filter)
        }
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        lastEnabled = null
    }

    private fun currentEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun report(enabled: Boolean) {
        if (lastEnabled == enabled) return
        lastEnabled = enabled
        scope.launch {
            runCatching {
                locationRepository.reportGpsStatus(enabled, Instant.now().toString())
            }
        }
    }
}
