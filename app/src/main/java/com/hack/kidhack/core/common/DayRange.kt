package com.hack.kidhack.core.common

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 오늘 0시 ~ 지금.
 *
 * `GET /api/monitoring/usage/{childId}` 는 `from`/`to` 가 없으면 **최근 1주일**을 합산한다.
 * 「오늘 스크린타임」을 보여 주는 화면이 범위를 넘기지 않으면 일주일치가 오늘 값으로 찍힌다.
 *
 * 서버는 오프셋 없는 `LocalDateTime` 을 받는다(서버 시간대 KST 가정).
 */
fun todayRange(): Pair<String, String> {
    val start = LocalDate.now().atStartOfDay()
    val now = LocalDateTime.now()
    return start.toString() to now.toString()
}
