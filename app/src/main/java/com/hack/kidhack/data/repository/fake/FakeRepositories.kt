package com.hack.kidhack.data.repository.fake

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.hack.kidhack.data.remote.PageResponse
import com.hack.kidhack.data.remote.paging.PageResponsePagingSource
import com.hack.kidhack.domain.model.Allowance
import com.hack.kidhack.domain.model.AllowanceEntry
import com.hack.kidhack.domain.model.AllowancePayment
import com.hack.kidhack.domain.model.AllowanceType
import com.hack.kidhack.domain.model.AppControlItem
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.model.BlocklistItem
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.model.ChildPhoto
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.LocationPoint
import com.hack.kidhack.domain.model.NotificationSettings
import com.hack.kidhack.domain.model.PairingCode
import com.hack.kidhack.domain.model.Payment
import com.hack.kidhack.domain.model.PointBalance
import com.hack.kidhack.domain.model.PointHistory
import com.hack.kidhack.domain.model.Post
import com.hack.kidhack.domain.model.RemoteCommand
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Screenshot
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.model.Subscription
import com.hack.kidhack.domain.model.User
import com.hack.kidhack.domain.repository.AllowanceRepository
import com.hack.kidhack.domain.repository.AppControlRepository
import com.hack.kidhack.domain.repository.AppUsageRepository
import com.hack.kidhack.domain.repository.AuthRepository
import com.hack.kidhack.domain.repository.BlocklistRepository
import com.hack.kidhack.domain.repository.ChatRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.CommandRepository
import com.hack.kidhack.domain.repository.CommunityRepository
import com.hack.kidhack.domain.repository.GeofenceRepository
import com.hack.kidhack.domain.repository.LocationRepository
import com.hack.kidhack.domain.repository.NotificationRepository
import com.hack.kidhack.domain.repository.PairingRepository
import com.hack.kidhack.domain.repository.PaymentRepository
import com.hack.kidhack.domain.repository.PhotoRepository
import com.hack.kidhack.domain.repository.PointRepository
import com.hack.kidhack.domain.repository.ScreenshotRepository
import com.hack.kidhack.domain.repository.SessionEvents
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.SubscriptionRepository
import com.hack.kidhack.domain.repository.UserRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FakeAuthRepository(
    private val sessionRepository: SessionRepository,
    private val sessionEvents: SessionEvents,
) : AuthRepository {
    override suspend fun signup(
        loginId: String,
        password: String,
        name: String,
        role: Role,
        phone: String,
        email: String?,
        pairingCode: String?,
    ): Session {
        delay(300)
        if (role == Role.CHILD && !pairingCode.isNullOrBlank() && pairingCode != FakeData.PAIRING_CODE) {
            error("페어링 코드가 올바르지 않아요")
        }
        val user = User(
            id = "user-$loginId",
            loginId = loginId,
            name = name,
            role = role,
            phone = phone,
            email = email,
        )
        val session = FakeData.sessionFor(user)
        sessionRepository.save(session)
        return session
    }

    override suspend fun login(loginId: String, password: String, preferredRole: Role?): Session {
        delay(250)
        require(loginId.isNotBlank() && password.isNotBlank()) { "아이디와 비밀번호를 입력해 주세요" }
        val user = when {
            preferredRole == Role.CHILD || loginId.contains("minjun") || loginId.startsWith("child") ->
                FakeData.childUser.copy(loginId = loginId.ifBlank { FakeData.childUser.loginId })
            else -> FakeData.parent.copy(loginId = loginId.ifBlank { FakeData.parent.loginId })
        }
        val session = FakeData.sessionFor(user)
        sessionRepository.save(session)
        return session
    }

    override suspend fun logout() {
        sessionRepository.clear()
        sessionEvents.emitLogout()
    }

    override suspend fun findId(name: String, phone: String): String {
        delay(200)
        return if (name.contains("민준")) FakeData.childUser.loginId else FakeData.parent.loginId
    }

    override suspend fun resetPassword(loginId: String, verifyCode: String, newPassword: String) {
        delay(200)
        require(verifyCode.length >= 4) { "인증 코드가 올바르지 않아요" }
        require(newPassword.length >= 8) { "비밀번호는 8자 이상이어야 해요" }
    }
}

/**
 * 목 모드 사용자 저장소.
 *
 * 예전에는 로그인한 계정과 무관하게 항상 [FakeData.parent] 를 돌려줘서
 * 자녀로 로그인해도 부모 정보가 보였고, 수정해도 아무 데도 반영되지 않았다.
 * 지금은 현재 세션의 사용자를 기준으로 동작한다.
 */
class FakeUserRepository(
    private val sessionRepository: SessionRepository,
) : UserRepository {
    private var settings = NotificationSettings(true, true, false, true)

    override suspend fun me(): User =
        sessionRepository.current()?.user ?: FakeData.parent

    override suspend fun updateMe(name: String?, phone: String?, email: String?): User {
        delay(200)
        val current = me()
        return current.copy(
            name = name?.takeIf { it.isNotBlank() } ?: current.name,
            phone = phone ?: current.phone,
            email = email,
        )
    }

    override suspend fun deleteMe() = Unit
    override suspend fun notificationSettings(): NotificationSettings = settings
    override suspend fun patchNotificationSettings(settings: NotificationSettings): NotificationSettings {
        this.settings = settings
        return settings
    }
}

class FakePairingRepository : PairingRepository {
    override suspend fun issueCode(): PairingCode = FakeData.pairing
    override suspend fun connect(pairingCode: String) {
        require(pairingCode == FakeData.PAIRING_CODE) { "페어링 코드가 올바르지 않아요" }
    }
}

class FakeChildRepository : ChildRepository {
    override suspend fun children(): List<ChildProfile> = FakeData.children
    override suspend fun child(childId: String): ChildProfile =
        FakeData.children.first { it.id == childId }
    override suspend fun updateChild(
        childId: String,
        name: String?,
        alias: String?,
        profileImageUrl: String?,
    ): ChildProfile = child(childId).copy(
        name = name ?: child(childId).name,
        alias = alias ?: child(childId).alias,
        profileImageUrl = profileImageUrl ?: child(childId).profileImageUrl,
    )
}

class FakeLocationRepository : LocationRepository {
    override suspend fun current(childId: String) = LocationPoint(
        lat = "37.5172",
        lng = "127.0473",
        recordedAt = "2026-08-25T14:22:00",
        label = "서울시 강남구 학동로 12길 (학교 근처)",
    )
    override suspend fun history(childId: String) = listOf(
        LocationPoint("37.4979", "127.0276", "08:10", "집 출발"),
        LocationPoint("37.5172", "127.0473", "08:40", "학교 도착"),
        LocationPoint("37.5048", "127.0490", "15:10", "학원"),
        current(childId),
    )
    override suspend fun uploadBatch(points: List<LocationPoint>) = Unit
    override suspend fun reportGpsStatus(gpsEnabled: Boolean, reportedAt: String) = Unit
}

class FakeGeofenceRepository : GeofenceRepository {
    private val items = mutableListOf(
        Geofence("geo-school", "학교", "37.5172", "127.0473", 200, true),
        Geofence("geo-home", "집", "37.4979", "127.0276", 150, true),
    )
    override suspend fun list(childId: String) = items.toList()
    override suspend fun create(childId: String, geofence: Geofence): Geofence {
        val created = geofence.copy(id = "geo-${items.size + 1}")
        items += created
        return created
    }
    override suspend fun delete(childId: String, geofenceId: String) {
        items.removeAll { it.id == geofenceId }
    }
}

class FakeNotificationRepository : NotificationRepository {
    private val items = listOf(
        AppNotification("n1", "HARMFUL", "유해 사이트 차단", "성인 콘텐츠 접속을 차단했어요", "10분 전", false),
        AppNotification("n2", "ARRIVAL", "학교 도착 확인", "김민준 님이 학교에 도착했어요", "오늘 08:42", true),
        AppNotification("n3", "NOTICE", "루키즈 앱 사용량 제한 도달", "오늘 스크린타임 한도에 도달했어요", "어제", true),
    )
    override fun paged(type: String?): Flow<PagingData<AppNotification>> = Pager(PagingConfig(20)) {
        PageResponsePagingSource { page, size ->
            val filtered = items.filter { type == null || it.type == type }
            PageResponse(filtered, page, size, filtered.size.toLong(), 1)
        }
    }.flow
    override suspend fun list(type: String?) = items.filter { type == null || it.type == type }
}

class FakeBlocklistRepository : BlocklistRepository {
    private val items = mutableListOf(
        BlocklistItem("b1", "BLACK", "gambling.com"),
        BlocklistItem("b2", "BLACK", "성인"),
        BlocklistItem("w1", "WHITE", "school.ac.kr"),
    )
    override suspend fun get(childId: String) = items.toList()
    override suspend fun add(childId: String, listType: String, keyword: String) {
        items += BlocklistItem("b-${items.size}", listType, keyword)
    }
    override suspend fun remove(childId: String, itemId: String) {
        items.removeAll { it.id == itemId }
    }
    override suspend fun reportHarmfulAccess(matchedText: String, accessedAt: String) = Unit
}

/**
 * 자녀가 올린 캡처·촬영본이 모이는 목 저장소.
 *
 * Koin 싱글턴 두 개([FakeScreenshotRepository], [FakePhotoRepository])가 같은 목록에 쓴다.
 * 덕분에 서버 없이도 「자녀 계정에서 촬영 → 부모 계정 화면 캡처 내역에 표시」가 그대로 재현된다.
 */
object FakeCapturedMedia {
    private val lock = Any()
    private val items = mutableListOf(
        Screenshot("s1", "오늘 14:22", "YouTube 시청 중", "MANUAL"),
        Screenshot("s2", "오늘 11:05", "카카오톡", "MANUAL"),
        Screenshot("s3", "어제 18:40", "Chrome", "SCHEDULED"),
    )

    fun snapshot(): List<Screenshot> = synchronized(lock) { items.toList() }

    fun add(item: Screenshot): Screenshot = synchronized(lock) {
        items.add(0, item)
        item
    }
}

class FakeScreenshotRepository : ScreenshotRepository {
    override fun paged(childId: String): Flow<PagingData<Screenshot>> = Pager(PagingConfig(20)) {
        PageResponsePagingSource { page, size ->
            val items = FakeCapturedMedia.snapshot()
            PageResponse(items, page, size, items.size.toLong(), 1)
        }
    }.flow

    override suspend fun list(childId: String) = FakeCapturedMedia.snapshot()

    override suspend fun requestCapture(childId: String) = Unit

    override suspend fun upload(
        childId: String,
        file: File,
        capturedAt: String,
        trigger: String,
    ): Screenshot = FakeCapturedMedia.add(
        Screenshot(
            id = "s-" + System.currentTimeMillis(),
            capturedAt = SimpleDateFormat("MM.dd HH:mm", Locale.getDefault()).format(Date()),
            description = "화면 캡처",
            trigger = trigger,
            imageUrl = file.toURI().toString(),
        ),
    )
}

class FakeCommunityRepository : CommunityRepository {
    private val items = FakeData.posts.toMutableList()
    override fun pagedPosts(): Flow<PagingData<Post>> = Pager(PagingConfig(20)) {
        PageResponsePagingSource { page, size ->
            PageResponse(items.toList(), page, size, items.size.toLong(), 1)
        }
    }.flow
    override suspend fun posts() = items.toList()
    override suspend fun post(postId: String) = items.first { it.id == postId }
    override suspend fun create(title: String, body: String): Post {
        val created = Post("p-${items.size + 1}", title, FakeData.parent.name, 0, 0, "방금")
        items.add(0, created)
        return created
    }
    override suspend fun like(postId: String) {
        val i = items.indexOfFirst { it.id == postId }
        if (i >= 0) {
            val p = items[i]
            items[i] = p.copy(liked = !p.liked, likeCount = if (p.liked) p.likeCount - 1 else p.likeCount + 1)
        }
    }
    override suspend fun comment(postId: String, body: String) {
        val i = items.indexOfFirst { it.id == postId }
        if (i >= 0) items[i] = items[i].copy(commentCount = items[i].commentCount + 1)
    }
}

class FakeChatRepository(
    private val sessionRepository: SessionRepository,
) : ChatRepository {
    private val lock = Any()
    private val store = mutableMapOf(
        "room-minjun" to FakeData.chatMessages.toMutableList(),
        "room-jia" to mutableListOf<ChatMessage>(),
    )
    private val incoming = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)

    /** 방 제목은 보는 쪽 기준으로 바뀐다 — 부모는 자녀 이름을, 자녀는 보호자 이름을 본다. */
    override suspend fun rooms(): List<ChatRoom> {
        val role = sessionRepository.current()?.user?.role ?: Role.PARENT
        return synchronized(lock) {
            if (role == Role.CHILD) {
                listOf(roomOf("room-minjun", "엄마 (" + FakeData.parent.name + ")", true))
            } else {
                listOf(
                    roomOf("room-minjun", "김민준", true),
                    roomOf("room-jia", "이지아", false),
                )
            }
        }
    }

    override suspend fun messages(roomId: String, before: String?, size: Int): List<ChatMessage> {
        val all = synchronized(lock) {
            store.getOrPut(roomId) { FakeData.chatMessages.toMutableList() }.toList()
        }
        val filtered = if (before.isNullOrBlank()) all else all.takeWhile { it.id != before }
        return filtered.takeLast(size)
    }

    override fun observeMessages(roomId: String): Flow<ChatMessage> =
        incoming.filter { it.roomId == roomId }

    /**
     * In-memory send only. Mock builds never open a live WebSocket.
     */
    override suspend fun send(roomId: String, content: String): ChatMessage {
        val role = sessionRepository.current()?.user?.role ?: Role.PARENT
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val msg = ChatMessage(
            id = "fake-${System.currentTimeMillis()}",
            roomId = roomId,
            fromRole = role,
            text = content,
            sentAt = time,
        )
        synchronized(lock) {
            store.getOrPut(roomId) { mutableListOf() }.add(msg)
        }
        incoming.emit(msg)
        return msg
    }

    override suspend fun markRead(roomId: String, lastMessageId: String?) = Unit

    private fun roomOf(id: String, title: String, online: Boolean): ChatRoom {
        val last = store[id]?.lastOrNull()
        return ChatRoom(
            id = id,
            title = title,
            lastMessage = last?.text ?: if (id == "room-jia") "숙제 다 했어요" else "",
            lastAt = last?.sentAt ?: if (id == "room-jia") "어제" else "",
            online = online,
        )
    }
}

class FakePaymentRepository : PaymentRepository {
    override suspend fun checkout(amount: Int, method: String, productId: Int) =
        Payment("pay-new", "충전", amount, "오늘", "in")
    override suspend fun history() = FakeData.payments
}

/** 구독·충전 포인트 지갑(부모 PayScreen 용). 용돈 지갑은 [FakeAllowanceRepository] 가 따로 관리한다. */
class FakePointRepository : PointRepository {
    private var current = FakeData.pointBalance.balance
    private val log = mutableListOf(
        PointHistory("ph1", "충전", 50_000, "08.15"),
        PointHistory("ph2", "구독 결제", -9_900, "08.01"),
    )

    override suspend fun balance() = PointBalance(current)

    override suspend fun history() = log.toList()

    override suspend fun use(productId: Int, point: Int) {
        require(point in 1..current) { "포인트가 부족해요" }
        current -= point
        log.add(0, PointHistory("ph-" + System.currentTimeMillis(), "포인트 사용", -point, "오늘"))
    }
}

class FakePhotoRepository : PhotoRepository {
    override suspend fun upload(
        childId: String,
        file: File,
        capturedAt: String,
        requestId: String?,
    ): ChildPhoto {
        delay(300)
        // 부모 화면(캡처 내역)에서도 보이도록 같은 저장소에 넣는다.
        val saved = FakeCapturedMedia.add(
            Screenshot(
                id = "photo-" + System.currentTimeMillis(),
                capturedAt = SimpleDateFormat("MM.dd HH:mm", Locale.getDefault()).format(Date()),
                description = "카메라 촬영",
                trigger = "CAMERA",
                imageUrl = file.toURI().toString(),
            ),
        )
        return ChildPhoto(id = saved.id, capturedAt = saved.capturedAt, imageUrl = saved.imageUrl)
    }
}

/**
 * 목 모드용 요청 채널.
 *
 * 서버가 없어도 캡처/촬영 플로우 전체를 손으로 돌려 볼 수 있게 [emit] 을 열어 뒀다.
 * `ChildServicesScreen` 의 「요청 시뮬레이션」 버튼이 이걸 호출한다.
 */
class FakeCommandRepository : CommandRepository {
    private val stream = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 8)
    private val queue = mutableListOf<RemoteCommand>()

    override fun observe(): Flow<RemoteCommand> = stream.asSharedFlow()

    /** 목 모드는 소켓 없이 같은 프로세스에서 바로 전달하므로 항상 연결된 것으로 본다. */
    override val connected: Flow<Boolean> = flowOf(true)

    override fun disconnect() = Unit

    override suspend fun pending(childId: String): List<RemoteCommand> {
        val snapshot = queue.toList()
        queue.clear()
        return snapshot
    }

    override suspend fun ack(childId: String, requestId: String, status: String, message: String?) = Unit

    fun emit(command: RemoteCommand) {
        if (!stream.tryEmit(command)) queue.add(command)
    }
}

class FakeSubscriptionRepository : SubscriptionRepository {
    override suspend fun me() = Subscription(1, "ACTIVE", "TOSS")
    override suspend fun subscribe(planId: Int, method: String) = Subscription(planId, "ACTIVE", method)
    override suspend fun cancel() = Unit
}

class FakeAppUsageRepository : AppUsageRepository {
    override suspend fun list(childId: String, groupBy: String) = FakeData.usages
    override suspend fun report(childId: String, usages: List<AppUsage>) = Unit
}

class FakeAppControlRepository : AppControlRepository {
    private val items = mutableListOf(
        AppControlItem("YouTube", "▶️", "동영상", false, "1시간"),
        AppControlItem("TikTok", "🎵", "동영상", true, "차단"),
        AppControlItem("카카오톡", "💬", "메신저", false, "무제한"),
        AppControlItem("Instagram", "📸", "SNS", true, "차단"),
        AppControlItem("Chrome", "🌐", "브라우저", false, "2시간"),
        AppControlItem("게임", "🎮", "게임", true, "차단"),
    )
    override suspend fun items(childId: String) = items.toList()
}

/**
 * 목 모드 용돈 지갑.
 *
 * Koin `single` 이라 한 프로세스 안에서 부모 계정과 자녀 계정이 **같은 지갑**을 본다.
 * 덕분에 서버 없이도 「부모로 로그인 → 용돈 보내기 → 자녀로 로그인 → QR 결제」가 그대로 재현된다.
 *
 * 목 데이터의 부모용 childId(`child-minjun`)와 자녀 세션의 userId(`user-child`)가 서로 달라서
 * childId 로 지갑을 나누면 두 역할이 다른 잔액을 보게 된다. 그래서 여기서는 **지갑 하나만** 쓴다.
 * 실서버 구현([RealAllowanceRepository])은 childId 별로 정상 분리된다.
 */
class FakeAllowanceRepository : AllowanceRepository {
    private val lock = Any()
    private var balance = SEED_BALANCE
    private val ledger = mutableListOf(
        AllowanceEntry("seed-2", AllowanceType.PAYMENT, -1_500, "문구점 결제", "08.20"),
        AllowanceEntry("seed-1", AllowanceType.SEND, SEED_BALANCE + 1_500, "엄마가 보낸 용돈", "08.15"),
    )

    override suspend fun balance(childId: String): Allowance =
        synchronized(lock) { Allowance(balance) }

    override suspend fun history(childId: String): List<AllowanceEntry> =
        synchronized(lock) { ledger.toList() }

    override suspend fun send(childId: String, amount: Int, memo: String?): Allowance {
        require(amount > 0) { "금액을 확인해 주세요" }
        return synchronized(lock) {
            balance += amount
            ledger.add(0, entry(AllowanceType.SEND, amount, memo ?: "엄마가 보낸 용돈"))
            Allowance(balance)
        }
    }

    override suspend fun withdraw(childId: String, amount: Int, memo: String?): Allowance {
        require(amount > 0) { "금액을 확인해 주세요" }
        return synchronized(lock) {
            require(amount <= balance) { "회수할 수 있는 금액을 넘었어요" }
            balance -= amount
            ledger.add(0, entry(AllowanceType.WITHDRAW, -amount, memo ?: "부모님이 회수"))
            Allowance(balance)
        }
    }

    override suspend fun pay(
        childId: String,
        token: String,
        amount: Int,
        merchant: String?,
    ): AllowancePayment {
        delay(400)
        return synchronized(lock) {
            require(amount in 1..balance) { "잔액이 부족해요" }
            balance -= amount
            ledger.add(0, entry(AllowanceType.PAYMENT, -amount, merchant ?: "QR 결제"))
            AllowancePayment(
                id = "apay-" + System.currentTimeMillis(),
                amount = amount,
                balanceAfter = balance,
                approvedAt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                merchant = merchant,
            )
        }
    }

    private fun entry(type: AllowanceType, amount: Int, description: String) = AllowanceEntry(
        id = "al-" + System.currentTimeMillis(),
        type = type,
        amount = amount,
        description = description,
        dateLabel = SimpleDateFormat("MM.dd HH:mm", Locale.getDefault()).format(Date()),
    )

    private companion object {
        const val SEED_BALANCE = 12_000
    }
}
