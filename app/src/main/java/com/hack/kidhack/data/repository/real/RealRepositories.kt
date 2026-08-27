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
import com.hack.kidhack.data.remote.api.GeofenceApi
import com.hack.kidhack.data.remote.api.InquiryApi
import com.hack.kidhack.data.remote.api.LocationApi
import com.hack.kidhack.data.remote.api.NotificationApi
import com.hack.kidhack.data.remote.api.PairingApi
import com.hack.kidhack.data.remote.api.PhotoApi
import com.hack.kidhack.data.remote.api.PaymentApi
import com.hack.kidhack.data.remote.api.PaymentChargeApi
import com.hack.kidhack.data.remote.api.PointApi
import com.hack.kidhack.data.remote.api.ScreenshotApi
import com.hack.kidhack.data.remote.api.SubscriptionApi
import com.hack.kidhack.data.remote.api.UserApi
import com.hack.kidhack.data.remote.dto.AllowancePaymentRequest
import com.hack.kidhack.data.remote.dto.AllowanceTransferRequest
import com.hack.kidhack.data.remote.api.WalletApi
import com.hack.kidhack.data.remote.dto.AppControlUpdateRequest
import com.hack.kidhack.data.remote.dto.AppUsageDto
import com.hack.kidhack.data.remote.dto.AppUsageReportRequest
import com.hack.kidhack.data.remote.dto.BlocklistAddRequest
import com.hack.kidhack.data.remote.dto.CardRegisterRequest
import com.hack.kidhack.data.remote.dto.ChangePasswordRequest
import com.hack.kidhack.data.remote.dto.ChargeConfirmRequest
import com.hack.kidhack.data.remote.dto.ChargePrepareRequest
import com.hack.kidhack.data.remote.dto.CheckoutRequest
import com.hack.kidhack.data.remote.dto.ChildUpdateRequest
import com.hack.kidhack.data.remote.dto.CommentRequest
import com.hack.kidhack.data.remote.dto.CreateChatRoomRequest
import com.hack.kidhack.data.remote.dto.CreateInquiryRequest
import com.hack.kidhack.data.remote.dto.CreatePostRequest
import com.hack.kidhack.data.remote.dto.FindIdRequest
import com.hack.kidhack.data.remote.dto.GeofenceDto
import com.hack.kidhack.data.remote.dto.GpsStatusRequest
import com.hack.kidhack.data.remote.dto.HarmfulAccessRequest
import com.hack.kidhack.data.remote.dto.LoginRequest
import com.hack.kidhack.data.remote.dto.LoginResponse
import com.hack.kidhack.data.remote.dto.PairingConnectRequest
import com.hack.kidhack.data.remote.dto.RemoteCommandAckRequest
import com.hack.kidhack.data.remote.dto.ReportRequest
import com.hack.kidhack.data.remote.dto.ResetPasswordRequest
import com.hack.kidhack.data.remote.dto.SignupRequest
import com.hack.kidhack.data.remote.dto.SubscribeRequest
import com.hack.kidhack.data.remote.dto.TransferRequest
import com.hack.kidhack.data.remote.dto.TransferResponse
import com.hack.kidhack.data.remote.dto.UsePointRequest
import com.hack.kidhack.data.remote.dto.UserUpdateRequest
import com.hack.kidhack.data.remote.paging.PageResponsePagingSource
import com.hack.kidhack.data.remote.unwrap
import com.hack.kidhack.data.remote.unwrapOrNull
import com.hack.kidhack.data.repository.toDomain
import com.hack.kidhack.data.repository.toDto
import com.hack.kidhack.data.ws.ChatWebSocketClient
import com.hack.kidhack.data.ws.ChatWsEvent
import com.hack.kidhack.data.ws.ChildCommandSocket
import com.hack.kidhack.domain.model.AppControlItem
import com.hack.kidhack.domain.model.Allowance
import com.hack.kidhack.domain.model.AllowanceEntry
import com.hack.kidhack.domain.model.AllowancePayment
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.model.BlocklistItem
import com.hack.kidhack.domain.model.Card
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.model.ChildPhoto
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.Inquiry
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
import com.hack.kidhack.domain.repository.GeofenceRepository
import com.hack.kidhack.domain.repository.InquiryRepository
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
        return establishSession(login)
    }

    override suspend fun logout() {
        runCatching { api.logout() }
        sessionRepository.clear()
        chatMessageStore.clear()
        chatWebSocketClient.reset()
        sessionEvents.emitLogout()
    }

    override suspend fun findId(name: String, phone: String): String =
        api.findId(FindIdRequest(name, phone)).unwrap().loginId

    override suspend fun resetPassword(loginId: String, verifyCode: String, newPassword: String) {
        api.resetPassword(ResetPasswordRequest(loginId, verifyCode, newPassword)).unwrapOrNull()
    }

    private suspend fun establishSession(login: LoginResponse): Session {
        val access = login.resolvedAccessToken()?.takeIf { it.isNotBlank() }
            ?: error("로그인 토큰이 없습니다")
        val refresh = login.refreshToken.orEmpty()
        val loginRole = runCatching { Role.valueOf(login.role.orEmpty()) }.getOrNull()
        val fromUser = login.user?.toDomain()
        val placeholder = User(
            id = fromUser?.id.orEmpty(),
            loginId = login.loginId?.takeIf { it.isNotBlank() }
                ?: fromUser?.loginId.orEmpty(),
            name = login.username?.takeIf { it.isNotBlank() }
                ?: fromUser?.name.orEmpty(),
            role = loginRole ?: fromUser?.role ?: Role.PARENT,
            phone = fromUser?.phone.orEmpty(),
            email = fromUser?.email,
            profileImageUrl = fromUser?.profileImageUrl,
            parentId = fromUser?.parentId,
        )
        sessionRepository.save(Session(access, refresh, placeholder))
        return try {
            val me = userApi.me().unwrap().toDomain()
            val merged = me.copy(
                loginId = me.loginId.ifBlank { placeholder.loginId },
                name = me.name.ifBlank { placeholder.name },
                role = loginRole ?: me.role,
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
    override suspend fun current(childId: String): LocationPoint = api.current(childId).unwrap().toDomain()
    override suspend fun history(childId: String): List<LocationPoint> =
        api.history(childId).unwrap().map { it.toDomain() }
    override suspend fun uploadBatch(points: List<LocationPoint>) {
        api.batch(points.map { it.toDto() }).unwrapOrNull()
    }
    override suspend fun reportGpsStatus(gpsEnabled: Boolean, reportedAt: String) {
        api.gpsStatus(GpsStatusRequest(gpsEnabled, reportedAt)).unwrapOrNull()
    }
}

class RealGeofenceRepository(private val api: GeofenceApi) : GeofenceRepository {
    override suspend fun list(childId: String): List<Geofence> = api.list(childId).unwrap().map { it.toDomain() }
    override suspend fun create(childId: String, geofence: Geofence): Geofence =
        api.create(
            childId,
            GeofenceDto(null, geofence.name, geofence.lat, geofence.lng, geofence.radius, geofence.notifyOnArrival),
        ).unwrap().toDomain()
    override suspend fun delete(childId: String, geofenceId: String) {
        api.delete(childId, geofenceId).unwrapOrNull()
    }
}

class RealNotificationRepository(private val api: NotificationApi) : NotificationRepository {
    override fun paged(type: String?): Flow<PagingData<AppNotification>> =
        Pager(PagingConfig(pageSize = 20)) {
            PageResponsePagingSource { page, size -> api.list(type, page, size).unwrap() }
        }.flow.map { paging -> paging.map { it.toDomain() } }

    override suspend fun list(type: String?): List<AppNotification> =
        api.list(type, 0, 50).unwrap().content.map { it.toDomain() }
}

class RealBlocklistRepository(private val api: BlocklistApi) : BlocklistRepository {
    override suspend fun get(childId: String): List<BlocklistItem> {
        val raw = api.get(childId).unwrap()
        return raw.blacklist.mapIndexed { i, kw -> BlocklistItem("black-$i", "BLACK", kw) } +
            raw.whitelist.mapIndexed { i, kw -> BlocklistItem("white-$i", "WHITE", kw) }
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
            PageResponsePagingSource { page, size -> api.list(childId, page, size).unwrap() }
        }.flow.map { paging -> paging.map { it.toDomain() } }
    override suspend fun list(childId: String): List<Screenshot> =
        api.list(childId, 0, 50).unwrap().content.map { it.toDomain() }
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
    override fun pagedPosts(): Flow<PagingData<Post>> =
        Pager(PagingConfig(20)) {
            PageResponsePagingSource { page, size -> api.posts(page, size).unwrap() }
        }.flow.map { paging -> paging.map { it.toDomain() } }
    override suspend fun posts(): List<Post> = api.posts(0, 50).unwrap().content.map { it.toDomain() }
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

class RealSubscriptionRepository(private val api: SubscriptionApi) : SubscriptionRepository {
    override suspend fun me(): Subscription? = api.me().unwrapOrNull()?.toDomain()
    override suspend fun subscribe(planId: Int, method: String): Subscription =
        api.subscribe(SubscribeRequest(planId, method)).unwrap().toDomain()
    override suspend fun cancel() { api.cancel().unwrapOrNull() }
}

class RealAppUsageRepository(private val api: AppUsageApi) : AppUsageRepository {
    override suspend fun list(childId: String, groupBy: String): List<AppUsage> =
        api.list(childId, groupBy).unwrap().map { it.toDomain() }
    override suspend fun report(childId: String, usages: List<AppUsage>) {
        api.report(
            childId,
            AppUsageReportRequest(usages.map { AppUsageDto(it.appName, it.durationSec, it.category) }),
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
 * 실시간 경로는 [ChildCommandSocket], 백업 경로는 `GET .../commands/pending` 폴링.
 *
 * 폴링 엔드포인트가 아직 서버에 없으면 [pending] 은 빈 목록으로 떨어지고
 * 소켓 경로만 살아 있게 된다(요청 자체가 유실되지는 않는다).
 */
class RealCommandRepository(
    private val api: CommandApi,
    private val socket: ChildCommandSocket,
) : CommandRepository {

    override fun observe(): Flow<RemoteCommand> = socket.commands
        .onStart { socket.start() }

    override val connected: Flow<Boolean> = socket.connected

    override fun disconnect() = socket.stop()

    override suspend fun pending(childId: String): List<RemoteCommand> =
        runCatching { api.pending(childId).unwrap().map { it.toDomain() } }
            .getOrDefault(emptyList())

    override suspend fun ack(childId: String, requestId: String, status: String, message: String?) {
        socket.ack(requestId, status, message)
        runCatching { api.ack(childId, requestId, RemoteCommandAckRequest(status, message)).unwrapOrNull() }
    }
}

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
