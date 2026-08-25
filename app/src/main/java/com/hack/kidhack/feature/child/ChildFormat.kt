package com.hack.kidhack.feature.child

internal fun formatDurationSec(sec: Int): String {
    val hours = sec / 3600
    val minutes = (sec % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}시간"
        else -> "${minutes}m"
    }
}

internal fun formatDurationMin(minutes: Int): String = formatDurationSec(minutes * 60)

internal fun remainingLabel(usedMin: Int, limitMin: Int): String {
    val left = (limitMin - usedMin).coerceAtLeast(0)
    val hours = left / 60
    val minutes = left % 60
    return if (hours > 0) "${hours}시간 ${minutes}분" else "${minutes}분"
}
