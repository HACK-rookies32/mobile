package com.hack.kidhack.background.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.repository.AppUsageRepository
import com.hack.kidhack.domain.repository.SessionRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class AppUsageWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val sessionRepository: SessionRepository by inject()
    private val appUsageRepository: AppUsageRepository by inject()

    override suspend fun doWork(): Result {
        val childId = sessionRepository.current()?.user?.id ?: return Result.success()
        val manager = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return Result.success()
        val end = System.currentTimeMillis()
        val start = end - TimeUnit.DAYS.toMillis(1)
        val stats = runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        }.getOrNull()
        if (stats.isNullOrEmpty()) {
            // Permission missing or no data — skip without failing the worker.
            return Result.success()
        }
        val usages = stats
            .filter { it.totalTimeInForeground > 0 }
            .map { stat ->
                AppUsage(
                    appName = stat.packageName.substringAfterLast('.'),
                    durationSec = (stat.totalTimeInForeground / 1000L).toInt(),
                )
            }
        if (usages.isEmpty()) return Result.success()
        return runCatching {
            appUsageRepository.report(childId, usages)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { if (runAttemptCount < 3) Result.retry() else Result.success() },
        )
    }

    companion object {
        const val UNIQUE = "kidhack.app.usage"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<AppUsageWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
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
