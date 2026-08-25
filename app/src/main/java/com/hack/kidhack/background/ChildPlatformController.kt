package com.hack.kidhack.background

import android.content.Context
import com.hack.kidhack.background.capture.ScreenCaptureWorker
import com.hack.kidhack.background.geofence.GeofenceRegistrar
import com.hack.kidhack.background.location.GpsStatusMonitor
import com.hack.kidhack.background.location.LocationBatchWorker
import com.hack.kidhack.background.location.LocationTrackingService
import com.hack.kidhack.background.usage.AppUsageWorker
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.repository.ChildOnboardingStore
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Starts child-role platform services only after login **and** onboarding.
 * Parent sessions never start these workers/services.
 */
class ChildPlatformController(
    private val context: Context,
    private val sessionRepository: SessionRepository,
    private val onboardingStore: ChildOnboardingStore,
    private val gpsStatusMonitor: GpsStatusMonitor,
    private val geofenceRegistrar: GeofenceRegistrar,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running = false

    fun bind() {
        scope.launch {
            combine(sessionRepository.session, onboardingStore.done) { session, done ->
                session?.user?.role == Role.CHILD && done
            }.distinctUntilChanged().collect { active ->
                if (active) start() else stop()
            }
        }
    }

    private fun start() {
        if (running) return
        running = true
        LocationTrackingService.start(context)
        LocationBatchWorker.enqueuePeriodic(context)
        LocationBatchWorker.enqueueNow(context)
        gpsStatusMonitor.start()
        AppUsageWorker.enqueuePeriodic(context)
        ScreenCaptureWorker.enqueuePeriodic(context)
        scope.launch { runCatching { geofenceRegistrar.sync() } }
    }

    private fun stop() {
        if (!running) {
            LocationTrackingService.stop(context)
            return
        }
        running = false
        LocationTrackingService.stop(context)
        LocationBatchWorker.cancel(context)
        gpsStatusMonitor.stop()
        AppUsageWorker.cancel(context)
        ScreenCaptureWorker.cancel(context)
        geofenceRegistrar.clear()
    }
}
