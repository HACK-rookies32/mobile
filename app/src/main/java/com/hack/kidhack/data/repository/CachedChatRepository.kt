package com.hack.kidhack.data.repository

import com.hack.kidhack.data.local.ChatMessageStore
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * 어떤 [ChatRepository] 든 감싸서 대화 기록을 기기에 남긴다.
 *
 * 목/실서버 구현을 건드리지 않고 영속성만 얹기 위한 데코레이터다.
 * 부모·자녀 양쪽 채팅이 모두 이걸 거치므로 앱을 완전히 종료해도 대화가 남는다.
 *
 * 서버 조회가 실패하면 캐시를 그대로 돌려주므로 오프라인에서도 지난 대화를 볼 수 있다.
 */
class CachedChatRepository(
    private val delegate: ChatRepository,
    private val store: ChatMessageStore,
) : ChatRepository {

    override suspend fun getOrCreateRoom(parentId: Long, childId: Long): ChatRoom =
        delegate.getOrCreateRoom(parentId, childId).also { store.saveRooms(listOf(it)) }

    override suspend fun rooms(): List<ChatRoom> =
        runCatching { delegate.rooms() }
            .onSuccess { if (it.isNotEmpty()) store.saveRooms(it) }
            .getOrElse { store.loadRooms() }
            .ifEmpty { store.loadRooms() }

    override suspend fun messages(roomId: String, before: String?, size: Int): List<ChatMessage> {
        val cached = store.loadMessages(roomId)
        // 더 이전 메시지를 요청하는 페이징은 캐시와 합치지 않는다(순서가 꼬인다).
        if (before != null) {
            return runCatching { delegate.messages(roomId, before, size) }.getOrDefault(emptyList())
        }
        return runCatching { delegate.messages(roomId, null, size) }
            .fold(
                onSuccess = { fresh -> store.mergeAndSave(roomId, cached, fresh) },
                onFailure = { cached },
            )
    }

    override fun observeMessages(roomId: String): Flow<ChatMessage> =
        delegate.observeMessages(roomId).onEach { store.append(roomId, it) }

    override suspend fun send(roomId: String, content: String): ChatMessage =
        delegate.send(roomId, content).also { store.append(roomId, it) }

    override suspend fun markRead(roomId: String, lastMessageId: String?) =
        delegate.markRead(roomId, lastMessageId)
}
