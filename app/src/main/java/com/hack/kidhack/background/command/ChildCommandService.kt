package com.hack.kidhack.background.command

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.hack.kidhack.R
import com.hack.kidhack.background.ChildNotifications
import com.hack.kidhack.domain.model.CommandAckStatus
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.domain.repository.CommandRepository
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * 부모 요청 수신용 상주 포그라운드 서비스 — **Firebase 없이 푸시를 구현하는 핵심**.
 *
 * 하는 일은 두 가지뿐이다.
 *  1. [CommandRepository.observe] (= WebSocket) 를 계속 구독한다.
 *  2. 요청이 오면 알림으로 띄우고 서버에 RECEIVED ack 를 보낸다.
 *
 * 실제 캡처/촬영은 사용자가 알림을 눌러야 시작된다. 화면 녹화(MediaProjection)와
 * 카메라는 안드로이드가 사용자 동의 없이 백그라운드 실행을 허용하지 않기 때문이다.
 *
 * 배포 시 유의: `dataSync` 포그라운드 타입은 안드로이드 15 에서 하루 6시간 제한이 있다.
 * 자녀 보호 용도로 상시 유지하려면 `specialUse` 선언 + 스토어 심사가 필요하다.
 */
class ChildCommandService : Service() {

    private val commandRepository: CommandRepository by inject()
    private val sessionRepository: SessionRepository by inject()
    private val notifier: ChildCommandNotifier by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ChildNotifications.ensureChannel(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(
                this,
                ChildNotifications.COMMAND_ID,
                ChildNotifications.foreground(
                    this,
                    getString(R.string.child_command_notif_title),
                    getString(R.string.child_command_notif_text),
                ),
                type,
            )
        }.onFailure {
            stopSelf()
            return
        }

        scope.launch {
            commandRepository.observe().collect { command ->
                if (command.type == RemoteCommandType.UNKNOWN) return@collect
                notifier.notify(command)
                val childId = command.childId.ifBlank {
                    sessionRepository.current()?.user?.id.orEmpty()
                }
                runCatching {
                    commandRepository.ack(childId, command.requestId, CommandAckStatus.RECEIVED)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        commandRepository.disconnect()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ChildCommandService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChildCommandService::class.java))
        }
    }
}
