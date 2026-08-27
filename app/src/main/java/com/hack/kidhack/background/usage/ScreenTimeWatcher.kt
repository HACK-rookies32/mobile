package com.hack.kidhack.background.usage

import com.hack.kidhack.data.local.ChildAlertStore

/**
 * 스크린타임 한도 도달을 감시해 알림 기록으로 남긴다.
 *
 * 한도 자체는 명세에 바꾸는 API 가 없어서(GAP-12) 클라이언트 상수다.
 * 홈 화면과 시간당 워커가 **같은 상수·같은 판정**을 쓰도록 여기 한 곳에 모아 둔다.
 * 서버가 `children/{id}` 에 한도 필드를 주면 [dailyLimitMinutes] 만 갈아 끼우면 된다.
 */
class ScreenTimeWatcher(
    private val alertStore: ChildAlertStore,
) {
    val dailyLimitMinutes: Int = DEFAULT_DAILY_LIMIT_MINUTES

    /** 한도에 닿았으면 하루 한 번만 기록한다. 이미 남긴 날이면 아무 일도 하지 않는다. */
    suspend fun check(usedMinutes: Int) {
        if (usedMinutes < dailyLimitMinutes) return
        alertStore.recordOncePerDay(
            type = ChildAlertStore.TYPE_SCREEN_TIME,
            title = "오늘 스크린타임 한도에 도달했어요",
            body = "오늘 " + formatMinutes(usedMinutes) + " 사용 · 한도 " + formatMinutes(dailyLimitMinutes),
        )
    }

    private fun formatMinutes(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours > 0 && rest > 0 -> hours.toString() + "시간 " + rest + "분"
            hours > 0 -> hours.toString() + "시간"
            else -> rest.toString() + "분"
        }
    }

    private companion object {
        const val DEFAULT_DAILY_LIMIT_MINUTES = 180
    }
}
