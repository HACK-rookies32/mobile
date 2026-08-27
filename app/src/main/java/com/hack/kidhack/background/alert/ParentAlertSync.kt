package com.hack.kidhack.background.alert

import com.hack.kidhack.data.local.ParentAlertStore
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.repository.NotificationRepository
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 부모 알림의 단일 진입점.
 *
 * 1. 서버 알림 목록을 가져와 [ParentAlertStore] 에 **영구 저장**한다.
 * 2. 이번에 처음 들어온 알림만 골라 푸시로 띄운다.
 * 3. 화면에 그릴 목록(로컬 보관함 전체)을 돌려준다.
 *
 * 대시보드·알림 탭의 새로고침과 15분 주기 [ParentAlertPollWorker] 가 모두 이 함수를 쓴다.
 * 그래서 앱을 켜 두든 꺼 두든 같은 결과가 나온다.
 */
class ParentAlertSync(
    private val sessionRepository: SessionRepository,
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository,
    private val store: ParentAlertStore,
    private val notifier: ParentAlertNotifier,
) {
    /** 워커와 화면 새로고침이 동시에 돌 때 같은 알림을 두 번 띄우지 않도록 직렬화한다. */
    private val mutex = Mutex()

    private val _unreadCount = MutableStateFlow(0)

    /** 대시보드 종 아이콘 배지에 쓰는 미확인 건수. */
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    data class Result(
        val alerts: List<AppNotification>,
        /** 서버 조회가 실패했는지. 실패해도 [alerts] 에는 보관해 둔 알림이 그대로 들어 있다. */
        val remoteFailed: Boolean = false,
    )

    suspend fun sync(): Result = mutex.withLock {
        if (sessionRepository.current()?.user?.role != Role.PARENT) {
            return@withLock Result(emptyList())
        }
        val remote = runCatching { notificationRepository.list(null) }
        remote.getOrNull()?.let { list ->
            val fresh = store.merge(list)
            if (fresh.isNotEmpty()) {
                // 수신 설정이 꺼진 종류는 푸시를 띄우지 않는다. 다만 보관함에는 남겨 두어
                // 알림 탭에서는 나중에라도 확인할 수 있게 한다.
                val settings = runCatching { userRepository.notificationSettings() }.getOrNull()
                notifier.notifyAll(fresh, settings)
                // 푸시를 건너뛴 알림도 notified 로 표시한다. 설정을 다시 켰을 때
                // 예전 알림이 한꺼번에 울리는 일을 막는다.
                store.markNotified(fresh.map { it.id })
            }
        }
        Result(alerts = store.all(), remoteFailed = remote.isFailure).also { refreshUnread() }
    }

    /** 서버를 건드리지 않고 보관함만 읽는다. */
    suspend fun cached(): List<AppNotification> = store.all().also { refreshUnread() }

    suspend fun markAllRead() {
        store.markAllRead()
        refreshUnread()
    }

    private suspend fun refreshUnread() {
        _unreadCount.value = store.unreadCount()
    }
}
