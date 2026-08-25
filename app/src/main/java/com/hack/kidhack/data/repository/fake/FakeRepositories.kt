package com.hack.kidhack.data.repository.fake

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.hack.kidhack.data.remote.PageResponse
import com.hack.kidhack.data.remote.dto.TransferResponse
import com.hack.kidhack.data.remote.paging.PageResponsePagingSource
import com.hack.kidhack.domain.model.AppControlItem
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.model.BlocklistItem
import com.hack.kidhack.domain.model.Card
import com.hack.kidhack.domain.model.Charge
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.model.Comment
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.Inquiry
import com.hack.kidhack.domain.model.InquiryAnswer
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
import com.hack.kidhack.domain.model.TransferResult
import com.hack.kidhack.domain.model.User
import com.hack.kidhack.domain.model.Wallet
import com.hack.kidhack.domain.model.WalletTransaction
import com.hack.kidhack.domain.repository.AppControlRepository
import com.hack.kidhack.domain.repository.AppUsageRepository
import com.hack.kidhack.domain.repository.AuthRepository
import com.hack.kidhack.domain.repository.BlocklistRepository
import com.hack.kidhack.domain.repository.CardRepository
import com.hack.kidhack.domain.repository.ChatRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.CommunityRepository
import com.hack.kidhack.domain.repository.GeofenceRepository
import com.hack.kidhack.domain.repository.InquiryRepository
import com.hack.kidhack.domain.repository.LocationRepository
import com.hack.kidhack.domain.repository.NotificationRepository
import com.hack.kidhack.domain.repository.PairingRepository
import com.hack.kidhack.domain.repository.PaymentRepository
import com.hack.kidhack.domain.repository.PointRepository
import com.hack.kidhack.domain.repository.ScreenshotRepository
import com.hack.kidhack.domain.repository.SessionRepository
import com.hack.kidhack.domain.repository.SubscriptionRepository
import com.hack.kidhack.domain.repository.UserRepository
import com.hack.kidhack.domain.repository.WalletRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class FakeAuthRepository(
    private val sessionRepository: SessionRepository,
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
        val session = FakeData.sessionFor(
            User(id = "user-${System.currentTimeMillis()}", loginId = loginId, name = name, role = role, phone = phone, email = email),
        )
        sessionRepository.save(session)
        return session
    }

    override suspend fun login(loginId: String, password: String, preferredRole: Role?): Session {
        val session = FakeData.sessionFor(
            if (loginId == "minjun") FakeData.childUser else FakeData.parent,
        )
        sessionRepository.save(session)
        return session
    }

    override suspend fun logout() {
        sessionRepository.clear()
    }

    override suspend fun findId(name: String, phone: String): String = "parent01"
    override suspend fun resetPassword(loginId: String, verifyCode: String, newPassword: String) {}
}

class FakeUserRepository : UserRepository {
    private var mySettings = NotificationSettings(location = true, harmfulAccess = true, appUsage = true, notice = true)
    override suspend fun me(): User = FakeData.parent
    override suspend fun updateMe(name: String?, phone: String?, email: String?): User = FakeData.parent.copy(
        name = name ?: FakeData.parent.name,
        phone = phone ?: FakeData.parent.phone,
        email = email ?: FakeData.parent.email,
    )
    override suspend fun deleteMe() {}
    override suspend fun notificationSettings(): NotificationSettings = mySettings
    override suspend fun patchNotificationSettings(settings: NotificationSettings): NotificationSettings {
        mySettings = settings
        return mySettings
    }
}

class FakePairingRepository : PairingRepository {
    override suspend fun issueCode(): PairingCode = FakeData.pairing
    override suspend fun connect(pairingCode: String) {}
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
        birthDate: String?,
        school: String?
    ): ChildProfile = child(childId).copy(
        name = name ?: child(childId).name,
        alias = alias ?: child(childId).alias,
        profileImageUrl = profileImageUrl ?: child(childId).profileImageUrl,
        birthDate = birthDate ?: child(childId).birthDate,
        school = school ?: child(childId).school
    )
}

class FakeLocationRepository : LocationRepository {
    private val historyMap = mutableMapOf<String, MutableList<LocationPoint>>()
    override suspend fun current(childId: String): LocationPoint =
        LocationPoint("37.5665", "126.9780", "2024.08.20 14:30", "📍 서울시청")
    override suspend fun history(childId: String): List<LocationPoint> = historyMap[childId] ?: listOf(
        LocationPoint("37.5665", "126.9780", "14:00", "📍 학교"),
        LocationPoint("37.5655", "126.9770", "15:30", "📍 학원"),
        LocationPoint("37.5645", "126.9760", "17:00", "📍 집"),
    )
    override suspend fun uploadBatch(points: List<LocationPoint>) {}
    override suspend fun reportGpsStatus(gpsEnabled: Boolean, reportedAt: String) {}
}

class FakeGeofenceRepository : GeofenceRepository {
    private val items = mutableListOf(
        Geofence("g1", "학교", "37.5665", "126.9780", 200, true),
        Geofence("g2", "집", "37.5410", "127.0270", 100, true),
    )
    override suspend fun list(childId: String): List<Geofence> = items.toList()
    override suspend fun create(childId: String, geofence: Geofence): Geofence {
        val created = geofence.copy(id = "g-${items.size + 1}")
        items.add(created)
        return created
    }
    override suspend fun delete(childId: String, geofenceId: String) {
        items.removeAll { it.id == geofenceId }
    }
}

class FakeNotificationRepository : NotificationRepository {
    override fun paged(type: String?): Flow<PagingData<AppNotification>> = Pager(PagingConfig(20)) {
        PageResponsePagingSource { page, size ->
            PageResponse(list(type), page, size, 2, 1)
        }
    }.flow
    override suspend fun list(type: String?): List<AppNotification> = listOf(
        AppNotification("n1", "LOCATION", "안전 구역 이탈", "민준이가 '학교' 구역을 벗어났습니다.", "14:20", false),
        AppNotification("n2", "HARMFUL", "유해 사이트 접속 차단", "부적절한 사이트 접속 시도가 차단되었습니다.", "12:05", true),
    ).filter { type == null || it.type == type }
}

class FakeBlocklistRepository : BlocklistRepository {
    private val black = mutableListOf("욕설", "폭력", "성인")
    private val white = mutableListOf("https://edu.example.com", "https://library.org")
    override suspend fun get(childId: String): List<BlocklistItem> =
        black.mapIndexed { i, kw -> BlocklistItem("b$i", "BLACK", kw) } +
            white.mapIndexed { i, kw -> BlocklistItem("w$i", "WHITE", kw) }
    override suspend fun add(childId: String, listType: String, keyword: String) {
        if (listType == "BLACK") black.add(keyword) else white.add(keyword)
    }
    override suspend fun remove(childId: String, itemId: String) {}
    override suspend fun reportHarmfulAccess(matchedText: String, accessedAt: String) {}
}

class FakeScreenshotRepository : ScreenshotRepository {
    override fun paged(childId: String): Flow<PagingData<Screenshot>> = Pager(PagingConfig(20)) {
        PageResponsePagingSource { page, size ->
            PageResponse(list(childId), page, size, 2, 1)
        }
    }.flow
    override suspend fun list(childId: String): List<Screenshot> = listOf(
        Screenshot("s1", "14:30", "게임 중", "MANUAL", null),
        Screenshot("s2", "11:15", "웹 서핑", "AUTO", null),
    )
    override suspend fun requestCapture(childId: String) {
        delay(1500)
    }
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
    override suspend fun create(
        title: String,
        body: String,
        imageUrls: List<String>?,
        links: List<String>?
    ): Post {
        val created = Post(
            id = "p-${items.size + 1}",
            title = title,
            author = FakeData.parent.name,
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
                author = "나",
                text = body,
                createdAt = "방금"
            )
            items[i] = p.copy(
                commentCount = p.commentCount + 1,
                comments = p.comments + newComment
            )
        }
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
        WalletTransaction("tx2", "TRANSFER_OUT", -5000, 45000, "child-minjun", "08.20")
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

    override suspend fun rooms(): List<ChatRoom> = listOf(
        ChatRoom("room-minjun", "김민준", "학원 끝나요", "14:32", true),
        ChatRoom("room-jia", "이지아", "아직 대화가 없어요", "", false),
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

class FakePointRepository : PointRepository {
    override suspend fun balance(): PointBalance = FakeData.pointBalance
    override suspend fun history(): List<PointHistory> = listOf(
        PointHistory("ph1", "이벤트 참여", 1000, "08.20"),
        PointHistory("ph2", "포인트 전환", -500, "08.18")
    )
    override suspend fun use(productId: Int, point: Int) {}
}

class FakeSubscriptionRepository : SubscriptionRepository {
    private var sub: Subscription? = Subscription(1, "ACTIVE", "CARD")
    override suspend fun me(): Subscription? = sub
    override suspend fun subscribe(planId: Int, method: String): Subscription {
        sub = Subscription(planId, "ACTIVE", method)
        return sub!!
    }
    override suspend fun cancel() { sub = null }
}

class FakeAppUsageRepository : AppUsageRepository {
    override suspend fun list(childId: String, groupBy: String): List<AppUsage> = FakeData.usages
    override suspend fun report(childId: String, usages: List<AppUsage>) {}
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
