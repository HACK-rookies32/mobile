package com.hack.kidhack.background.capture

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hack.kidhack.domain.model.RemoteCommand
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.background.command.ChildCommandNotifier
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * 1시간 주기 정기 캡처 (REQ-M-12).
 *
 * MediaProjection 토큰은 저장할 수 없고 안드로이드가 매번 사용자 동의를 요구한다.
 * 그래서 워커는 직접 캡처하지 않고 **동의를 유도하는 알림**만 띄운다.
 * 자녀가 알림을 누르면 `trigger=SCHEDULED` 로 캡처가 진행된다.
 */
class ScreenCaptureWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val notifier: ChildCommandNotifier by inject()

    override suspend fun doWork(): Result {
        notifier.notify(
            RemoteCommand(
                requestId = SCHEDULED_PREFIX + System.currentTimeMillis(),
                type = RemoteCommandType.SCREEN_CAPTURE,
                childId = "",
            ),
        )
        return Result.success()
    }

    companion object {
        const val UNIQUE = "kidhack.screen.capture"

        /** requestId 가 이 접두사면 서버 ack 대상이 아닌 자체 스케줄 캡처다. */
        const val SCHEDULED_PREFIX = "scheduled-"

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
