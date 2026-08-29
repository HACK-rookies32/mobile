package com.hack.kidhack.data.repository.real

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.hack.kidhack.data.local.ChatMessageStore
import com.hack.kidhack.data.remote.api.AllowanceApi
import com.hack.kidhack.data.remote.api.AppControlApi
import com.hack.kidhack.data.remote.api.AppUsageApi
import com.hack.kidhack.data.remote.api.AuthApi
import com.hack.kidhack.data.remote.api.BlocklistApi
import com.hack.kidhack.data.remote.api.CardApi
import com.hack.kidhack.data.remote.api.ChatApi
import com.hack.kidhack.data.remote.api.ChildApi
import com.hack.kidhack.data.remote.api.CommandApi
import com.hack.kidhack.data.remote.api.CommunityApi
import com.hack.kidhack.data.remote.api.InquiryApi
import com.hack.kidhack.data.remote.api.DashboardApi
import com.hack.kidhack.data.remote.api.DeviceApi
import com.hack.kidhack.data.remote.api.LocationApi
import com.hack.kidhack.data.remote.api.LocationEventApi
import com.hack.kidhack.data.remote.api.ChildAlertApi
import com.hack.kidhack.data.remote.api.NotificationApi
import com.hack.kidhack.data.remote.api.PairingApi
import com.hack.kidhack.data.remote.api.PhotoApi
import com.hack.kidhack.data.remote.api.PlaceApi
import com.hack.kidhack.data.remote.api.PaymentApi
import com.hack.kidhack.data.remote.api.PaymentChargeApi
import com.hack.kidhack.data.remote.api.PointApi
import com.hack.kidhack.data.remote.api.ScreenshotApi
import com.hack.kidhack.data.remote.api.UserApi
import com.hack.kidhack.data.remote.dto.AllowancePaymentRequest
import com.hack.kidhack.data.remote.dto.AllowanceTransferRequest
import com.hack.kidhack.data.remote.api.WalletApi
import com.hack.kidhack.data.remote.dto.AppControlUpdateRequest
import com.hack.kidhack.data.remote.dto.AppUsageReportItemDto
import com.hack.kidhack.data.remote.dto.AppUsageReportRequest
import com.hack.kidhack.data.remote.dto.BlocklistAddRequest
import com.hack.kidhack.data.remote.dto.CardRegisterRequest
import com.hack.kidhack.data.remote.dto.ChangePasswordRequest
import com.hack.kidhack.data.remote.dto.PushTokenRequest
import com.hack.kidhack.data.remote.dto.ChargeConfirmRequest
import com.hack.kidhack.data.remote.dto.ChargePrepareRequest
import com.hack.kidhack.data.remote.dto.CheckoutRequest
import com.hack.kidhack.data.remote.dto.ChildUpdateRequest
import com.hack.kidhack.data.remote.dto.CommentRequest
import com.hack.kidhack.data.remote.dto.CreateChatRoomRequest
import com.hack.kidhack.data.remote.dto.CreateInquiryRequest
import com.hack.kidhack.data.remote.dto.CreatePostRequest
import com.hack.kidhack.data.remote.dto.FindIdSendCodeRequest
import com.hack.kidhack.data.remote.dto.FindIdVerifyRequest
import com.hack.kidhack.data.remote.dto.DeviceEventRequest
import com.hack.kidhack.data.remote.dto.HarmfulAccessRequest
import com.hack.kidhack.data.remote.dto.LocationTrackRequest
import com.hack.kidhack.data.remote.dto.LoginRequest
import com.hack.kidhack.data.remote.dto.LoginResponse
import com.hack.kidhack.data.remote.dto.PairingConnectRequest
import com.hack.kidhack.data.remote.dto.PlaceRequest
import com.hack.kidhack.data.remote.dto.RemoteCommandAckRequest
import com.hack.kidhack.data.remote.dto.ReportRequest
import com.hack.kidhack.data.remote.dto.ResetPasswordRequest
import com.hack.kidhack.data.remote.dto.ResetPasswordSendCodeRequest
import com.hack.kidhack.data.remote.dto.SignupRequest
import com.hack.kidhack.data.remote.dto.TransferRequest
import com.hack.kidhack.data.remote.dto.TransferResponse
import com.hack.kidhack.data.remote.dto.UsePointRequest
import com.hack.kidhack.data.remote.dto.UserUpdateRequest
import com.hack.kidhack.data.remote.paging.PageResponsePagingSource
import com.hack.kidhack.data.remote.unwrap
import com.hack.kidhack.data.remote.unwrapOrNull
import com.hack.kidhack.data.repository.toDomain
import com.hack.kidhack.data.repository.toRoleOrNull
import com.hack.kidhack.data.repository.toDto
import com.hack.kidhack.data.ws.ChatWebSocketClient
import com.hack.kidhack.data.ws.ChatWsEvent
import com.hack.kidhack.data.ws.ChildCommandSocket
import com.hack.kidhack.domain.model.AppControlItem
import com.hack.kidhack.domain.model.Allowance
import com.hack.kidhack.domain.model.AllowanceEntry
import com.hack.kidhack.domain.model.AllowancePayment
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.ChildAlertEvent
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.model.AppUsageStat
import com.hack.kidhack.domain.model.BlocklistItem
import com.hack.kidhack.domain.model.Card
import com.hack.kidhack.domain.model.ChildDashboard
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.model.ChildPhoto
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.Inquiry
import com.hack.kidhack.domain.model.LocationEvent
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class RealAuthRepository(
    private val api: AuthApi,
    private val userApi: UserApi,
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
        // Signup response has User only (no tokens). Do not auto-login.
        val user = api.signup(
            SignupRequest(
                loginId = loginId,
                username = username,
                password = password,
                nickname = nickname,
                email = email,
                phoneNumber = phoneNumber,
                birthDate = birthDate,
                address = address,
                profileImageUrl = profileImageUrl,
                role = role.name,
                parentId = parentId,
            ),
        ).unwrap().toDomain()
        val session = Session(
            accessToken = "",
            refreshToken = "",
            user = user.copy(
                loginId = user.loginId.ifBlank { loginId },
                name = user.name.ifBlank { username },
                role = role,
            ),
        )
        sessionRepository.save(session)
        return session
    }

    override suspend fun login(loginId: String, password: String, preferredRole: Role?): Session {
        val login = api.login(LoginRequest(loginId, password)).unwrap()
        return establishSession(login, preferredRole)
    }

    override suspend fun logout() {
        runCatching { api.logout() }
        sessionRepository.clear()
        chatMessageStore.clear()
        chatWebSocketClient.reset()
        sessionEvents.emitLogout()
    }

    override suspend fun sendFindIdCode(username: String, email: String): SentCode =
        api.sendFindIdCode(FindIdSendCodeRequest(username = username, email = email))
            .unwrap()
            .let { SentCode(message = it.message, code = it.code) }

    override suspend fun verifyFindId(email: String, code: String): String =
        api.verifyFindId(FindIdVerifyRequest(email = email, code = code)).unwrap().loginId

    override suspend fun sendResetPasswordCode(loginId: String, email: String): SentCode =
        api.sendResetPasswordCode(ResetPasswordSendCodeRequest(loginId = loginId, email = email))
            .unwrap()
            .let { SentCode(message = it.message, code = it.code) }

    override suspend fun resetPassword(email: String, code: String, newPassword: String) {
        api.resetPassword(
            ResetPasswordRequest(email = email, code = code, newPassword = newPassword),
        ).unwrapOrNull()
    }

    /**
     * 역할 결정 순서: 로그인 응답 → `GET /users/me` → 로그인 화면에서 고른 값 → PARENT.
     *
     * 예전에는 응답의 role 문자열을 `Role.valueOf` 로만 읽어서, 서버가 필드를 안 주거나
     * `ROLE_CHILD` 같은 표기를 쓰면 **말없이 PARENT 로 떨어졌다**. 자녀 계정으로 로그인해도
     * 부모 화면이 열린 이유다. 이제 표기를 폭넓게 받아들이고([toRoleOrNull]), 그래도 알 수
     * 없을 때만 사용자가 고른 [preferredRole] 을 쓴다.
     */
    private suspend fun establishSession(login: LoginResponse, preferredRole: Role?): Session {
        val access = login.resolvedAccessToken()?.takeIf { it.isNotBlank() }
            ?: error("로그인 토큰이 없습니다")
        val refresh = login.refreshToken.orEmpty()
        val loginRole = login.role.toRoleOrNull() ?: login.user?.role.toRoleOrNull()
        val fromUser = login.user?.toDomain()
        val placeholder = User(
            id = fromUser?.id.orEmpty(),
            loginId = login.loginId?.takeIf { it.isNotBlank() }
                ?: fromUser?.loginId.orEmpty(),
            name = login.username?.takeIf { it.isNotBlank() }
                ?: fromUser?.name.orEmpty(),
            role = loginRole ?: preferredRole ?: Role.PARENT,
            phone = fromUser?.phone.orEmpty(),
            email = fromUser?.email,
            profileImageUrl = fromUser?.profileImageUrl,
            parentId = fromUser?.parentId,
        )
        sessionRepository.save(Session(access, refresh, placeholder))
        return try {
            // DTO 를 먼저 잡아 둔다. toDomain() 은 모르는 role 을 PARENT 로 메워 버려서
            // 「서버가 PARENT 라고 했다」와 「서버가 아무 말도 안 했다」를 구분할 수 없다.
            val meDto = userApi.me().unwrap()
            val me = meDto.toDomain()
            val merged = me.copy(
                loginId = me.loginId.ifBlank { placeholder.loginId },
                name = me.name.ifBlank { placeholder.name },
                role = loginRole ?: meDto.role.toRoleOrNull() ?: preferredRole ?: Role.PARENT,
                parentId = me.parentId ?: placeholder.parentId,
            )
            Session(access, refresh, merged).also { sessionRepository.save(it) }
        } catch (t: Throwable) {
            sessionRepository.clear()
            throw t
        }
    }
}

class RealUserRepository(private val api: UserApi) : UserRepository {
    override suspend fun me(): User = api.me().unwrap().toDomain()
    override suspend fun updateMe(
        nickname: String?,
        name: String?,
        phone: String?,
        email: String?,
        profileImageUrl: String?,
    ): User =
        api.updateMe(UserUpdateRequest(nickname, name, phone, email, profileImageUrl)).unwrap().toDomain()
    override suspend fun changePassword(newPassword: String) {
        api.changePassword(ChangePasswordRequest(newPassword)).unwrap()
    }
    override suspend fun deleteMe() { api.deleteMe().unwrapOrNull() }
    override suspend fun notificationSettings(): NotificationSettings =
        api.notificationSettings().unwrap().toDomain()
    override suspend fun patchNotificationSettings(settings: NotificationSettings): NotificationSettings =
        api.patchNotificationSettings(settings.toDto()).unwrap().toDomain()
}

class RealPairingRepository(private val api: PairingApi) : PairingRepository {
    override suspend fun issueCode(): PairingCode = api.issueCode().unwrap().toDomain()
    override suspend fun connect(pairingCode: String) {
        api.connect(PairingConnectRequest(pairingCode)).unwrap()
    }
}

class RealChildRepository(private val api: ChildApi) : ChildRepository {
    override suspend fun children(): List<ChildProfile> = api.children().unwrap().map { it.toDomain() }
    override suspend fun child(childId: String): ChildProfile = api.child(childId).unwrap().toDomain()
    override suspend fun updateChild(
        childId: String,
        name: String?,
        alias: String?,
        profileImageUrl: String?,
        birthDate: String?,
        school: String?,
    ): ChildProfile = api.update(
        childId,
        ChildUpdateRequest(name, alias, profileImageUrl, birthDate, school)
    ).unwrap().toDomain()
}

class RealLocationRepository(private val api: LocationApi) : LocationRepository {
    override suspend fun track(childId: String, latitude: String, longitude: String): LocationPoint =
        api.track(LocationTrackRequest(childId.toServerId(), latitude, longitude)).unwrap().toDomain()
    override suspend fun history(childId: String): List<LocationPoint> =
        api.history(childId).unwrap().map { it.toDomain() }
    override suspend fun toggleRealtime(childId: String, enabled: Boolean): Boolean =
        api.toggleRealtime(childId, enabled).unwrap().realtimeEnabled
}

class RealDeviceRepository(private val api: DeviceApi) : DeviceRepository {
    override suspend fun reportEvent(childId: String, type: String) {
        api.reportEvent(DeviceEventRequest(childId.toServerId(), type)).unwrapOrNull()
    }

    override suspend fun registerPushToken(token: String) {
        api.registerPushToken(PushTokenRequest(token)).unwrapOrNull()
    }

    override suspend fun unregisterPushToken(token: String) {
        api.unregisterPushToken(PushTokenRequest(token)).unwrapOrNull()
    }
}

class RealDashboardRepository(private val api: DashboardApi) : DashboardRepository {
    override suspend fun get(childId: String): ChildDashboard =
        api.get(childId).unwrap().toDomain()
}

class RealPlaceRepository(private val api: PlaceApi) : PlaceRepository {
    override suspend fun list(childId: String): List<Geofence> = api.list(childId).unwrap().map { it.toDomain() }
    override suspend fun create(childId: String, place: Geofence): Geofence =
        api.create(
            PlaceRequest(
                childId = childId.toServerId(),
                name = place.name,
                latitude = place.lat.toServerCoordinate("위도"),
                longitude = place.lng.toServerCoordinate("경도"),
                radiusMeters = place.radius.toDouble(),
                type = place.type.name,
            ),
        ).unwrap().toDomain()
    override suspend fun delete(childId: String, placeId: String) {
        api.delete(placeId).unwrapOrNull()
    }
}

class RealLocationEventRepository(private val api: LocationEventApi) : LocationEventRepository {
    override suspend fun events(childId: String): List<LocationEvent> =
        api.events(childId).unwrap().map { it.toDomain() }
}

class RealNotificationRepository(private val api: NotificationApi) : NotificationRepository {
    override suspend fun list(recipientId: String, childId: String?): List<AppNotification> =
        api.list(recipientId, childId).unwrap().map { it.toDomain() }

    override suspend fun markRead(notificationId: String) {
        api.markRead(notificationId).unwrapOrNull()
    }
}

class RealChildAlertRepository(private val api: ChildAlertApi) : ChildAlertRepository {
    override suspend fun upload(childId: String, alerts: List<ChildAlertEvent>) {
        api.upload(childId, alerts.map { it.toDto() }).unwrapOrNull()
    }

    override suspend fun list(childId: String): List<ChildAlertEvent> =
        api.list(childId).unwrap().map { it.toDomain() }
}
/**
 * GAP-04: `GET /children/{childId}/blocklist` 가 문자열 배열만 주므로 응답에 itemId 가 없다.
 *
 * 예전에는 인덱스로 `black-0` 같은 id 를 만들어 `DELETE .../blocklist/{itemId}` 에 넘겼는데,
 * 그런 id 는 서버에 존재하지 않아 카테고리 토글을 꺼도 아무것도 지워지지 않았다.
 * 응답에서 유일하게 안정적인 식별자는 **키워드 문자열**이라 그것을 itemId 로 쓴다.
 * 백엔드가 `{ id, listType, keyword }[]` 로 스키마를 고치면 여기만 바꾸면 된다.
 */
class RealBlocklistRepository(private val api: BlocklistApi) : BlocklistRepository {
    override suspend fun get(childId: String): List<BlocklistItem> {
        val raw = api.get(childId).unwrap()
        return raw.blacklist.map { BlocklistItem(it, "BLACK", it) } +
            raw.whitelist.map { BlocklistItem(it, "WHITE", it) }
    }
    override suspend fun add(childId: String, listType: String, keyword: String) {
        api.add(childId, BlocklistAddRequest(listType, keyword)).unwrapOrNull()
    }
    override suspend fun remove(childId: String, itemId: String) {
        api.delete(childId, itemId).unwrapOrNull()
    }
    override suspend fun reportHarmfulAccess(matchedText: String, accessedAt: String) {
        api.report(HarmfulAccessRequest(matchedText, accessedAt)).unwrapOrNull()
    }
}

class RealScreenshotRepository(private val api: ScreenshotApi) : ScreenshotRepository {
    override fun paged(childId: String): Flow<PagingData<Screenshot>> =
        Pager(PagingConfig(20)) {
            PageResponsePagingSource { page, size -> api.list(childId, page = page, size = size).unwrap() }
        }.flow.map { paging -> paging.map { it.toDomain() } }
    override suspend fun list(childId: String): List<Screenshot> =
        api.list(childId, page = 0, size = 50).unwrap().content.map { it.toDomain() }
    override suspend fun requestCapture(childId: String) {
        api.requestCapture(childId).unwrapOrNull()
    }

    override suspend fun upload(
        childId: String,
        file: File,
        capturedAt: String,
        trigger: String,
    ): Screenshot = api.upload(
        childId = childId,
        file = file.toImagePart(),
        capturedAt = capturedAt.toPlainBody(),
        trigger = trigger.toPlainBody(),
    ).unwrap().toDomain()
}

class RealCommunityRepository(private val api: CommunityApi) : CommunityRepository {
    override fun pagedPosts(keyword: String?): Flow<PagingData<Post>> =
        Pager(PagingConfig(20)) {
            PageResponsePagingSource { page, size -> api.posts(keyword = keyword, page = page, size = size).unwrap() }
        }.flow.map { paging -> paging.map { it.toDomain() } }
    override suspend fun posts(): List<Post> = api.posts(page = 0, size = 50).unwrap().content.map { it.toDomain() }
    override suspend fun post(postId: String): Post = api.post(postId).unwrap().toDomain()
    override suspend fun create(
        title: String,
        body: String,
        imageUrls: List<String>?,
        links: List<String>?
    ): Post =
        api.create(CreatePostRequest(title, body, imageUrls, links)).unwrap().toDomain()
    override suspend fun like(postId: String) { api.like(postId).unwrapOrNull() }
    override suspend fun comment(postId: String, body: String) {
        api.comment(postId, CommentRequest(body)).unwrapOrNull()
    }
    override suspend fun reportPost(postId: String, reason: String, detail: String?) {
        api.reportPost(postId, ReportRequest(reason, detail)).unwrapOrNull()
    }
    override suspend fun reportComment(postId: String, commentId: String, reason: String, detail: String?) {
        api.reportComment(postId, commentId, ReportRequest(reason, detail)).unwrapOrNull()
    }
}

class RealInquiryRepository(private val api: InquiryApi) : InquiryRepository {
    override suspend fun list(): List<Inquiry> =
        api.getInquiries().unwrap().map { it.toDomain() }

    override suspend fun create(
        title: String,
        body: String,
        imageUrls: List<String>?,
        links: List<String>?
    ): Inquiry = api.createInquiry(CreateInquiryRequest(title, body, imageUrls, links)).unwrap().toDomain()

    override suspend fun get(id: String): Inquiry =
        api.getInquiry(id).unwrap().toDomain()
}

class RealWalletRepository(
    private val walletApi: WalletApi,
    private val chargeApi: PaymentChargeApi
) : WalletRepository {
    override suspend fun getMyWallet(): Wallet = walletApi.me().unwrap().toDomain()
    
    override suspend fun getTransactions(type: String?, page: Int): List<WalletTransaction> =
        walletApi.transactions(type = type, page = page).unwrap().content.map { it.toDomain() }

    override suspend fun transferToChild(childId: String, amount: Int, memo: String?): TransferResult =
        walletApi.transfer(TransferRequest(childId, amount, memo)).unwrap().toDomain()

    override suspend fun reclaimFromChild(childId: String, amount: Int): TransferResult =
        walletApi.reclaim(TransferRequest(childId, amount)).unwrap().toDomain()

    override suspend fun getChildWallet(childId: String): Wallet =
        walletApi.childWallet(childId).unwrap().toDomain()

    override suspend fun prepareCharge(amount: Int, method: String, cardId: String): com.hack.kidhack.domain.model.Charge =
        chargeApi.prepare(ChargePrepareRequest(amount, method, cardId)).unwrap().toDomain()

    override suspend fun confirmCharge(chargeId: String, paymentKey: String, amount: Int): Wallet =
        chargeApi.confirm(ChargeConfirmRequest(chargeId, paymentKey, amount)).unwrap().let { resp ->
            Wallet(walletId = resp.chargeId, balance = resp.walletBalance, updatedAt = "")
        }
}

class RealCardRepository(private val api: CardApi) : CardRepository {
    override suspend fun register(
        cardNumber: String,
        expiry: String,
        cvc: String,
        cardPassword2: String,
        birthDate: String,
        ownerName: String
    ): Card = api.register(CardRegisterRequest(cardNumber, expiry, cvc, cardPassword2, birthDate, ownerName)).unwrap().toDomain()

    override suspend fun list(): List<Card> = api.list().unwrap().map { it.toDomain() }
    override suspend fun delete(cardId: String) { api.delete(cardId).unwrapOrNull() }
}

class RealChatRepository(
    private val api: ChatApi,
    private val ws: ChatWebSocketClient,
    private val sessionRepository: SessionRepository,
) : ChatRepository {
    override suspend fun getOrCreateRoom(parentId: Long, childId: Long): ChatRoom =
        api.createOrGetRoom(CreateChatRoomRequest(parentId, childId)).unwrap().toDomain()

    override suspend fun rooms(): List<ChatRoom> =
        // 백엔드에 방 목록 API 없음. ViewModel에서 children + getOrCreate 사용.
        emptyList()

    override suspend fun messages(roomId: String, before: String?, size: Int): List<ChatMessage> {
        val session = sessionRepository.current()
        val myId = session?.user?.id?.toLongOrNull()
        val myRole = session?.user?.role ?: Role.PARENT
        return api.messages(roomId, before, size).unwrap().content.map {
            it.toDomain(myUserId = myId, myRole = myRole)
        }
    }

    override fun observeMessages(roomId: String): Flow<ChatMessage> =
        ws.events
            .onStart {
                ws.subscribeRoom(roomId)
                ws.start()
            }
            .onCompletion { ws.stop() }
            .filterIsInstance<ChatWsEvent.Message>()
            .map { it.message }
            .filter { it.roomId == roomId }

    override suspend fun send(roomId: String, content: String): ChatMessage {
        val session = sessionRepository.current()
        val role = session?.user?.role ?: Role.PARENT
        val senderId = session?.user?.id?.toLongOrNull()
            ?: error("로그인 정보가 없어요")
        ws.send(roomId, senderId, content)
        val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        return ChatMessage(
            id = "local-${System.currentTimeMillis()}",
            roomId = roomId,
            fromRole = role,
            text = content,
            sentAt = now,
        )
    }

    override suspend fun markRead(roomId: String, lastMessageId: String?) {
        // 백엔드 미지원
    }
}

class RealPaymentRepository(private val api: PaymentApi) : PaymentRepository {
    override suspend fun checkout(amount: Int, method: String, productId: Int): Payment =
        api.checkout(CheckoutRequest(amount, method, productId)).unwrap().toDomain()
    override suspend fun history(): List<Payment> =
        api.history().unwrap().content.map { it.toDomain() }
}

class RealPointRepository(private val api: PointApi) : PointRepository {
    override suspend fun balance(): PointBalance = api.balance().unwrap().toDomain()
    override suspend fun history(): List<PointHistory> =
        api.history().unwrap().content.map { it.toDomain() }
    override suspend fun use(productId: Int, point: Int) {
        api.use(UsePointRequest(productId, point)).unwrapOrNull()
    }
}

class RealAppUsageRepository(private val api: AppUsageApi) : AppUsageRepository {
    override suspend fun list(
        childId: String,
        groupBy: String,
        from: String?,
        to: String?,
    ): List<AppUsageStat> =
        api.list(childId, groupBy, from, to).unwrap().items.map { it.toDomain() }

    override suspend fun report(childId: String, usages: List<AppUsage>) {
        val recordedAt = java.time.Instant.now().toString()
        api.report(
            AppUsageReportRequest(
                childId = childId.toServerId(),
                usages = usages.map {
                    AppUsageReportItemDto(it.appName, it.durationSec, recordedAt)
                },
            ),
        ).unwrapOrNull()
    }
}

class RealAppControlRepository(private val api: AppControlApi) : AppControlRepository {
    override suspend fun items(childId: String): List<AppControlItem> =
        api.getAppControlList(childId).unwrap().map { it.toDomain() }

    override suspend fun getInstalledApps(childId: String): List<com.hack.kidhack.domain.model.InstalledApp> =
        api.getInstalledApps(childId).unwrap().apps.map { it.toDomain() }

    override suspend fun updateAppControl(
        childId: String,
        packageName: String,
        blocked: Boolean,
        limitMinutes: Int?
    ): AppControlItem = api.updateAppControl(
        childId,
        AppControlUpdateRequest(packageName, blocked, limitMinutes)
    ).unwrap().toDomain()

    override suspend fun deleteAppControl(childId: String, packageName: String) {
        api.deleteAppControl(childId, packageName).unwrapOrNull()
    }
}

class RealPhotoRepository(private val api: PhotoApi) : PhotoRepository {
    override suspend fun list(childId: String): List<ChildPhoto> =
        api.list(childId, 0, 50).unwrap().content.map { it.toDomain() }

    override suspend fun requestShot(childId: String) {
        api.requestShot(childId).unwrapOrNull()
    }

    override suspend fun upload(
        childId: String,
        file: File,
        capturedAt: String,
        requestId: String?,
    ): ChildPhoto = api.upload(
        childId = childId,
        file = file.toImagePart(),
        capturedAt = capturedAt.toPlainBody(),
        requestId = requestId?.toPlainBody(),
    ).unwrap().toDomain()
}

/**
 * 수신 경로는 [ChildCommandSocket] (실시간) 과 FCM 두 가지다.
 * 폴링 백업 경로는 서버에 대응 엔드포인트가 없어 걷어냈다(2026-08-28).
 *
 * [ack] 은 소켓과 REST 로 모두 보낸다. FCM 이 소켓 꺼진 상태로 도착하면
 * `socket.ack` 이 no-op 이라 REST 쪽이 유일한 경로가 된다.
 */
class RealCommandRepository(
    private val api: CommandApi,
    private val socket: ChildCommandSocket,
) : CommandRepository {

    override fun observe(): Flow<RemoteCommand> = socket.commands
        .onStart { socket.start() }

    override val connected: Flow<Boolean> = socket.connected

    override fun disconnect() = socket.stop()

    override suspend fun ack(childId: String, requestId: String, status: String, message: String?) {
        socket.ack(requestId, status, message)
        runCatching { api.ack(childId, requestId, RemoteCommandAckRequest(status, message)).unwrapOrNull() }
    }
}

/** 위치 API body의 `childId`는 숫자다. 숫자가 아니면 엉뚱한 대상으로 보내지 말고 실패시킨다. */
private fun String.toServerId(): Long =
    toLongOrNull() ?: throw IllegalStateException("childId는 숫자 형식이어야 합니다: $this")

/** 장소 좌표도 서버에서는 숫자다. (0.0, 0.0)으로 조용히 등록되는 것을 막는다. */
private fun String.toServerCoordinate(label: String): Double =
    toDoubleOrNull() ?: throw IllegalStateException("$label 값이 숫자 형식이어야 합니다: $this")

private fun File.toImagePart(): MultipartBody.Part =
    MultipartBody.Part.createFormData("file", name, asRequestBody("image/jpeg".toMediaType()))

private fun String.toPlainBody(): RequestBody = toRequestBody("text/plain".toMediaType())

class RealAllowanceRepository(private val api: AllowanceApi) : AllowanceRepository {

    override suspend fun balance(childId: String): Allowance =
        api.balance(childId).unwrap().toDomain()

    override suspend fun history(childId: String): List<AllowanceEntry> =
        api.history(childId).unwrap().content.map { it.toDomain() }

    override suspend fun send(childId: String, amount: Int, memo: String?): Allowance =
        api.send(childId, AllowanceTransferRequest(amount, memo)).unwrap().toDomain()

    override suspend fun withdraw(childId: String, amount: Int, memo: String?): Allowance =
        api.withdraw(childId, AllowanceTransferRequest(amount, memo)).unwrap().toDomain()

    override suspend fun pay(
        childId: String,
        token: String,
        amount: Int,
        merchant: String?,
    ): AllowancePayment =
        api.pay(childId, AllowancePaymentRequest(token, amount, merchant)).unwrap().toDomain()
}
