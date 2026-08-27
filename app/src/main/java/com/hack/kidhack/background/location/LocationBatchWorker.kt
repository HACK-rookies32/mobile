package com.hack.kidhack.background.location

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hack.kidhack.background.geofence.GeofenceRegistrar
import com.hack.kidhack.domain.repository.LocationRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class LocationBatchWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val queue: LocationQueue by inject()
    private val locationRepository: LocationRepository by inject()
    private val geofenceRegistrar: GeofenceRegistrar by inject()

    override suspend fun doWork(): Result {
        // 안전 구역을 다시 등록한다. 자녀 앱이 시작할 때 한 번만 등록하면, 그 뒤에
        // 보호자가 추가한 구역은 자녀 기기에 영영 걸리지 않아 이탈이 감지되지 않는다.
        // 같은 구역을 다시 넣는 건 무해하므로 위치 배치를 올릴 때마다 맞춰 준다.
        runCatching { geofenceRegistrar.sync() }

        val batch = queue.drain()
        if (batch.isEmpty()) return Result.success()
        return runCatching {
            locationRepository.uploadBatch(batch)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = {
                queue.restore(batch)
                if (runAttemptCount < 3) Result.retry() else Result.success()
            },
        )
    }

    companion object {
        const val UNIQUE_PERIODIC = "kidhack.location.batch"
        const val UNIQUE_NOW = "kidhack.location.batch.now"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<LocationBatchWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<LocationBatchWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NOW)
        }
    }
}
