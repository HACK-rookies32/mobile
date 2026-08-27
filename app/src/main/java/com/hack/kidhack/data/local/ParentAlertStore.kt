package com.hack.kidhack.data.local

import com.hack.kidhack.core.common.KidhackJson
import com.hack.kidhack.domain.model.AppNotification
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 부모 계정의 **영구 알림 보관함**.
 *
 * 대시보드 「최근 알림」은 지금까지 `GET /notifications` 응답을 그대로 그렸다.
 * 그래서 서버가 목록에서 지우거나(보관 기간 만료) 네트워크가 끊기면 이미 받아 둔
 * 「안전 구역 이탈」·「유해 사이트 접속 차단」이 화면에서 사라졌다.
 *
 * 이 저장소는 한 번 들어온 알림을 기기에 남겨 두고, 서버 응답과 **합쳐서** 돌려준다.
 * 동시에 아직 푸시로 띄우지 않은 알림([merge] 의 반환값)을 알려 주므로
 * 같은 알림이 두 번 울리지 않는다.
 *
 * 자녀 기기 사건을 남기는 [ChildAlertStore] 의 부모 버전이다.
 */
class ParentAlertStore(context: Context) {

    @Serializable
    private data class Entry(
        val id: String,
        val type: String,
        val title: String,
        val body: String = "",
        /** 서버가 준 표시용 시각 문자열("14:20" 등). 비어 있으면 [receivedAtMillis] 로 만든다. */
        val createdAt: String = "",
        /** 기기가 이 알림을 처음 본 시각. 정렬 기준이자 표시 시각의 대체값. */
        val receivedAtMillis: Long,
        val read: Boolean = false,
        /** 푸시 알림을 이미 띄웠는지. 재시도 중복 발송을 막는다. */
        val notified: Boolean = false,
    )

    private val file = File(context.filesDir, "parent_alerts.json")
    private val mutex = Mutex()
    private val json = KidhackJson.instance
    private val serializer = ListSerializer(Entry.serializer())

    /**
     * 서버 목록을 보관함에 합친다.
     *
     * @return 이번에 처음 들어와 **아직 푸시를 띄우지 않은** 알림들(오래된 것부터).
     */
    suspend fun merge(remote: List<AppNotification>): List<AppNotification> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val stored = readUnlocked()
                val byId = stored.associateBy { it.id }.toMutableMap()
                val now = System.currentTimeMillis()
                val fresh = mutableListOf<Entry>()

                // 목록은 최신순으로 내려온다. 뒤에 있을수록 오래된 알림이므로
                // 수신 시각을 1ms 씩 앞당겨 서버가 준 순서를 그대로 유지한다.
                remote.forEachIndexed { index, item ->
                    val id = item.identity()
                    val old = byId[id]
                    if (old == null) {
                        val entry = Entry(
                            id = id,
                            type = item.type,
                            title = item.title,
                            body = item.body,
                            createdAt = item.createdAt,
                            receivedAtMillis = now - index,
                            read = item.read,
                            notified = false,
                        )
                        byId[id] = entry
                        fresh += entry
                    } else {
                        // 서버에서 읽음 처리된 알림은 로컬도 따라간다(반대 방향은 유지).
                        byId[id] = old.copy(read = old.read || item.read)
                    }
                }

                writeUnlocked(byId.values.sortedByDescending { it.receivedAtMillis })
                fresh.filter { !it.notified }
                    .sortedBy { it.receivedAtMillis }
                    .map { it.toDomain() }
            }
        }

    /** 기기에서 직접 감지한 사건을 보관함에 넣는다(서버를 거치지 않는 경로). */
    suspend fun record(
        id: String,
        type: String,
        title: String,
        body: String = "",
    ): AppNotification? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val stored = readUnlocked()
            if (stored.any { it.id == id }) return@withLock null
            val entry = Entry(
                id = id,
                type = type,
                title = title,
                body = body,
                receivedAtMillis = System.currentTimeMillis(),
            )
            writeUnlocked(listOf(entry) + stored)
            entry.toDomain()
        }
    }

    /** 푸시를 띄운 뒤 호출한다. 워커가 재시도해도 같은 알림이 다시 울리지 않는다. */
    suspend fun markNotified(ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val target = ids.toSet()
        mutex.withLock {
            val next = readUnlocked().map {
                if (it.id in target) it.copy(notified = true) else it
            }
            writeUnlocked(next)
        }
        Unit
    }

    /** 알림 탭을 열었을 때. 대시보드의 미확인 건수를 0 으로 만든다. */
    suspend fun markAllRead() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val stored = readUnlocked()
            if (stored.none { !it.read }) return@withLock
            writeUnlocked(stored.map { it.copy(read = true) })
        }
        Unit
    }

    suspend fun markRead(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val stored = readUnlocked()
            if (stored.none { it.id == id && !it.read }) return@withLock
            writeUnlocked(stored.map { if (it.id == id) it.copy(read = true) else it })
        }
        Unit
    }

    suspend fun all(limit: Int = MAX_ENTRIES): List<AppNotification> = withContext(Dispatchers.IO) {
        mutex.withLock { readUnlocked() }
            .sortedByDescending { it.receivedAtMillis }
            .take(limit)
            .map { it.toDomain() }
    }

    suspend fun unreadCount(): Int = withContext(Dispatchers.IO) {
        mutex.withLock { readUnlocked() }.count { !it.read }
    }

    /** 다른 계정의 알림이 보이지 않도록 계정 전환 시에만 비운다. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock { runCatching { file.delete() } }
        Unit
    }

    private fun Entry.toDomain(): AppNotification = AppNotification(
        id = id,
        type = type,
        title = title,
        body = body,
        createdAt = createdAt.ifBlank { timeLabel(receivedAtMillis) },
        read = read,
    )

    /** 서버가 id 를 비워 보내도 같은 알림이 매번 새 알림으로 쌓이지 않게 한다. */
    private fun AppNotification.identity(): String =
        id.ifBlank { LOCAL_ID_PREFIX + listOf(type, title, body, createdAt).joinToString("|").hashCode() }

    private fun readUnlocked(): List<Entry> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString(serializer, file.readText()) }
            .getOrDefault(emptyList())
    }

    private fun writeUnlocked(entries: List<Entry>) {
        val capped = entries.sortedByDescending { it.receivedAtMillis }.take(MAX_ENTRIES)
        runCatching { file.writeText(json.encodeToString(serializer, capped)) }
    }

    private fun timeLabel(atMillis: Long): String {
        val diff = System.currentTimeMillis() - atMillis
        return if (diff < 86_400_000) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(atMillis))
        } else {
            SimpleDateFormat("MM.dd HH:mm", Locale.getDefault()).format(Date(atMillis))
        }
    }

    companion object {
        const val TYPE_GEOFENCE_EXIT = "LOCATION"
        const val TYPE_HARMFUL = "HARMFUL"

        private const val LOCAL_ID_PREFIX = "local-"
        private const val MAX_ENTRIES = 200
    }
}
