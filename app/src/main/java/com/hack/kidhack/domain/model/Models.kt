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
    val phone: String = "",
    val email: String? = null,
    val profileImageUrl: String? = null,
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
)

data class Geofence(
    val id: String,
    val name: String,
    val lat: String,
    val lng: String,
    val radius: Int,
    val notifyOnArrival: Boolean,
)

data class AppNotification(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val createdAt: String,
    val read: Boolean,
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
)

data class ChatRoom(
    val id: String,
    val title: String,
    val lastMessage: String,
    val lastAt: String,
    val online: Boolean,
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

data class PairingCode(
    val pairingCode: String,
    val expiresIn: Int,
)

data class AppControlItem(
    val name: String,
    val icon: String,
    val category: String,
    val blocked: Boolean,
    val limit: String,
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
    val imageUrl: String? = null,
)

/** 자녀 모드 루키즈페이 QR 결제 토큰. */
data class PayQrToken(
    val payload: String,
    val amount: Int,
    val expiresAtMillis: Long,
)
