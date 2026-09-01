package com.hack.kidhack.background.usage

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
import com.hack.kidhack.data.local.AppUsageReportStore
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.repository.AppUsageRepository
import com.hack.kidhack.domain.repository.SessionRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class AppUsageWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val sessionRepository: SessionRepository by inject()
    private val appUsageRepository: AppUsageRepository by inject()
    private val usageCollector: AppUsageCollector by inject()
    private val screenTimeWatcher: ScreenTimeWatcher by inject()
    private val reportStore: AppUsageReportStore by inject()

    /**
     * 서버는 보고를 행으로 쌓고 조회 시 합산한다. 그래서 **증가분만** 보내야 한다.
     *
     * 예전에는 매 실행마다 「최근 24시간 누적」을 그대로 보냈다. 1시간 주기로 돌면
     * 30분·45분·60분… 이 전부 더해져 실제보다 몇 배 큰 스크린타임이 찍혔다.
     * 앱 이름도 패키지명 꼬리(`api`, `settings`)를 쓰고 있어 사람이 읽을 수 없었다 —
     * [AppUsageCollector] 와 같은 기준(런처에 뜨는 앱 + 표시 이름)으로 맞춘다.
     */
    override suspend fun doWork(): Result {
        val childId = sessionRepository.current()?.user?.id ?: return Result.success()

        // 자녀가 앱을 열지 않아도 한도 도달은 기록돼야 한다.
        // 바로 아래에서 사용량을 보고하므로 여기서는 즉시 보고를 다시 예약하지 않는다.
        val today = usageCollector.todayUsage(limit = Int.MAX_VALUE)
        screenTimeWatcher.check(today.totalMinutes, reportToServer = false)

        // 기기에서 실측하지 못했으면(권한 없음) 보고할 게 없다.
        // 서버 대체값을 되돌려 보내면 같은 값이 무한히 누적된다.
        if (!today.fromDevice || today.usages.isEmpty()) return Result.success()

        val date = LocalDate.now().toString()
        // 기준점은 패키지명으로 잡는다. 앱 이름은 기기 언어를 바꾸거나 앱이 이름을 바꾸면
        // 달라져서, 이름으로 잡으면 그때 증가분이 처음부터 다시 계산돼 하루치가 두 번 쌓인다.
        val byPackage = today.usages.associateBy { it.packageName ?: it.appName }
        val current = byPackage.mapValues { (_, usage) -> usage.durationSec }
        val previous = reportStore.lastTotals(date)

        // 증가분만. OS 통계가 되감기는 드문 경우(앱 삭제·초기화)에는 0 으로 눌러 음수를 막는다.
        val deltas = byPackage.mapNotNull { (key, usage) ->
            val delta = usage.durationSec - (previous[key] ?: 0)
            if (delta > 0) usage.copy(durationSec = delta) else null
        }
        if (deltas.isEmpty()) return Result.success()

        return runCatching {
            appUsageRepository.report(childId, deltas)
        }.fold(
            onSuccess = {
                // 보고에 성공한 뒤에만 기준점을 옮긴다. 실패한 값을 저장하면 그만큼 영영 누락된다.
                reportStore.save(date, current)
                Result.success()
            },
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
