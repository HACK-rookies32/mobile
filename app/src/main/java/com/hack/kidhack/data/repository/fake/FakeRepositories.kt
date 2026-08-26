package com.hack.kidhack.data.repository.fake

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.hack.kidhack.data.remote.PageResponse
import com.hack.kidhack.data.remote.paging.PageResponsePagingSource
import com.hack.kidhack.domain.model.AppControlItem
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.model.BlocklistItem
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.LocationPoint
import com.hack.kidhack.domain.model.NotificationSettings
import com.hack.kidhack.domain.model.PairingCode
import com.hack.kidhack.domain.model.Payment
import com.hack.kidhack.domain.model.PointBalance
import com.hack.kidhack.domain.model.PointHistory
import com.hack.kidhack.domain.model.Post
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Screenshot
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.model.Subscription
import com.hack.kidhack.domain.model.User
import com.hack.kidhack.domain.repository.AppControlRepository
import com.hack.kidhack.domain.repository.AppUsageRepository
import com.hack.kidhack.domain.repository.AuthRepository
import com.hack.kidhack.domain.repository.BlocklistRepository
import com.hack.kidhack.domain.repository.ChatRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.CommunityRepository
import com.hack.kidhack.domain.repository.GeofenceRepository
import com.hack.kidhack.domain.repository.LocationRepository
import com.hack.kidhack.domain.repository.NotificationRepository
import com.hack.kidhack.domain.repository.PairingRepository
import com.hack.kidhack.domain.repository.PaymentRepository
import com.hack.kidhack.domain.repository.PointRepository
import com.hack.kidhack.domain.repository.ScreenshotRepository
import com.hack.kidhack.domain.repository.SessionEvents
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.SubscriptionRepository
import com.hack.kidhack.domain.repository.UserRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FakeAuthRepository(
    private val sessionRepository: SessionRepository,
    private val sessionEvents: SessionEvents,
) : AuthRepository {
    override suspend fun signup(
        loginId: String,
        username: String,
        password: String,
        nickname: String?,
        email: String?,
        phoneNumber: String,
        birthDate: String,
        address: String?,
        profileImageUrl: String?,
        role: Role,
        parentId: Long?,
    ): Session {
        delay(300)
        require(loginId.isNotBlank() && password.isNotBlank()) { "아이디와 비밀번호를 입력해 주세요" }
        require(username.isNotBlank()) { "실명을 입력해 주세요" }
        require(phoneNumber.isNotBlank()) { "휴대폰 번호를 입력해 주세요" }
        require(birthDate.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) { "생년월일은 yyyy-MM-dd 형식이어야 해요" }
        if (role != Role.CHILD) {
            require(!email.isNullOrBlank()) { "이메일을 입력해 주세요" }
        }
        if (role == Role.CHILD) {
            require(parentId != null && parentId > 0) { "연결할 부모님 회원 번호를 입력해 주세요" }
        }
        val user = User(
            id = "user-$loginId",
            loginId = loginId,
            name = username,
            role = role,
            phone = phoneNumber,
            email = email,
            profileImageUrl = profileImageUrl,
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

class FakeUserRepository : UserRepository {
    private var settings = NotificationSettings(true, true, false, true)
    override suspend fun me(): User = FakeData.parent
    override suspend fun updateMe(name: String?, phone: String?, email: String?): User =
        FakeData.parent.copy(name = name ?: FakeData.parent.name, phone = phone ?: FakeData.parent.phone, email = email ?: FakeData.parent.email)
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

class FakeScreenshotRepository : ScreenshotRepository {
    private val items = listOf(
        Screenshot("s1", "오늘 14:22", "YouTube 시청 중", "MANUAL"),
        Screenshot("s2", "오늘 11:05", "카카오톡", "MANUAL"),
        Screenshot("s3", "어제 18:40", "Chrome", "SCHEDULED"),
    )
    override fun paged(childId: String): Flow<PagingData<Screenshot>> = Pager(PagingConfig(20)) {
        PageResponsePagingSource { page, size -> PageResponse(items, page, size, items.size.toLong(), 1) }
    }.flow
    override suspend fun list(childId: String) = items
    override suspend fun requestCapture(childId: String) = Unit
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

    override suspend fun rooms(): List<ChatRoom> = synchronized(lock) {
        listOf(
            roomOf("room-minjun", "김민준", true),
            roomOf("room-jia", "이지아", false),
        )
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

class FakePointRepository : PointRepository {
    override suspend fun balance() = FakeData.pointBalance
    override suspend fun history() = listOf(
        PointHistory("ph1", "충전", 50_000, "08.15"),
        PointHistory("ph2", "구독 결제", -9_900, "08.01"),
    )
    override suspend fun use(productId: Int, point: Int) = Unit
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
