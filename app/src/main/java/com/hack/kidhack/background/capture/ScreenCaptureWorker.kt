package com.hack.kidhack.background.capture

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import java.util.concurrent.TimeUnit

/**
 * Hourly screenshot hook (REQ-M-12). MediaProjection tokens cannot be
 * persisted, so this worker no-ops until a live grant exists.
 */
class ScreenCaptureWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    override suspend fun doWork(): Result {
        ParentCaptureRequestListener.onRemoteRequest()
        return Result.success()
    }

    companion object {
        const val UNIQUE = "kidhack.screen.capture"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScreenCaptureWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
    }
}
