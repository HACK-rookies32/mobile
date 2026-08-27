package com.hack.kidhack.background.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import com.hack.kidhack.background.ChildAlertUploadWorker
import com.hack.kidhack.data.local.ChildAlertStore
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
    private val alertStore: ChildAlertStore,
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

    private companion object {
        /** 설정 화면에서 토글을 오가도 30분에 한 번만 남긴다. */
        const val GPS_ALERT_DEDUPE_MS = 30 * 60 * 1000L
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
            if (!enabled) {
                // 자녀 홈 「최근 알림」에도 남기고, 보호자가 곧 받아 보도록 바로 올린다.
                alertStore.record(
                    type = ChildAlertStore.TYPE_GPS_OFF,
                    title = "GPS가 꺼졌어요",
                    body = "위치 전송이 멈췄어요. 위치 서비스를 다시 켜 주세요.",
                    dedupeWindowMs = GPS_ALERT_DEDUPE_MS,
                )
                ChildAlertUploadWorker.enqueueNow(context)
            }
        }
    }
}
