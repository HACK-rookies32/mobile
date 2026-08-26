package com.hack.kidhack.background.command

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hack.kidhack.domain.model.CommandAckStatus
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.domain.repository.CommandRepository
import com.hack.kidhack.domain.repository.SessionRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * WebSocket 백업 경로.
 *
 * 도즈 모드나 네트워크 전환으로 소켓이 끊겨 있는 동안 쌓인 요청을
 * 15분마다(WorkManager 최소 주기) 긁어와 알림으로 띄운다.
 * 소켓이 살아 있으면 서버가 pending 을 비워 두므로 대부분 빈 응답으로 끝난다.
 */
class ChildCommandPollWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val commandRepository: CommandRepository by inject()
    private val sessionRepository: SessionRepository by inject()
    private val notifier: ChildCommandNotifier by inject()

    override suspend fun doWork(): Result {
        val childId = sessionRepository.current()?.user?.id ?: return Result.success()
        val commands = runCatching { commandRepository.pending(childId) }
            .getOrElse { return Result.retry() }

        commands.filter { it.type != RemoteCommandType.UNKNOWN }.forEach { command ->
            notifier.notify(command)
            runCatching {
                commandRepository.ack(childId, command.requestId, CommandAckStatus.RECEIVED)
            }
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE = "kidhack.command.poll"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ChildCommandPollWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
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
