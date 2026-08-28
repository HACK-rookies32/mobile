package com.hack.kidhack.background

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
import com.hack.kidhack.data.local.ChildAlertStore
import com.hack.kidhack.domain.model.childAlertTypeToParent
import com.hack.kidhack.domain.repository.ChildAlertRepository
import com.hack.kidhack.domain.repository.SessionRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * 자녀 홈 「최근 알림」의 안전 사건을 서버로 올린다 (docs/child-push-contract.md 4절).
 *
 * [ChildAlertStore] 가 원본이다 — 자녀 화면에 뜬 것과 똑같은 사건이 보호자에게 간다.
 * 감지 즉시 [enqueueNow] 로 한 번, 놓친 것은 15분 주기로 다시 올린다.
 * 서버가 이 경로를 아직 구현하지 않았으면 다음 주기에 재시도만 하고 조용히 넘어간다.
 */
class ChildAlertUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val alertStore: ChildAlertStore by inject()
    private val childAlertRepository: ChildAlertRepository by inject()
    private val sessionRepository: SessionRepository by inject()

    override suspend fun doWork(): Result {
        val childId = sessionRepository.current()?.user?.id ?: return Result.success()

        // 캡처 요청 같은 비안전 항목은 보호자에게 나를 필요가 없다. 다시 집히지 않게 완료 표시만 한다.
        val (send, skip) = alertStore.pendingUpload()
            .partition { childAlertTypeToParent(it.type) != null }
        if (skip.isNotEmpty()) alertStore.markUploaded(skip.map { it.id })
        if (send.isEmpty()) return Result.success()

        return runCatching { childAlertRepository.upload(childId, send) }.fold(
            onSuccess = {
                alertStore.markUploaded(send.map { it.id })
                Result.success()
            },
            onFailure = { if (runAttemptCount < 3) Result.retry() else Result.success() },
        )
    }

    companion object {
        const val UNIQUE = "kidhack.child.alert.upload"
        private const val UNIQUE_NOW = "kidhack.child.alert.upload.now"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ChildAlertUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /** 사건이 감지된 직후. 보호자가 다음 폴링에서 바로 받아 볼 수 있게 지체 없이 올린다. */
        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ChildAlertUploadWorker>()
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(UNIQUE)
                cancelUniqueWork(UNIQUE_NOW)
            }
        }

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
