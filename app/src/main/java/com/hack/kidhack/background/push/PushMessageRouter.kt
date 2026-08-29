package com.hack.kidhack.background.push

import android.content.Context
import com.hack.kidhack.R
import com.hack.kidhack.background.alert.ServerAlertSync
import com.hack.kidhack.background.command.ChildCommandBus
import com.hack.kidhack.background.command.ChildCommandNotifier
import com.hack.kidhack.core.common.KoreanParticles
import com.hack.kidhack.data.local.ChildAlertStore
import com.hack.kidhack.domain.model.CommandAckStatus
import com.hack.kidhack.domain.model.RemoteCommand
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.data.repository.toRemoteCommandType
import com.hack.kidhack.domain.repository.CommandRepository
import com.hack.kidhack.domain.repository.SessionRepository

/**
 * FCM 으로 들어온 푸시 하나를 알맞은 기존 처리 경로로 흘려 보낸다.
 *
 * 두 갈래다.
 *
 * - **보호자 알림**([PushMessage.Kind.PARENT_ALERT]) → [ServerAlertSync.recordRemote].
 *   보관함에 영구 기록 + 헤드업 푸시 + 대시보드 「최근 알림」 즉시 반영이 한 번에 된다.
 *   폴링 경로와 **완전히 같은 함수**를 쓰므로, FCM 과 15분 폴링이 같은 알림을 둘 다 물어와도
 *   id 로 합쳐져 중복 푸시가 없다.
 *
 * - **자녀 명령**([PushMessage.Kind.CHILD_COMMAND]) → 알림([ChildCommandNotifier]) +
 *   홈 기록([ChildAlertStore]) + 버스([ChildCommandBus]) + 수신 ACK.
 *   `ChildCommandService` 가 WebSocket 프레임을 처리하던 것과 **동일한 동작**이라,
 *   FCM 을 주 경로로 켜도 화면 이동·캡처/촬영 플로우가 그대로 이어진다.
 *
 * 역할을 확인해 엉뚱한 기기가 처리하지 않게 한다(부모 기기가 자녀 명령을 받거나 그 반대).
 * 서버가 올바른 토큰으로만 보내더라도, 계정 전환 직후 옛 토큰으로 온 푸시를 거른다.
 */
class PushMessageRouter(
    private val context: Context,
    private val sessionRepository: SessionRepository,
    private val serverAlertSync: ServerAlertSync,
    private val childCommandBus: ChildCommandBus,
    private val childCommandNotifier: ChildCommandNotifier,
    private val childAlertStore: ChildAlertStore,
    private val commandRepository: CommandRepository,
) {
    suspend fun route(message: PushMessage) {
        when (message.kind) {
            PushMessage.Kind.PARENT_ALERT -> routeParentAlert(message)
            PushMessage.Kind.CHILD_COMMAND -> routeChildCommand(message)
            PushMessage.Kind.UNKNOWN -> Unit
        }
    }

    private suspend fun routeParentAlert(message: PushMessage) {
        if (sessionRepository.current()?.user?.role != Role.PARENT) return
        serverAlertSync.recordRemote(
            id = message.id.ifBlank { "fcm-" + message.type + "-" + message.title },
            type = message.type,
            title = message.title,
            body = message.body,
            childId = message.childId,
        )
    }

    private suspend fun routeChildCommand(message: PushMessage) {
        if (sessionRepository.current()?.user?.role != Role.CHILD) return
        val type = message.type.toRemoteCommandType()
        if (type == RemoteCommandType.UNKNOWN) return
        val childId = message.childId?.takeIf { it.isNotBlank() }
            ?: sessionRepository.current()?.user?.id.orEmpty()
        val command = RemoteCommand(
            requestId = message.id,
            type = type,
            childId = childId,
            requestedBy = message.requestedBy,
        )
        // ChildCommandService 의 소켓 처리와 동일한 3동작.
        childCommandNotifier.notify(command)
        recordChildAlert(command)
        childCommandBus.post(command)
        runCatching {
            commandRepository.ack(childId, command.requestId, CommandAckStatus.RECEIVED)
        }
    }

    private suspend fun recordChildAlert(command: RemoteCommand) {
        val who = KoreanParticles.subject(
            command.requestedBy?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.child_request_default_sender),
        )
        when (command.type) {
            RemoteCommandType.SCREEN_CAPTURE -> childAlertStore.record(
                type = ChildAlertStore.TYPE_CAPTURE_REQUEST,
                title = context.getString(R.string.child_alert_capture_request),
                body = context.getString(R.string.child_alert_request_from, who),
                dedupeKey = ChildAlertStore.TYPE_CAPTURE_REQUEST + "-" + command.requestId,
            )
            RemoteCommandType.CAMERA_SHOT -> childAlertStore.record(
                type = ChildAlertStore.TYPE_CAMERA_REQUEST,
                title = context.getString(R.string.child_alert_camera_request),
                body = context.getString(R.string.child_alert_request_from, who),
                dedupeKey = ChildAlertStore.TYPE_CAMERA_REQUEST + "-" + command.requestId,
            )
            RemoteCommandType.UNKNOWN -> Unit
        }
    }
}
