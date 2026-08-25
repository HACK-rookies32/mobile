package com.hack.kidhack.domain.repository

import androidx.paging.PagingData
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
import kotlinx.coroutines.flow.Flow
import java.io.File

interface AuthRepository {
    suspend fun signup(
        loginId: String,
        password: String,
        name: String,
        role: Role,
        phone: String,
        email: String?,
        pairingCode: String?,
    ): Session

    suspend fun login(loginId: String, password: String, preferredRole: Role? = null): Session
    suspend fun logout()
    suspend fun findId(name: String, phone: String): String
    suspend fun resetPassword(loginId: String, verifyCode: String, newPassword: String)
}

interface UserRepository {
    suspend fun me(): User
    suspend fun updateMe(name: String?, phone: String?, email: String?): User
    suspend fun deleteMe()
    suspend fun notificationSettings(): NotificationSettings
    suspend fun patchNotificationSettings(settings: NotificationSettings): NotificationSettings
}

interface PairingRepository {
    suspend fun issueCode(): PairingCode
    suspend fun connect(pairingCode: String)
}

interface ChildRepository {
    suspend fun children(): List<ChildProfile>
    suspend fun child(childId: String): ChildProfile
    suspend fun updateChild(childId: String, name: String?, alias: String?, profileImageUrl: String?): ChildProfile
}

interface LocationRepository {
    suspend fun current(childId: String): LocationPoint
    suspend fun history(childId: String): List<LocationPoint>
    suspend fun uploadBatch(points: List<LocationPoint>)
    suspend fun reportGpsStatus(gpsEnabled: Boolean, reportedAt: String)
}

interface GeofenceRepository {
    suspend fun list(childId: String): List<Geofence>
    suspend fun create(childId: String, geofence: Geofence): Geofence
    suspend fun delete(childId: String, geofenceId: String)
}

interface NotificationRepository {
    fun paged(type: String?): Flow<PagingData<AppNotification>>
    suspend fun list(type: String?): List<AppNotification>
}

interface BlocklistRepository {
    suspend fun get(childId: String): List<BlocklistItem>
    suspend fun add(childId: String, listType: String, keyword: String)
    suspend fun remove(childId: String, itemId: String)
    suspend fun reportHarmfulAccess(matchedText: String, accessedAt: String)
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

/** 자녀 카메라로 찍은 사진을 부모 계정으로 올린다. */
interface PhotoRepository {
    suspend fun upload(
        childId: String,
        file: File,
        capturedAt: String,
        requestId: String?,
    ): ChildPhoto
}

/**
 * 부모 → 자녀 원격 요청 수신 채널.
 *
 * Firebase(FCM)를 쓰지 않으므로 실시간 경로는 WebSocket, 백업 경로는 주기 폴링이다.
 * [observe] 는 소켓 이벤트, [pending] 은 폴링에 쓴다.
 */
interface CommandRepository {
    fun observe(): Flow<RemoteCommand>

    /** 상주 소켓을 내린다. 자녀 세션이 끝날 때(로그아웃/역할 변경) 호출한다. */
    fun disconnect()

    suspend fun pending(childId: String): List<RemoteCommand>
    suspend fun ack(childId: String, requestId: String, status: String, message: String? = null)
}

interface CommunityRepository {
    fun pagedPosts(): Flow<PagingData<Post>>
    suspend fun posts(): List<Post>
    suspend fun post(postId: String): Post
    suspend fun create(title: String, body: String): Post
    suspend fun like(postId: String)
    suspend fun comment(postId: String, body: String)
}

interface ChatRepository {
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

interface SubscriptionRepository {
    suspend fun me(): Subscription?
    suspend fun subscribe(planId: Int, method: String): Subscription
    suspend fun cancel()
}

interface AppUsageRepository {
    suspend fun list(childId: String, groupBy: String): List<AppUsage>
    suspend fun report(childId: String, usages: List<AppUsage>)
}

interface AppControlRepository {
    suspend fun items(childId: String): List<AppControlItem>
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
