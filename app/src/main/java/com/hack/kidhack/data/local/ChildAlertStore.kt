package com.hack.kidhack.data.local

import android.content.Context
import com.hack.kidhack.core.common.KidhackJson
import com.hack.kidhack.domain.model.AppNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 기기에서 **실제로 일어난** 사건을 남기는 로컬 알림 기록.
 *
 * 유해 사이트 차단이나 스크린타임 한도 도달은 자녀 기기에서 즉시 발생하는데,
 * 서버 알림 목록(`GET /notifications`)에는 서버가 내려 준 것만 들어 있다.
 * 그래서 홈 탭 「최근 알림」이 이 사건들을 놓쳤다. 이 저장소가 그 빈틈을 메운다.
 *
 * 서버 알림을 대체하는 게 아니라 **합쳐서** 보여 주는 용도다.
 */
class ChildAlertStore(context: Context) {

    @Serializable
    private data class Entry(
        val id: String,
        val type: String,
        val title: String,
        val body: String = "",
        val atMillis: Long,
    )

    private val file = File(context.filesDir, "child_alerts.json")
    private val mutex = Mutex()
    private val json = KidhackJson.instance
    private val serializer = ListSerializer(Entry.serializer())

    /** 같은 사건이 연달아 쌓이지 않도록 종류별 최소 간격을 둔다. */
    suspend fun record(
        type: String,
        title: String,
        body: String = "",
        dedupeKey: String = type,
        dedupeWindowMs: Long = DEFAULT_DEDUPE_MS,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val entries = readUnlocked()
            val duplicate = entries.firstOrNull {
                it.id.startsWith(dedupeKey) && now - it.atMillis < dedupeWindowMs
            }
            if (duplicate != null) return@withLock
            val next = listOf(
                Entry(
                    id = dedupeKey + "-" + now,
                    type = type,
                    title = title,
                    body = body,
                    atMillis = now,
                ),
            ) + entries
            writeUnlocked(next.take(MAX_ENTRIES))
        }
        Unit
    }

    /** 하루에 한 번만 남겨야 하는 사건(스크린타임 한도 도달 등). */
    suspend fun recordOncePerDay(type: String, title: String, body: String = "") =
        record(
            type = type,
            title = title,
            body = body,
            dedupeKey = type + "-" + today(),
            dedupeWindowMs = Long.MAX_VALUE,
        )

    suspend fun recent(limit: Int = MAX_ENTRIES): List<AppNotification> = withContext(Dispatchers.IO) {
        mutex.withLock { readUnlocked() }
            .sortedByDescending { it.atMillis }
            .take(limit)
            .map {
                AppNotification(
                    id = it.id,
                    type = it.type,
                    title = it.title,
                    body = it.body,
                    createdAt = relativeLabel(it.atMillis),
                    read = false,
                )
            }
    }

    /** 오늘 발생한 특정 종류의 건수. 「오늘 차단된 사이트: N건」에 쓴다. */
    suspend fun countToday(type: String): Int = withContext(Dispatchers.IO) {
        val dayStart = startOfToday()
        mutex.withLock { readUnlocked() }
            .count { it.type == type && it.atMillis >= dayStart }
    }

    /** 로그아웃 시 다른 계정에 이전 기록이 보이지 않도록 지운다. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock { runCatching { file.delete() } }
        Unit
    }

    private fun readUnlocked(): List<Entry> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString(serializer, file.readText()) }
            .getOrDefault(emptyList())
    }

    private fun writeUnlocked(entries: List<Entry>) {
        runCatching { file.writeText(json.encodeToString(serializer, entries)) }
    }

    private fun relativeLabel(atMillis: Long): String {
        val diff = System.currentTimeMillis() - atMillis
        return when {
            diff < 60_000 -> "방금 전"
            diff < 3_600_000 -> (diff / 60_000).toString() + "분 전"
            diff < 86_400_000 -> (diff / 3_600_000).toString() + "시간 전"
            else -> SimpleDateFormat("MM.dd HH:mm", Locale.getDefault()).format(Date(atMillis))
        }
    }

    private fun today(): String =
        SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        const val TYPE_HARMFUL_BLOCK = "HARMFUL_BLOCK"
        const val TYPE_SCREEN_TIME = "SCREEN_TIME_LIMIT"
        const val TYPE_CAPTURE_REQUEST = "CAPTURE_REQUEST"
        const val TYPE_CAMERA_REQUEST = "CAMERA_REQUEST"
        const val TYPE_SCHEDULED_CAPTURE = "SCHEDULED_CAPTURE"

        private const val MAX_ENTRIES = 50
        private const val DEFAULT_DEDUPE_MS = 60_000L
    }
}
