package com.hack.kidhack.background.push

import com.hack.kidhack.data.local.PushTokenStore
import com.hack.kidhack.domain.repository.DeviceRepository
import com.hack.kidhack.domain.repository.SessionRepository

/**
 * FCM 토큰을 서버에 등록·해제하는 단일 창구.
 *
 * 토큰은 두 계기로 바뀐다 — FCM 이 새 토큰을 발급할 때(`onNewToken`), 그리고 로그인해서
 * 「이 계정 = 이 기기」가 정해질 때. 어느 쪽이 먼저 와도 되도록 마지막 토큰을 [PushTokenStore]
 * 에 남겨 두고, 로그인 시 그 토큰을 다시 올린다.
 *
 * 서버가 아직 등록 엔드포인트를 구현하지 않았어도 실패를 삼켜 앱은 정상 동작한다
 * (그동안은 WebSocket + 폴링 백업 경로가 알림을 나른다).
 */
class PushTokenRegistrar(
    private val sessionRepository: SessionRepository,
    private val deviceRepository: DeviceRepository,
    private val tokenStore: PushTokenStore,
) {
    /** `onNewToken` 콜백에서 호출. 로그인 상태면 곧바로 서버에 올린다. */
    suspend fun onTokenRefreshed(token: String) {
        tokenStore.save(token)
        if (sessionRepository.current() != null) {
            runCatching { deviceRepository.registerPushToken(token) }
        }
    }

    /** 로그인 직후 호출. 마지막으로 받은 토큰을 이 계정에 묶는다. */
    suspend fun onSignedIn() {
        val token = tokenStore.current() ?: return
        runCatching { deviceRepository.registerPushToken(token) }
    }

    /**
     * 로그아웃 시 호출. 이 기기로 더 이상 이 계정 알림이 가지 않게 서버에서 토큰을 지운다.
     * 저장된 토큰 자체는 남긴다 — FCM 토큰은 계정이 아니라 기기에 속하기 때문이다.
     */
    suspend fun onSignedOut() {
        val token = tokenStore.current() ?: return
        runCatching { deviceRepository.unregisterPushToken(token) }
    }
}
