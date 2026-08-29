package com.hack.kidhack.data.repository.fake

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.hack.kidhack.data.local.CapturedShotStore
import com.hack.kidhack.data.local.ChatMessageStore
import com.hack.kidhack.data.remote.PageResponse
import com.hack.kidhack.data.remote.dto.TransferResponse
import com.hack.kidhack.data.remote.paging.PageResponsePagingSource
import com.hack.kidhack.data.ws.ChatWebSocketClient
import com.hack.kidhack.domain.model.Allowance
import com.hack.kidhack.domain.model.AllowanceEntry
import com.hack.kidhack.domain.model.AllowancePayment
import com.hack.kidhack.domain.model.AllowanceType
import com.hack.kidhack.domain.model.Activity
import com.hack.kidhack.domain.model.ActivityType
import com.hack.kidhack.domain.model.AppControlItem
import com.hack.kidhack.data.local.BlocklistStore
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.ChildAlertEvent
import com.hack.kidhack.domain.model.childAlertTypeToParent
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.model.AppUsageStat
import com.hack.kidhack.domain.model.BlocklistItem
import com.hack.kidhack.domain.model.Card
import com.hack.kidhack.domain.model.Charge
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.model.ChildPhoto
import com.hack.kidhack.domain.model.ChildDashboard
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.model.Comment
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.Inquiry
import com.hack.kidhack.domain.model.InquiryAnswer
import com.hack.kidhack.domain.model.LocationEvent
import com.hack.kidhack.domain.model.LocationPoint
import com.hack.kidhack.domain.model.NotificationSettings
import com.hack.kidhack.domain.model.PairingCode
import com.hack.kidhack.domain.model.Payment
import com.hack.kidhack.domain.model.PointBalance
import com.hack.kidhack.domain.model.PointHistory
import com.hack.kidhack.domain.model.Post
import com.hack.kidhack.domain.model.RemoteCommand
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Screenshot
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.model.SentCode
import com.hack.kidhack.domain.model.TransferResult
import com.hack.kidhack.domain.model.User
import com.hack.kidhack.domain.repository.AllowanceRepository
import com.hack.kidhack.domain.model.Wallet
import com.hack.kidhack.domain.model.WalletTransaction
import com.hack.kidhack.domain.repository.AppControlRepository
import com.hack.kidhack.domain.repository.AppUsageRepository
import com.hack.kidhack.domain.repository.AuthRepository
import com.hack.kidhack.domain.repository.BlocklistRepository
import com.hack.kidhack.domain.repository.CardRepository
import com.hack.kidhack.domain.repository.ChatRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.CommandRepository
import com.hack.kidhack.domain.repository.CommunityRepository
import com.hack.kidhack.domain.repository.InquiryRepository
import com.hack.kidhack.domain.repository.LocationEventRepository
import com.hack.kidhack.domain.repository.DashboardRepository
import com.hack.kidhack.domain.repository.DeviceRepository
import com.hack.kidhack.domain.repository.LocationRepository
import com.hack.kidhack.domain.repository.ChildAlertRepository
import com.hack.kidhack.domain.repository.NotificationRepository
import com.hack.kidhack.domain.repository.PairingRepository
import com.hack.kidhack.domain.repository.PaymentRepository
import com.hack.kidhack.domain.repository.PhotoRepository
import com.hack.kidhack.domain.repository.PlaceRepository
import com.hack.kidhack.domain.repository.PointRepository
import com.hack.kidhack.domain.repository.ScreenshotRepository
import com.hack.kidhack.domain.repository.SessionEvents
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.UserRepository
import com.hack.kidhack.domain.repository.WalletRepository
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
    private val chatMessageStore: ChatMessageStore,
    private val chatWebSocketClient: ChatWebSocketClient,
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
        require(username.isNotBlank()) { "이름을 입력해 주세요" }
        require(!nickname.isNullOrBlank()) { "닉네임을 입력해 주세요" }
        require(phoneNumber.isNotBlank()) { "휴대폰 번호를 입력해 주세요" }
        require(birthDate.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) { "생년월일은 yyyy-MM-dd 형식이어야 해요" }
        if (role != Role.CHILD) {
            require(!email.isNullOrBlank()) { "이메일을 입력해 주세요" }
        }
        // 자녀는 가입 시 부모를 지정하지 않는다. 로그인 후 페어링 코드로 연결한다.
        // 채팅 등 API가 member id를 Long으로 쓰므로 목업도 숫자 id를 쓴다.
        val numericId = when (role) {
            Role.CHILD -> if (loginId.equals("minjun", ignoreCase = true)) "101" else "102"
            Role.PARENT -> "1"
        }
        val user = User(
            id = numericId,
            loginId = loginId,
            name = username,
            role = role,
            nickname = nickname,
            phone = phoneNumber,
            email = email,
            profileImageUrl = profileImageUrl,
            parentId = parentId.takeIf { role == Role.CHILD },
        )

        val session = FakeData.sessionFor(user)
        sessionRepository.save(session)
        return session
    }

    /**
     * 목업 로그인.
     *
     * 예전에는 아이디가 정확히 `minjun` 일 때만 자녀로 들어갔다. 그래서 로그인 화면에서
     * 「자녀」를 골라도 다른 아이디를 넣으면 부모 계정으로 로그인됐다.
     * 실서버가 role 을 보고 판단하듯, 목업은 화면에서 고른 역할을 그대로 따른다.
     */
    override suspend fun login(loginId: String, password: String, preferredRole: Role?): Session {
        delay(300)
        val role = preferredRole
            ?: if (loginId.equals(FakeData.childUser.loginId, ignoreCase = true)) Role.CHILD else Role.PARENT
        val user = if (role == Role.CHILD) FakeData.childUser else FakeData.parent
        val session = FakeData.sessionFor(user.copy(loginId = loginId.ifBlank { user.loginId }))
        sessionRepository.save(session)
        return session
    }

    override suspend fun logout() {
        sessionRepository.clear()
        chatMessageStore.clear()
        chatWebSocketClient.reset()
        sessionEvents.emitLogout()
    }

    /** 목업은 서버처럼 6자리 코드를 응답에 실어 준다. 검증은 [FAKE_CODE] 로만 통과한다. */
    override suspend fun sendFindIdCode(username: String, email: String): SentCode {
        delay(300)
        require(username.isNotBlank()) { "이름을 입력해 주세요" }
        require(email.contains("@")) { "이메일 형식이 올바르지 않아요" }
        return SentCode(message = "인증코드가 발송되었습니다.", code = FAKE_CODE)
    }

    override suspend fun verifyFindId(email: String, code: String): String {
        delay(300)
        require(code.trim() == FAKE_CODE) { "인증코드가 올바르지 않습니다." }
        return "parent01"
    }

    override suspend fun sendResetPasswordCode(loginId: String, email: String): SentCode {
        delay(300)
        require(loginId.isNotBlank()) { "아이디를 입력해 주세요" }
        require(email.contains("@")) { "이메일 형식이 올바르지 않아요" }
        return SentCode(message = "인증코드가 발송되었습니다.", code = FAKE_CODE)
    }

    override suspend fun resetPassword(email: String, code: String, newPassword: String) {
        delay(300)
        require(code.trim() == FAKE_CODE) { "인증코드가 올바르지 않습니다." }
    }

    private companion object {
        const val FAKE_CODE = "123456"
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
    private var settings = NotificationSettings(location = true, harmfulAccess = true, appUsage = true, notice = true)

    override suspend fun me(): User =
        sessionRepository.current()?.user ?: FakeData.parent

    override suspend fun updateMe(
        nickname: String?,
        phoneNumber: String?,
        email: String?,
        address: String?,
        profileImageUrl: String?,
    ): User {
        delay(200)
        val current = me()
        // 서버가 실명(username)은 수정 대상으로 받지 않으므로 목업도 바꾸지 않는다.
        return current.copy(
            nickname = nickname ?: current.nickname,
            phone = phoneNumber ?: current.phone,
            email = email,
            profileImageUrl = profileImageUrl ?: current.profileImageUrl,
        )
    }

    override suspend fun deleteMe() = Unit

    override fun pagedActivities(type: ActivityType): Flow<PagingData<Activity>> =
        flowOf(PagingData.from(FakeData.activities.filter { it.type == type }))
    override suspend fun notificationSettings(): NotificationSettings = settings
    override suspend fun patchNotificationSettings(settings: NotificationSettings): NotificationSettings {
        this.settings = settings
        return settings
    }
}

class FakePairingRepository : PairingRepository {
    // 서버는 연결 성공 시 코드를 지운다. 목업도 같은 관찰 가능한 동작을 흉내 내야
    // 「코드가 아직 살아 있으면 아무도 안 쓴 것」이라는 판정을 화면에서 검증할 수 있다.
    private val active = mutableListOf<PairingCode>()

    override suspend fun issueCode(): PairingCode =
        FakeData.pairing.also { active += it }

    override suspend fun activeCodes(): List<PairingCode> = active.toList()

    override suspend fun connect(pairingCode: String) {
        active.removeAll { it.pairingCode == pairingCode }
    }
}

class FakeChildRepository : ChildRepository {
    /** 목업에서 해제된 자녀. 목록에서 빠져야 화면이 결과를 확인할 수 있다. */
    private val removed = mutableSetOf<String>()

    override suspend fun children(): List<ChildProfile> =
        FakeData.children.filterNot { it.id in removed }
    override suspend fun child(childId: String): ChildProfile =
        FakeData.children.first { it.id == childId }
    override suspend fun updateChild(
        childId: String,
        name: String?,
        alias: String?,
        profileImageUrl: String?,
        birthDate: String?,
    ): ChildProfile = child(childId).copy(
        name = name ?: child(childId).name,
        alias = alias ?: child(childId).alias,
        profileImageUrl = profileImageUrl ?: child(childId).profileImageUrl,
        birthDate = birthDate ?: child(childId).birthDate,
    )
    override suspend fun unpair(childId: String) {
        delay(200)
        removed += childId
    }

    override suspend fun forceLogout(childId: String) {
        delay(200)
    }
}


class FakeLocationRepository : LocationRepository {
    private val historyMap = mutableMapOf<String, MutableList<LocationPoint>>()
    private val realtimeMap = mutableMapOf<String, Boolean>()

    override suspend fun track(childId: String, latitude: String, longitude: String): LocationPoint {
        val stamp = "2026-08-25T17:10:00"
        val point = LocationPoint(
            lat = latitude,
            lng = longitude,
            recordedAt = stamp,
            segmentStart = "2026-08-25T17:05:00",
            segmentEnd = stamp,
            updatedAt = stamp,
        )
        historyMap.getOrPut(childId) { FakeData.locationSegments.toMutableList() }.add(0, point)
        return point
    }

    override suspend fun history(childId: String): List<LocationPoint> =
        historyMap[childId] ?: FakeData.locationSegments

    override suspend fun toggleRealtime(childId: String, enabled: Boolean): Boolean {
        realtimeMap[childId] = enabled
        return enabled
    }
}

class FakeDeviceRepository : DeviceRepository {
    override suspend fun reportEvent(childId: String, type: String) {}
    override suspend fun registerPushToken(token: String) = Unit
    override suspend fun unregisterPushToken(token: String) = Unit
}

class FakeDashboardRepository(
    private val locationRepository: LocationRepository,
    private val notificationRepository: NotificationRepository,
    private val placeRepository: PlaceRepository,
) : DashboardRepository {
    override suspend fun get(childId: String): ChildDashboard = ChildDashboard(
        childId = childId,
        currentLocation = locationRepository.history(childId).firstOrNull(),
        realtimeEnabled = false,
        recentNotifications = notificationRepository.list(childId).take(3),
        blockRulePatterns = emptyList(),
        places = placeRepository.list(childId),
    )
}

class FakePlaceRepository : PlaceRepository {
    private val items = FakeData.places.toMutableList()
    override suspend fun list(childId: String): List<Geofence> = items.toList()
    override suspend fun create(childId: String, place: Geofence): Geofence {
        val created = place.copy(id = "${items.size + 1}")
        items.add(created)
        return created
    }
    override suspend fun delete(childId: String, placeId: String) {
        items.removeAll { it.id == placeId }
    }
}

class FakeLocationEventRepository : LocationEventRepository {
    override suspend fun events(childId: String): List<LocationEvent> = FakeData.locationEvents
}

class FakeChildAlertRepository(private val feed: MockNotificationFeed) : ChildAlertRepository {
    override suspend fun upload(childId: String, alerts: List<ChildAlertEvent>) {
        alerts.forEach { alert ->
            val parentType = childAlertTypeToParent(alert.type) ?: return@forEach
            feed.add(
                type = parentType,
                title = alert.title,
                body = alert.body.ifBlank { alert.title },
                dedupeKey = alert.id,
                childId = FakeData.loggedInChildProfileId,
            )
        }
    }

    override suspend fun list(childId: String): List<ChildAlertEvent> = emptyList()
}

class FakeNotificationRepository(private val feed: MockNotificationFeed) : NotificationRepository {
    override suspend fun list(recipientId: String, childId: String?): List<AppNotification> =
        (feed.all() + SAMPLES)
            .filter { childId == null || it.childId == null || it.childId == childId }

    override suspend fun markRead(notificationId: String) {}

    private companion object {
        val SAMPLES = listOf(
            AppNotification("n1", "LOCATION", "민준이가 '학교' 구역을 벗어났습니다.", "14:20", false, FakeData.minjun.id),
            AppNotification("n2", "HARMFUL", "지아의 부적절한 사이트 접속 시도가 차단되었습니다.", "12:05", true, FakeData.jia.id),
        )
    }
}
/**
 * 목업 블랙/화이트리스트.
 *
 * 예전에는 목록을 메모리에만 들고 있어서 앱을 다시 켜면 부모가 바꾼 차단 설정이 통째로
 * 초기값으로 돌아갔고, `remove` 가 빈 함수라 카테고리 토글을 꺼도 아무 일도 일어나지 않았다.
 *
 * id 는 인덱스가 아니라 **키워드 그 자체**다. 인덱스로 만들면 목록이 바뀔 때마다 id 가 밀려서
 * `remove(itemId)` 가 엉뚱한 항목을 지운다. 실구현체도 같은 규칙을 쓴다.
 */
class FakeBlocklistRepository(
    private val store: BlocklistStore,
    private val feed: MockNotificationFeed,
) : BlocklistRepository {
    override suspend fun get(childId: String): List<BlocklistItem> =
        store.blacklist().map { BlocklistItem(it, "BLACK", it) } +
            store.whitelist().map { BlocklistItem(it, "WHITE", it) }
    override suspend fun add(childId: String, listType: String, keyword: String) {
        store.add(listType, keyword)
    }
    override suspend fun remove(childId: String, itemId: String) {
        store.remove(itemId)
    }
    override suspend fun reportHarmfulAccess(matchedText: String, accessedAt: String) {
        feed.add(
            type = "HARMFUL",
            title = "유해 사이트 접속 차단",
            body = matchedText + " 이(가) 포함된 사이트 접속 시도가 차단되었어요.",
            dedupeKey = "HARMFUL-" + matchedText,
            childId = FakeData.loggedInChildProfileId,
        )
    }
}

class FakeScreenshotRepository(
    private val store: CapturedShotStore,
    private val commandRepository: CommandRepository,
) : ScreenshotRepository {
    override fun paged(childId: String): Flow<PagingData<Screenshot>> = Pager(PagingConfig(20)) {
        PageResponsePagingSource { page, size ->
            val all = store.list()
            val totalPages = kotlin.math.ceil(all.size / size.toDouble()).toInt().coerceAtLeast(1)
            val slice = all.drop(page * size).take(size)
            PageResponse(slice, page, size, all.size.toLong(), totalPages)
        }
    }.flow

    override suspend fun list(childId: String) = store.list()

    /**
     * 실서버라면 202 후 서버가 자녀 소켓으로 명령을 민다. 목업은 같은 프로세스의
     * 명령 채널로 직접 밀어서, 자녀 계정으로 전환했을 때 요청 푸시가 뜨게 한다.
     */
    override suspend fun requestCapture(childId: String) {
        (commandRepository as? FakeCommandRepository)?.emit(
            RemoteCommand(
                requestId = "mock-cap-" + System.currentTimeMillis(),
                type = RemoteCommandType.SCREEN_CAPTURE,
                childId = childId,
                requestedBy = FakeData.parent.name,
            ),
        )
    }

    override suspend fun upload(
        childId: String,
        file: File,
        capturedAt: String,
        trigger: String,
    ): Screenshot = store.add(
        source = file,
        description = "화면 캡처",
        trigger = trigger,
    )
}

class FakeCommunityRepository : CommunityRepository {
    private val items = FakeData.posts.toMutableList()
    override fun pagedPosts(keyword: String?): Flow<PagingData<Post>> = Pager(PagingConfig(20)) {
        PageResponsePagingSource { page, size ->
            val all = if (keyword.isNullOrBlank()) items.toList()
                else items.filter { it.title.contains(keyword, ignoreCase = true) }
            val totalPages = kotlin.math.ceil(all.size / size.toDouble()).toInt().coerceAtLeast(1)
            val slice = all.drop(page * size).take(size)
            PageResponse(slice, page, size, all.size.toLong(), totalPages)
        }
    }.flow
    override suspend fun posts() = items.toList()
    override suspend fun post(postId: String) = items.first { it.id == postId }
    override suspend fun create(
        title: String,
        body: String,
        imageUrls: List<String>?,
        links: List<String>?
    ): Post {
        val created = Post(
            id = "p-${items.size + 1}",
            title = title,
            author = FakeData.parent.nickname ?: FakeData.parent.name,
            commentCount = 0,
            likeCount = 0,
            createdAtLabel = "방금",
            imageUrls = imageUrls ?: emptyList(),
            links = links ?: emptyList()
        )
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
        if (i >= 0) {
            val p = items[i]
            val newComment = com.hack.kidhack.domain.model.Comment(
                id = "c-${System.currentTimeMillis()}",
                author = FakeData.parent.nickname ?: FakeData.parent.name,
                text = body,
                createdAt = "방금"
            )
            items[i] = p.copy(
                commentCount = p.commentCount + 1,
                comments = p.comments + newComment
            )
        }
    }
    override suspend fun reportPost(postId: String, reason: String, detail: String?) {
        // 모의(mock): 신고 접수만 처리하고 별도 저장은 하지 않음
    }
    override suspend fun reportComment(postId: String, commentId: String, reason: String, detail: String?) {
        // 모의(mock): 신고 접수만 처리하고 별도 저장은 하지 않음
    }
}

class FakeInquiryRepository : InquiryRepository {
    private val items = mutableListOf(
        Inquiry(
            id = "i1",
            title = "페어링 코드가 안 나와요",
            body = "부모 앱에서 버튼을 눌러도 코드가 생성되지 않습니다.",
            status = "ANSWERED",
            createdAt = "2024.08.20",
            answers = listOf(
                InquiryAnswer("a1", "관리자", "안녕하세요. 앱을 최신 버전으로 업데이트하신 후 다시 시도해 주세요.", "2024.08.21")
            )
        ),
        Inquiry(
            id = "i2",
            title = "위치 추적 간격 설정 문의",
            body = "위치 추적 간격을 10분보다 짧게 설정할 수 있나요?",
            status = "PENDING",
            createdAt = "2024.08.24"
        )
    )

    override suspend fun list(): List<Inquiry> = items.toList()

    override suspend fun create(
        title: String,
        body: String,
        imageUrls: List<String>?,
        links: List<String>?
    ): Inquiry {
        val created = Inquiry(
            id = "i-${items.size + 1}",
            title = title,
            body = body,
            status = "PENDING",
            createdAt = "방금",
            imageUrls = imageUrls ?: emptyList(),
            links = links ?: emptyList()
        )
        items.add(0, created)
        return created
    }

    override suspend fun get(id: String): Inquiry = items.first { it.id == id }
}

class FakeWalletRepository : WalletRepository {
    private var balance = 45_000
    override suspend fun getMyWallet(): Wallet = Wallet("w-parent", balance, "방금")
    override suspend fun getTransactions(type: String?, page: Int): List<WalletTransaction> = listOf(
        WalletTransaction("tx1", "CHARGE", 50000, 50000, null, "08.15"),
        WalletTransaction("tx2", "TRANSFER_OUT", -5000, 45000, "101", "08.20")
    )
    override suspend fun transferToChild(childId: String, amount: Int, memo: String?): TransferResult {
        balance -= amount
        return TransferResult(balance, 10000 + amount, "tx-${System.currentTimeMillis()}")
    }
    override suspend fun reclaimFromChild(childId: String, amount: Int): TransferResult {
        balance += amount
        return TransferResult(balance, 10000 - amount, "tx-${System.currentTimeMillis()}")
    }
    override suspend fun getChildWallet(childId: String): Wallet = Wallet("w-child", 10000, "방금")
    override suspend fun prepareCharge(amount: Int, method: String, cardId: String): Charge = Charge("c1", amount, "PENDING", "key1", method)
    override suspend fun confirmCharge(chargeId: String, paymentKey: String, amount: Int): Wallet {
        balance += amount
        return Wallet("w-parent", balance, "방금")
    }
}

class FakeCardRepository : CardRepository {
    private val items = mutableListOf(
        Card("card1", "1234", "12/28", "김지영"),
        Card("card2", "5678", "05/26", "김지영")
    )
    override suspend fun register(
        cardNumber: String,
        expiry: String,
        cvc: String,
        cardPassword2: String,
        birthDate: String,
        ownerName: String
    ): Card {
        val new = Card("card-${items.size + 1}", cardNumber.takeLast(4), expiry, ownerName)
        items.add(new)
        return new
    }
    override suspend fun list(): List<Card> = items.toList()
    override suspend fun delete(cardId: String) { items.removeAll { it.cardId == cardId } }
}

class FakeChatRepository(
    private val sessionRepository: SessionRepository,
) : ChatRepository {
    private val store = mutableMapOf(
        "room-minjun" to FakeData.chatMessages.toMutableList(),
        "room-jia" to mutableListOf<ChatMessage>(),
    )
    private val incoming = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)

    override suspend fun getOrCreateRoom(parentId: Long, childId: Long): ChatRoom {
        return when (childId) {
            102L -> ChatRoom(
                id = "room-jia",
                title = "이지아",
                lastMessage = "아직 대화가 없어요",
                lastAt = "",
                online = false,
                parentId = parentId,
                childId = childId,
            )
            else -> ChatRoom(
                id = "room-minjun",
                title = "김민준",
                lastMessage = "학원 끝나요",
                lastAt = "14:32",
                online = true,
                parentId = parentId,
                childId = childId,
            )
        }
    }

    /** 방 제목은 보는 쪽 기준으로 바뀐다 — 부모는 자녀 이름을, 자녀는 보호자 이름을 본다. */
    override suspend fun rooms(): List<ChatRoom> = listOf(
        ChatRoom("room-minjun", "김민준", "학원 끝나요", "14:32", true, parentId = 1L, childId = 101L),
        ChatRoom("room-jia", "이지아", "아직 대화가 없어요", "", false, parentId = 1L, childId = 102L),
    )

    override suspend fun messages(roomId: String, before: String?, size: Int): List<ChatMessage> =
        store[roomId] ?: emptyList()

    override fun observeMessages(roomId: String): Flow<ChatMessage> = incoming.asSharedFlow()

    override suspend fun send(roomId: String, content: String): ChatMessage {
        val role = sessionRepository.current()?.user?.role ?: Role.PARENT
        val msg = ChatMessage("m-${System.currentTimeMillis()}", roomId, role, content, "방금")
        store.getOrPut(roomId) { mutableListOf() }.add(msg)
        incoming.emit(msg)
        return msg
    }

    override suspend fun markRead(roomId: String, lastMessageId: String?) {}
}

class FakePaymentRepository : PaymentRepository {
    override suspend fun checkout(amount: Int, method: String, productId: Int): Payment =
        Payment("pay-${System.currentTimeMillis()}", "상품 구매", -amount, "방금", "out")
    override suspend fun history(): List<Payment> = FakeData.payments
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

/**
 * 목업 카메라 촬영 저장소.
 *
 * 예전에는 촬영본을 캡처 저장소([CapturedShotStore])에 섞어 넣어 부모 「화면 캡처」
 * 목록에 끼워 보여 줬다. 부모 「카메라 촬영」 화면이 생겼으니 전용 피드로 분리한다.
 * 피드는 계정 전환에도 지워지지 않아 목업 서버 역할을 한다.
 */
class FakePhotoRepository(
    private val feed: MockPhotoFeed,
    private val commandRepository: CommandRepository,
) : PhotoRepository {
    override suspend fun upload(
        childId: String,
        file: File,
        capturedAt: String,
        requestId: String?,
    ): ChildPhoto {
        delay(300)
        return feed.add(file, requestId)
    }

    override suspend fun list(childId: String): List<ChildPhoto> = feed.list()

    override suspend fun requestShot(childId: String) {
        (commandRepository as? FakeCommandRepository)?.emit(
            RemoteCommand(
                requestId = "mock-cam-" + System.currentTimeMillis(),
                type = RemoteCommandType.CAMERA_SHOT,
                childId = childId,
                requestedBy = FakeData.parent.name,
            ),
        )
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

    override fun observe(): Flow<RemoteCommand> = stream.asSharedFlow()

    /** 목 모드는 소켓 없이 같은 프로세스에서 바로 전달하므로 항상 연결된 것으로 본다. */
    override val connected: Flow<Boolean> = flowOf(true)

    override fun disconnect() = Unit

    override suspend fun ack(childId: String, requestId: String, status: String, message: String?) = Unit

    fun emit(command: RemoteCommand) {
        stream.tryEmit(command)
    }
}

class FakeAppUsageRepository(private val feed: MockNotificationFeed) : AppUsageRepository {
    override suspend fun list(
        childId: String,
        groupBy: String,
        from: String?,
        to: String?,
    ): List<AppUsageStat> = when (groupBy) {
        "HOUR" -> FakeData.usagesByHour
        "DAY" -> FakeData.usagesByDay
        else -> FakeData.usages.map { AppUsageStat(it.appName, it.durationSec) }
    }
    override suspend fun report(childId: String, usages: List<AppUsage>) {
        val totalMinutes = usages.sumOf { it.durationSec } / 60
        if (totalMinutes < DAILY_LIMIT_MINUTES) return
        feed.addOncePerDay(
            type = "APP_USAGE",
            title = "앱 사용 시간 한도 도달",
            body = "오늘 사용 시간이 " + DAILY_LIMIT_MINUTES / 60 + "시간 한도에 도달했어요.",
            childId = childId,
        )
    }

    private companion object {
        const val DAILY_LIMIT_MINUTES = 180
    }
}

class FakeAppControlRepository : AppControlRepository {
    private val db = mutableListOf(
        com.hack.kidhack.domain.model.AppControlItem("com.youtube", "YouTube", "📺", "동영상", false, "1시간 30분", 90),
        com.hack.kidhack.domain.model.AppControlItem("com.tiktok", "TikTok", "🎵", "소셜", true, "차단"),
        com.hack.kidhack.domain.model.AppControlItem("com.roblox", "Roblox", "🎮", "게임", false, "무제한")
    )
    override suspend fun items(childId: String): List<com.hack.kidhack.domain.model.AppControlItem> = db.toList()

    override suspend fun getInstalledApps(childId: String): List<com.hack.kidhack.domain.model.InstalledApp> = listOf(
        com.hack.kidhack.domain.model.InstalledApp("com.youtube", "YouTube", "동영상"),
        com.hack.kidhack.domain.model.InstalledApp("com.tiktok", "TikTok", "소셜"),
        com.hack.kidhack.domain.model.InstalledApp("com.instagram", "Instagram", "소셜"),
        com.hack.kidhack.domain.model.InstalledApp("com.roblox", "Roblox", "게임"),
        com.hack.kidhack.domain.model.InstalledApp("com.pubg", "배틀그라운드", "게임")
    )

    override suspend fun updateAppControl(
        childId: String,
        packageName: String,
        blocked: Boolean,
        limitMinutes: Int?
    ): com.hack.kidhack.domain.model.AppControlItem {
        val existing = db.find { it.packageName == packageName }
        val updated = com.hack.kidhack.domain.model.AppControlItem(
            packageName = packageName,
            name = existing?.name ?: "알 수 없는 앱",
            icon = existing?.icon ?: "📱",
            category = existing?.category ?: "기타",
            blocked = blocked,
            limitMinutes = limitMinutes,
            limit = if (blocked) "차단" else if (limitMinutes == null || limitMinutes == 0) "무제한" else "${limitMinutes / 60}시간 ${limitMinutes % 60}분"
        )
        db.removeAll { it.packageName == packageName }
        db.add(updated)
        return updated
    }

    override suspend fun deleteAppControl(childId: String, packageName: String) {
        db.removeAll { it.packageName == packageName }
    }
}

/**
 * 목 모드 용돈 지갑.
 *
 * Koin `single` 이라 한 프로세스 안에서 부모 계정과 자녀 계정이 **같은 지갑**을 본다.
 * 덕분에 서버 없이도 「부모로 로그인 → 용돈 보내기 → 자녀로 로그인 → QR 결제」가 그대로 재현된다.
 *
 * 목 데이터의 부모용 childId와 자녀 세션 userId를 동일하게 `101`로 맞춰
 * 두 역할이 같은 용돈 지갑을 보게 한다.
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
