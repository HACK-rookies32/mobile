package com.hack.kidhack.domain.repository

import androidx.paging.PagingData
import com.hack.kidhack.domain.model.AppControlItem
import com.hack.kidhack.domain.model.Activity
import com.hack.kidhack.domain.model.ActivityType
import com.hack.kidhack.domain.model.Allowance
import com.hack.kidhack.domain.model.AllowanceEntry
import com.hack.kidhack.domain.model.AllowancePayment
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.model.AppUsageStat
import com.hack.kidhack.domain.model.BlocklistItem
import com.hack.kidhack.domain.model.ChildAlertEvent
import com.hack.kidhack.domain.model.Card
import com.hack.kidhack.domain.model.Charge
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.model.ChildPhoto
import com.hack.kidhack.domain.model.ChildDashboard
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.InstalledApp
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
import com.hack.kidhack.domain.model.SentCode
import com.hack.kidhack.domain.model.Screenshot
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.model.User
import com.hack.kidhack.domain.model.Wallet
import com.hack.kidhack.domain.model.WalletTransaction
import com.hack.kidhack.domain.model.TransferResult
import kotlinx.coroutines.flow.Flow
import java.io.File

interface AuthRepository {
    suspend fun signup(
        loginId: String,
        username: String,
        password: String,
        nickname: String?,
        email: String?,
        phoneNumber: String,
        birthDate: String,
        address: String?,
        profileImageUrl: String? = null,
        role: Role,
        parentId: Long?,
    ): Session

    suspend fun login(loginId: String, password: String, preferredRole: Role? = null): Session
    suspend fun logout()
    /**
     * 아이디 찾기 1단계 — 실명 + 이메일로 인증코드 발송.
     *
     * 서버가 mock 메일러를 써서 코드를 응답에 실어 주므로 [SentCode.code] 가 채워져 올 수 있다.
     * 운영에서는 화면에 노출하지 않는다.
     */
    suspend fun sendFindIdCode(username: String, email: String): SentCode

    /** 아이디 찾기 2단계 — 코드 검증 후 로그인 아이디 반환. */
    suspend fun verifyFindId(email: String, code: String): String

    /** 비밀번호 재설정 1단계 — 아이디 + 이메일로 인증코드 발송. */
    suspend fun sendResetPasswordCode(loginId: String, email: String): SentCode

    /** 비밀번호 재설정 2단계 — 코드 검증과 동시에 새 비밀번호 적용. */
    suspend fun resetPassword(email: String, code: String, newPassword: String)
}

interface UserRepository {
    suspend fun me(): User
    /** 서버가 받는 항목만. 실명(username)은 수정 대상이 아니다. */
    suspend fun updateMe(
        nickname: String?,
        phoneNumber: String?,
        email: String?,
        address: String?,
        profileImageUrl: String?,
    ): User
    suspend fun deleteMe()

    /**
     * 커뮤니티 활동 내역.
     *
     * 서버는 `type` 을 **필수**로 받는다(미전달 시 400). 「전체」 조회는 없으므로
     * 널을 허용하지 않는다. [ActivityType.UNKNOWN] 은 응답 파싱용이라 필터로 쓰지 않는다.
     */
    fun pagedActivities(type: ActivityType): Flow<PagingData<Activity>>
    suspend fun notificationSettings(): NotificationSettings
    suspend fun patchNotificationSettings(settings: NotificationSettings): NotificationSettings
}

interface PairingRepository {
    suspend fun issueCode(): PairingCode

    /** 아직 만료되지 않은 내 코드 목록. 연결에 쓰인 코드는 서버가 지우므로 목록에서 빠진다. */
    suspend fun activeCodes(): List<PairingCode>

    suspend fun connect(pairingCode: String)
}

interface ChildRepository {
    suspend fun children(): List<ChildProfile>
    suspend fun child(childId: String): ChildProfile
    /** 서버 ChildUpdateRequest 가 받는 항목만. 이름·생년월일은 수정 대상이 아니다. */
    suspend fun updateChild(
        childId: String,
        nickname: String?,
        profileImageUrl: String?,
    ): ChildProfile

    /**
     * 자녀 페어링 해제. **서버가 계정을 소프트 삭제까지 하므로 되돌릴 수 없다.**
     * 화면에서 「연결만 끊는다」는 인상을 주면 안 된다.
     */
    suspend fun unpair(childId: String)

    /** 자녀 기기를 강제 로그아웃시킨다(서버의 refresh token 무효화). */
    suspend fun forceLogout(childId: String)
}

interface LocationRepository {
    /** 자녀 앱에서 좌표 업로드. 저장된 세그먼트/포인트를 돌려준다. */
    suspend fun track(childId: String, latitude: String, longitude: String): LocationPoint

    /**
     * 동선 좌표를 **최신순**으로. 첫 항목이 가장 최근 위치다.
     * (서버는 오름차순으로 주고 구현에서 뒤집는다.)
     */
    suspend fun history(childId: String): List<LocationPoint>

    /** 실시간 추적 현재 상태. 화면 진입 시 스위치 초기값을 서버에서 가져오는 데 쓴다. */
    suspend fun realtimeEnabled(childId: String): Boolean

    /** 실시간 추적 토글. 서버가 확정한 상태를 돌려준다. */
    suspend fun toggleRealtime(childId: String, enabled: Boolean): Boolean
}

interface DeviceRepository {
    /** 기기 전원/GPS 꺼짐 보고. type = "POWER_OFF" | "GPS_OFF" */
    suspend fun reportEvent(childId: String, type: String)

    /** FCM 토큰 등록·해제. 서버가 아직 없으면 호출자가 runCatching으로 삼킨다. */
    suspend fun registerPushToken(token: String)
    suspend fun unregisterPushToken(token: String)
}

interface DashboardRepository {
    suspend fun get(childId: String): ChildDashboard
}

interface PlaceRepository {
    suspend fun list(childId: String): List<Geofence>
    suspend fun create(childId: String, place: Geofence): Geofence

    /** 백엔드에 신설 요청한 DELETE /location/places/{placeId} 선반영. */
    suspend fun delete(childId: String, placeId: String)
}

interface LocationEventRepository {
    suspend fun events(childId: String): List<LocationEvent>
}

interface NotificationRepository {
    /** 명세 4.5 GET /notifications/{recipientId} — page/size 없이 배열 그대로 반환. */
    suspend fun list(recipientId: String, childId: String? = null): List<AppNotification>
    suspend fun markRead(notificationId: String)
}

/**
 * 자녀 안전 사건의 서버 동기화 (docs/child-push-contract.md 4절 제안 계약).
 *
 * 자녀 앱이 [upload] 로 올리고 보호자 앱이 [list] 로 읽는다.
 * 서버가 아직 구현하지 않아도 앱은 정상 동작해야 하므로, 실패는 호출자가 삼킨다.
 */
interface ChildAlertRepository {
    suspend fun upload(childId: String, alerts: List<ChildAlertEvent>)
    suspend fun list(childId: String): List<ChildAlertEvent>
}

interface BlocklistRepository {
    suspend fun get(childId: String): List<BlocklistItem>
    suspend fun add(childId: String, pattern: String): BlocklistItem

    /** 규칙 id 로 삭제한다. */
    suspend fun remove(ruleId: String)

    /**
     * 접속 대상을 서버에 올리고 차단 여부를 돌려받는다.
     * 서버는 자녀별 규칙 + 관리자 전역 유해 키워드를 함께 본다.
     */
    suspend fun reportVisit(childId: String, target: String): Boolean
}

interface ScreenshotRepository {
    fun paged(childId: String): Flow<PagingData<Screenshot>>
    suspend fun list(childId: String): List<Screenshot>
    suspend fun requestCapture(childId: String)

    /** 자녀 기기가 캡처한 이미지를 multipart 로 올린다. trigger 는 SCHEDULED | MANUAL. */
    suspend fun upload(
        childId: String,
        file: File,
        capturedAt: String,
        trigger: String,
    ): Screenshot
}

/**
 * 자녀 카메라 촬영.
 *
 * 자녀 앱이 [upload] 로 올리고, 부모 앱이 [list] 로 갤러리를 보고
 * [requestShot] 으로 「지금 찍어 달라」 요청을 보낸다(ScreenshotRepository 와 대칭).
 */
interface PhotoRepository {
    suspend fun upload(
        childId: String,
        file: File,
        capturedAt: String,
        requestId: String?,
    ): ChildPhoto

    suspend fun list(childId: String): List<ChildPhoto>

    /** 부모 모드 전용. 서버가 자녀 기기로 CAMERA_REQUEST 푸시를 밀게 한다. */
    suspend fun requestShot(childId: String)
}

/**
 * 부모 → 자녀 원격 요청 수신 채널.
 *
 * 수신 경로는 WebSocket([observe]) 과 FCM 두 가지다. 폴링 백업 경로는
 * 서버에 대응 엔드포인트가 없어 걷어냈다(2026-08-28).
 * [ack] 은 두 경로 모두에서 처리 결과를 서버로 되돌릴 때 쓴다.
 */
interface CommandRepository {
    fun observe(): Flow<RemoteCommand>

    /**
     * 실시간 요청 채널(WebSocket)이 붙어 있는지.
     *
     * 끊겨 있으면 부모 요청이 FCM 으로만 들어오므로 화면에 그대로 알려 준다.
     */
    val connected: Flow<Boolean>

    /** 상주 소켓을 내린다. 자녀 세션이 끝날 때(로그아웃/역할 변경) 호출한다. */
    fun disconnect()

    suspend fun ack(childId: String, requestId: String, status: String, message: String? = null)
}

interface CommunityRepository {
    fun pagedPosts(keyword: String? = null): Flow<PagingData<Post>>
    suspend fun posts(): List<Post>
    suspend fun post(postId: String): Post
    suspend fun create(
        title: String,
        body: String,
        imageUrls: List<String>? = null,
        links: List<String>? = null
    ): Post
    suspend fun like(postId: String)
    suspend fun comment(postId: String, body: String)
    suspend fun reportPost(postId: String, reason: String, detail: String? = null)
    suspend fun reportComment(postId: String, commentId: String, reason: String, detail: String? = null)
}

interface InquiryRepository {
    suspend fun list(): List<Inquiry>
    suspend fun create(title: String, body: String, imageUrls: List<String>? = null, links: List<String>? = null): Inquiry
    suspend fun get(id: String): Inquiry
}

interface WalletRepository {
    suspend fun getMyWallet(): Wallet
    suspend fun getTransactions(type: String?, page: Int): List<WalletTransaction>
    suspend fun transferToChild(childId: String, amount: Int, memo: String?): TransferResult
    suspend fun reclaimFromChild(childId: String, amount: Int): TransferResult
    suspend fun getChildWallet(childId: String): Wallet
    suspend fun prepareCharge(amount: Int, method: String, cardId: String): Charge
    suspend fun confirmCharge(chargeId: String, paymentKey: String, amount: Int): Wallet
}

interface CardRepository {
    suspend fun register(
        cardNumber: String,
        expiry: String,
        cvc: String,
        cardPassword2: String,
        birthDate: String,
        ownerName: String
    ): Card
    suspend fun list(): List<Card>
    suspend fun delete(cardId: String)
}

interface ChatRepository {
    /** 부모–자녀 1:1 방. 없으면 서버가 생성한다. */
    suspend fun getOrCreateRoom(parentId: Long, childId: Long): ChatRoom
    suspend fun rooms(): List<ChatRoom>
    suspend fun messages(roomId: String, before: String? = null, size: Int = 30): List<ChatMessage>
    fun observeMessages(roomId: String): Flow<ChatMessage>
    suspend fun send(roomId: String, content: String): ChatMessage
    suspend fun markRead(roomId: String, lastMessageId: String? = null)
}

interface PaymentRepository {
    suspend fun checkout(amount: Int, method: String, productId: Int): Payment
    suspend fun history(): List<Payment>
}

interface PointRepository {
    suspend fun balance(): PointBalance
    suspend fun history(): List<PointHistory>
    suspend fun use(productId: Int, point: Int)
}

interface AppUsageRepository {
    suspend fun list(
        childId: String,
        groupBy: String = "DAY",
        from: String? = null,
        to: String? = null,
    ): List<AppUsageStat>
    suspend fun report(childId: String, usages: List<AppUsage>)
}

interface AppControlRepository {
    /** 자녀별 사용시간 제한 규칙 목록. */
    suspend fun items(childId: String): List<AppControlItem>

    /** 자녀 기기에 설치된 앱 목록(서버가 보관 중인 것). */
    suspend fun getInstalledApps(childId: String): List<InstalledApp>

    /** 자녀 앱이 설치 목록을 서버에 올린다. 반영된 개수를 돌려준다. */
    suspend fun syncInstalledApps(childId: String, apps: List<InstalledApp>): Int

    /** 규칙 신설. 한도 0분이면 사실상 차단이다. */
    suspend fun addRule(
        childId: String,
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int?,
    ): AppControlItem

    /** 규칙 수정. 바꿀 값만 넘긴다. */
    suspend fun updateRule(
        ruleId: String,
        dailyLimitMinutes: Int? = null,
        enabled: Boolean? = null,
    ): AppControlItem

    suspend fun deleteRule(ruleId: String)
}

/**
 * 루키즈페이 용돈 지갑.
 *
 * 구독·결제 적립에 쓰는 [PointRepository] 와 분리된 별도 지갑이다.
 * 잔액의 유일한 공급원은 부모의 [send] 이고, 자녀는 [pay] 로 소비만 한다.
 */
interface AllowanceRepository {
    suspend fun balance(childId: String): Allowance
    suspend fun history(childId: String): List<AllowanceEntry>

    /** 부모 모드 전용 — 자녀 앱에는 이 UI 가 없다. */
    suspend fun send(childId: String, amount: Int, memo: String?): Allowance

    /** 부모 모드 전용 — 남은 용돈을 회수한다. */
    suspend fun withdraw(childId: String, amount: Int, memo: String?): Allowance

    /**
     * 자녀 모드 QR 결제. 잔액 검증의 최종 권한은 서버에 있다.
     * (부모가 방금 회수했을 수 있으므로 클라이언트 잔액만 믿으면 안 된다.)
     */
    suspend fun pay(childId: String, token: String, amount: Int, merchant: String?): AllowancePayment
}

interface SessionRepository {
    val session: Flow<Session?>
    suspend fun current(): Session?
    suspend fun save(session: Session)
    suspend fun clear()
    fun accessToken(): String?
    fun refreshToken(): String?
}

interface SelectedChildRepository {
    val selectedChildId: Flow<String?>
    suspend fun current(): String?
    suspend fun select(childId: String?)
}

interface SessionEvents {
    val logout: Flow<Unit>
    fun emitLogout()
}

interface ChildOnboardingStore {
    val done: Flow<Boolean>
    suspend fun isDone(): Boolean
    suspend fun markDone()
}

/** 자녀 앱에서 "내 childId". 서버 리소스 경로에 그대로 들어간다. */
suspend fun SessionRepository.childId(): String = current()?.user?.id.orEmpty()
