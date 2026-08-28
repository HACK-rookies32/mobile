package com.hack.kidhack.domain.model

enum class Role { PARENT, CHILD }

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val user: User,
)

data class User(
    val id: String,
    val loginId: String,
    val name: String,
    val role: Role,
    val nickname: String? = null,
    val phone: String = "",
    val email: String? = null,
    val profileImageUrl: String? = null,
    /** 자녀 계정만. 연결된 부모 member id. */
    val parentId: Long? = null,
)

data class ChildProfile(
    val id: String,
    val name: String,
    val alias: String? = null,
    val ageLabel: String? = null,
    val school: String? = null,
    val birthDate: String? = null,
    val deviceModel: String? = null,
    val profileImageUrl: String? = null,
    val online: Boolean = false,
)

data class LocationPoint(
    val lat: String,
    val lng: String,
    val recordedAt: String,
    val label: String? = null,
    val segmentStart: String? = null,
    val segmentEnd: String? = null,
    val updatedAt: String? = null,
)

/** 지오펜스(진입/이탈) / 목적지(도착) 구분. */
enum class PlaceType { GEOFENCE, DESTINATION }

data class Geofence(
    val id: String,
    val name: String,
    val lat: String,
    val lng: String,
    val radius: Int,
    val notifyOnArrival: Boolean,
    val type: PlaceType = PlaceType.GEOFENCE,
)

enum class LocationEventType { ENTER, EXIT, ARRIVED }

data class LocationEvent(
    val id: String,
    val placeId: String,
    val eventType: LocationEventType,
    val occurredAt: String,
)

data class AppNotification(
    val id: String,
    val type: String,
    val message: String,
    val createdAt: String,
    val read: Boolean,
    /**
     * 어느 자녀의 알림인지. 자녀를 여러 명 둔 보호자가 대시보드에서 한 명을 고르면
     * 그 자녀의 알림만 보여 주는 데 쓴다.
     *
     * null 이면 「어느 자녀인지 알 수 없음」이다(서버가 알려 주지 않은 경우).
     * 그런 알림은 숨기지 않고 모든 자녀에게 공통으로 보여 준다 — 감춰서 놓치는 것보다 낫다.
     */
    val childId: String? = null,
<<<<<<< HEAD
=======
)

/** ingyu 알림 경로가 쓰던 title/body. 서버 계약의 message를 첫 줄/나머지로 나눈다. */
val AppNotification.title: String
    get() = message.substringBefore('\n').ifBlank { message }

val AppNotification.body: String
    get() = message.substringAfter('\n', missingDelimiterValue = "")

/** GET /children/{childId}/dashboard 통합 조회 결과. */
data class ChildDashboard(
    val childId: String,
    val currentLocation: LocationPoint?,
    val realtimeEnabled: Boolean,
    val recentNotifications: List<AppNotification>,
    val blockRulePatterns: List<String>,
    val places: List<Geofence>,
>>>>>>> 3c24953b345a846397da744d9d2a9170b7a27e57
)

data class NotificationSettings(
    val location: Boolean,
    val harmfulAccess: Boolean,
    val appUsage: Boolean,
    val notice: Boolean,
)

data class BlocklistItem(
    val id: String,
    val listType: String,
    val keyword: String,
)

data class Screenshot(
    val id: String,
    val capturedAt: String,
    val description: String,
    val trigger: String,
    val imageUrl: String? = null,
)

data class Post(
    val id: String,
    val title: String,
    val author: String,
    val commentCount: Int,
    val likeCount: Int,
    val createdAtLabel: String,
    val liked: Boolean = false,
    val imageUrls: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val comments: List<Comment> = emptyList(),
)

data class Comment(
    val id: String,
    val author: String,
    val text: String,
    val createdAt: String,
)

data class Inquiry(
    val id: String,
    val title: String,
    val body: String,
    val status: String, // PENDING, ANSWERED
    val createdAt: String,
    val imageUrls: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val answers: List<InquiryAnswer> = emptyList(),
)

data class InquiryAnswer(
    val id: String,
    val author: String,
    val body: String,
    val createdAt: String,
)

data class ChatRoom(
    val id: String,
    val title: String,
    val lastMessage: String,
    val lastAt: String,
    val online: Boolean,
    /** 서버 방 소유자. 계정 전환 시 잘못된 room 재사용 방지용. */
    val parentId: Long? = null,
    val childId: Long? = null,
)

data class ChatMessage(
    val id: String,
    val roomId: String,
    val fromRole: Role,
    val text: String,
    val sentAt: String,
)

data class Payment(
    val id: String,
    val description: String,
    val amount: Int,
    val dateLabel: String,
    val type: String,
)

data class PointBalance(
    val balance: Int,
)

data class PointHistory(
    val id: String,
    val description: String,
    val point: Int,
    val dateLabel: String,
)

data class Subscription(
    val planId: Int,
    val status: String,
    val method: String? = null,
)

data class AppUsage(
    val appName: String,
    val durationSec: Int,
    val category: String? = null,
)

<<<<<<< HEAD
=======
/** 서버 GET /monitoring/usage 집계 결과. groupBy에 따라 key의 의미가 달라진다
 *  (APP=앱 이름, HOUR="00"~"23", DAY="yyyy-MM-dd"). */
data class AppUsageStat(
    val key: String,
    val durationSec: Int,
)

>>>>>>> 3c24953b345a846397da744d9d2a9170b7a27e57
/**
 * 자녀 기기에서 감지된 안전 사건.
 *
 * 자녀 홈 「최근 알림」([com.hack.kidhack.data.local.ChildAlertStore])과 같은 데이터를
 * 서버로 나르는 단위다. id 는 기기가 만든 고유값이라 재업로드·재조회에도 중복되지 않는다.
 */
data class ChildAlertEvent(
    val id: String,
    val type: String,
    val title: String,
    val body: String = "",
    val occurredAt: String = "",
)

/**
 * 자녀 기기 사건 타입 → 보호자 알림 타입.
 *
 * 보호자 화면의 아이콘 매핑과 수신 설정(위치/유해/앱사용) 필터가 이 값 기준으로 돈다.
 * null 이면 안전 사건이 아니라는 뜻이다(캡처 요청 등) — 보호자에게 나르지 않는다.
 */
fun childAlertTypeToParent(type: String): String? = when (type.trim().uppercase()) {
    "HARMFUL_BLOCK" -> "HARMFUL"
    "GEOFENCE_EXIT" -> "LOCATION"
    "GEOFENCE_ENTER" -> "ARRIVAL"
    "SCREEN_TIME_LIMIT" -> "APP_USAGE"
    "GPS_OFF" -> "GPS_OFF"
    else -> null
}

data class PairingCode(
    val pairingCode: String,
    val expiresIn: Int,
)

data class AppControlItem(
    val packageName: String,
    val name: String,
    val icon: String,
    val category: String,
    val blocked: Boolean,
    val limit: String,
    val limitMinutes: Int? = null,
)

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val category: String?,
)

data class Wallet(
    val walletId: String,
    val balance: Int,
    val updatedAt: String
)

data class WalletTransaction(
    val txId: String,
    val type: String,
    val amount: Int,
    val balanceAfter: Int,
    val refId: String?,
    val createdAt: String
)

data class Charge(
    val chargeId: String,
    val amount: Int,
    val status: String,
    val paymentKey: String?,
    val method: String
)

data class Card(
    val cardId: String,
    val last4: String,
    val expiry: String,
    val ownerName: String
)

data class TransferResult(
    val parentBalance: Int,
    val childBalance: Int,
    val txId: String
)

/** 부모가 보낸 원격 요청 종류. Firebase 대신 WebSocket/폴링으로 전달된다. */
enum class RemoteCommandType { SCREEN_CAPTURE, CAMERA_SHOT, UNKNOWN }

data class RemoteCommand(
    val requestId: String,
    val type: RemoteCommandType,
    val childId: String,
    val requestedBy: String? = null,
    val requestedAt: String? = null,
)

/** 요청 처리 결과를 서버에 되돌려 줄 때 쓰는 상태값. */
object CommandAckStatus {
    const val RECEIVED = "RECEIVED"
    const val DONE = "DONE"
    const val DENIED = "DENIED"
    const val FAILED = "FAILED"
}

data class ChildPhoto(
    val id: String,
    val capturedAt: String,
    /** 서버 URL 또는 기기에 저장된 파일 경로. 갤러리는 이 값으로 이미지를 그린다. */
    val imageUrl: String? = null,
    /** 부모 요청으로 찍은 사진이면 그 요청 id. 자녀가 스스로 찍었으면 null. */
    val requestId: String? = null,
    val requestedBy: String? = null,
)

/** 자녀 모드 루키즈페이 QR 결제 토큰. */
data class PayQrToken(
    val token: String,
    val payload: String,
    val amount: Int,
    val expiresAtMillis: Long,
)

/**
 * 루키즈페이 용돈 지갑.
 *
 * 부모가 [AllowanceType.SEND] 로 채우고 [AllowanceType.WITHDRAW] 로 회수한다.
 * 자녀는 이 잔액 한도 안에서만 [AllowanceType.PAYMENT] 를 만들 수 있다.
 */
data class Allowance(
    val balance: Int,
    val updatedAt: String? = null,
)

enum class AllowanceType { SEND, WITHDRAW, PAYMENT }

data class AllowanceEntry(
    val id: String,
    val type: AllowanceType,
    /** 부호 있는 금액. 받으면 +, 회수/결제면 -. */
    val amount: Int,
    val description: String,
    val dateLabel: String,
)

data class AllowancePayment(
    val id: String,
    val amount: Int,
    val balanceAfter: Int,
    val approvedAt: String,
    val merchant: String? = null,
)
