package com.hack.kidhack.data.ws

import com.hack.kidhack.core.common.KidhackJson
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicInteger

/**
 * OkHttp WebSocket client for `wss://…/ws/chat`.
 *
 * Auth is attached as `Authorization: Bearer` and as a `token` query param.
 * Reconnects with exponential backoff while [start] observers remain.
 */
class ChatWebSocketClient(
    private val okHttpClient: OkHttpClient,
    private val sessionRepository: SessionRepository,
    private val baseUrl: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val observers = AtomicInteger(0)
    private val json = KidhackJson.instance

    private val _events = MutableSharedFlow<ChatWsEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ChatWsEvent> = _events.asSharedFlow()

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var wanted: Boolean = false

    private var attempt: Int = 0
    private var reconnectJob: Job? = null

    fun start() {
        if (observers.getAndIncrement() == 0) {
            wanted = true
            scope.launch { connect() }
        }
    }

    fun stop() {
        if (observers.decrementAndGet() <= 0) {
            observers.set(0)
            wanted = false
            reconnectJob?.cancel()
            socket?.close(1000, "idle")
            socket = null
        }
    }

    fun send(roomId: String, content: String) {
        val frame = ChatSocketFrame(
            type = ChatSocketFrame.SEND,
            roomId = roomId,
            content = content,
        )
        val body = json.encodeToString(ChatSocketFrame.serializer(), frame)
        val sent = socket?.send(body) == true
        if (!sent) {
            scope.launch {
                connect(force = true)
                socket?.send(body)
            }
        }
        _events.tryEmit(ChatWsEvent.Send(roomId, content))
    }

    fun read(roomId: String, messageId: String?) {
        val frame = ChatSocketFrame(
            type = ChatSocketFrame.READ,
            roomId = roomId,
            messageId = messageId,
        )
        val body = json.encodeToString(ChatSocketFrame.serializer(), frame)
        if (socket?.send(body) != true) {
            scope.launch {
                connect(force = true)
                socket?.send(body)
            }
        }
        _events.tryEmit(ChatWsEvent.Read(roomId, messageId))
    }

    private suspend fun connect(force: Boolean = false) {
        mutex.withLock {
            if (!wanted && !force) return
            socket?.cancel()
            val token = sessionRepository.accessToken()
            val request = Request.Builder()
                .url(buildWsUrl(token))
                .apply {
                    if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
                }
                .build()
            socket = okHttpClient.newWebSocket(request, listener)
        }
    }

    private fun buildWsUrl(token: String?): String {
        val origin = baseUrl.trimEnd('/')
            .removeSuffix("/api/v1")
            .replace(Regex("^https://"), "wss://")
            .replace(Regex("^http://"), "ws://")
        val raw = "$origin/ws/chat"
        val parsed = raw.toHttpUrlOrNull()
        return if (parsed != null && !token.isNullOrBlank()) {
            parsed.newBuilder().addQueryParameter("token", token).build().toString()
        } else {
            raw
        }
    }

    private fun scheduleReconnect() {
        if (!wanted) return
        reconnectJob?.cancel()
        val delayMs = (1_000L * (1L shl attempt.coerceAtMost(5))).coerceAtMost(30_000L)
        attempt += 1
        reconnectJob = scope.launch {
            delay(delayMs)
            if (wanted) connect()
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            attempt = 0
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val frame = runCatching {
                json.decodeFromString(ChatSocketFrame.serializer(), text)
            }.getOrElse {
                _events.tryEmit(ChatWsEvent.Error("invalid frame"))
                return
            }
            when (frame.type.uppercase()) {
                ChatSocketFrame.MESSAGE -> {
                    val roomId = frame.roomId ?: return
                    val body = frame.content ?: frame.text.orEmpty()
                    val role = runCatching { Role.valueOf(frame.fromRole.orEmpty()) }
                        .getOrDefault(Role.PARENT)
                    val message = ChatMessage(
                        id = frame.id ?: frame.messageId ?: "ws-${System.currentTimeMillis()}",
                        roomId = roomId,
                        fromRole = role,
                        text = body,
                        sentAt = frame.sentAt.orEmpty(),
                    )
                    _events.tryEmit(ChatWsEvent.Message(message))
                }
                ChatSocketFrame.READ -> {
                    _events.tryEmit(ChatWsEvent.Read(frame.roomId.orEmpty(), frame.messageId ?: frame.id))
                }
                ChatSocketFrame.ERROR -> {
                    _events.tryEmit(ChatWsEvent.Error(frame.message ?: frame.content ?: "chat error"))
                }
                ChatSocketFrame.SEND -> {
                    val roomId = frame.roomId ?: return
                    _events.tryEmit(ChatWsEvent.Send(roomId, frame.content ?: frame.text.orEmpty()))
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (wanted) scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _events.tryEmit(ChatWsEvent.Error(t.message ?: "socket failure"))
            if (wanted) scheduleReconnect()
        }
    }
}
