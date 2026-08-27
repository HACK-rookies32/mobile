package com.hack.kidhack.background.usage

import android.app.usage.UsageStatsManager
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
    private val usageCollector: AppUsageCollector by inject()
    private val screenTimeWatcher: ScreenTimeWatcher by inject()

    override suspend fun doWork(): Result {
        val childId = sessionRepository.current()?.user?.id ?: return Result.success()

        // 자녀가 앱을 열지 않아도 한도 도달은 기록돼야 한다.
        // 바로 아래에서 사용량을 보고하므로 여기서는 즉시 보고를 다시 예약하지 않는다.
        screenTimeWatcher.check(usageCollector.todayUsage().totalMinutes, reportToServer = false)

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
        private const val UNIQUE_NOW = "kidhack.app.usage.now"

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

        /**
         * 다음 정시(1시간 주기)를 기다리지 않고 지금 보고한다.
         *
         * 스크린타임 한도 도달처럼 보호자가 바로 알아야 하는 순간에 쓴다.
         * 서버가 사용량을 받아야 「사용량 한도」 알림을 만들 수 있다.
         */
        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<AppUsageWorker>()
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
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NOW)
        }
    }
}
