package com.hack.kidhack.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val loginId: String, val password: String)

@Serializable
data class SignupRequest(
    val loginId: String,
    val password: String,
    val name: String,
    val role: String,
    val phone: String,
    val email: String? = null,
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class FindIdRequest(val name: String, val phone: String)

@Serializable
data class ResetPasswordRequest(
    val loginId: String,
    val verifyCode: String,
    val newPassword: String,
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
)

@Serializable
data class FindIdResponse(val loginId: String)

@Serializable
data class UserDto(
    val id: String,
    val loginId: String,
    val name: String,
    val role: String,
    val phone: String = "",
    val email: String? = null,
    val profileImageUrl: String? = null,
)

@Serializable
data class UserUpdateRequest(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
)

@Serializable
data class NotificationSettingsDto(
    val location: Boolean,
    val harmfulAccess: Boolean,
    val appUsage: Boolean,
    val notice: Boolean,
)

@Serializable
data class PairingCodeDto(val pairingCode: String, val expiresIn: Int)

@Serializable
data class PairingConnectRequest(val pairingCode: String)

@Serializable
data class ChildDto(
    val id: String,
    val name: String,
    val alias: String? = null,
    val profileImageUrl: String? = null,
    val school: String? = null,
    val birthDate: String? = null,
    val deviceModel: String? = null,
    val ageLabel: String? = null,
    val online: Boolean = false,
)

@Serializable
data class ChildUpdateRequest(
    val name: String? = null,
    val alias: String? = null,
    val profileImageUrl: String? = null,
)

@Serializable
data class LocationDto(
    val lat: String,
    val lng: String,
    val recordedAt: String,
    val label: String? = null,
)

@Serializable
data class GpsStatusRequest(val gpsEnabled: Boolean, val reportedAt: String)

@Serializable
data class GeofenceDto(
    val id: String? = null,
    val name: String,
    val lat: String,
    val lng: String,
    val radius: Int,
    val notifyOnArrival: Boolean = true,
)

@Serializable
data class NotificationDto(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val createdAt: String,
    val read: Boolean = false,
)

@Serializable
data class BlocklistResponse(
    val blacklist: List<String> = emptyList(),
    val whitelist: List<String> = emptyList(),
)

@Serializable
data class BlocklistAddRequest(val listType: String, val keyword: String)

@Serializable
data class HarmfulAccessRequest(val matchedText: String, val accessedAt: String)

@Serializable
data class ScreenshotDto(
    val id: String,
    val capturedAt: String,
    val description: String = "",
    val trigger: String = "MANUAL",
    val imageUrl: String? = null,
)

@Serializable
data class PostDto(
    val id: String,
    val title: String,
    val author: String,
    val commentCount: Int = 0,
    val likeCount: Int = 0,
    val createdAtLabel: String = "",
    val liked: Boolean = false,
)

@Serializable
data class CreatePostRequest(val title: String, val body: String)

@Serializable
data class CommentRequest(val body: String)

@Serializable
data class ChatRoomDto(
    val id: String,
    val title: String,
    val lastMessage: String = "",
    val lastAt: String = "",
    val online: Boolean = false,
)

@Serializable
data class ChatMessageDto(
    val id: String,
    val roomId: String,
    val fromRole: String,
    val text: String,
    val sentAt: String,
)

@Serializable
data class CheckoutRequest(val amount: Int, val method: String, val productId: Int)

@Serializable
data class PaymentDto(
    val id: String,
    val description: String = "",
    val amount: Int,
    val dateLabel: String = "",
    val type: String = "in",
    val status: String? = null,
    val pointEarned: Int? = null,
)

@Serializable
data class PointBalanceDto(val balance: Int)

@Serializable
data class PointHistoryDto(
    val id: String,
    val description: String,
    val point: Int,
    val dateLabel: String,
)

@Serializable
data class UsePointRequest(val productId: Int, val point: Int)

@Serializable
data class SubscribeRequest(val planId: Int, val method: String)

@Serializable
data class SubscriptionDto(
    val planId: Int,
    val status: String,
    val method: String? = null,
)

@Serializable
data class AppUsageDto(
    val appName: String,
    val durationSec: Int,
    val category: String? = null,
)

@Serializable
data class AppUsageReportRequest(val usages: List<AppUsageDto>)

/**
 * 부모가 보낸 원격 요청.
 *
 * 명세 4.x 에 아직 없는 **신규 제안 계약**이다(GAP-11: 202 만 있고 페이로드 미정의).
 * 백엔드와 키 이름을 맞출 때 이 DTO 와 [RemoteCommandAckRequest] 만 고치면 된다.
 */
@Serializable
data class RemoteCommandDto(
    val requestId: String,
    val type: String,
    val childId: String = "",
    val requestedBy: String? = null,
    val requestedAt: String? = null,
)

@Serializable
data class RemoteCommandAckRequest(
    val status: String,
    val message: String? = null,
)

@Serializable
data class ChildPhotoDto(
    val id: String,
    val capturedAt: String,
    val imageUrl: String? = null,
)

/**
 * 루키즈페이 용돈 지갑 (신규 제안 계약, GAP-05).
 *
 * 명세의 `points` 경로는 구독 적립 포인트라 「부모 → 자녀 송금」 필드가 없다.
 * 자녀 QR 결제는 송금자·수신자가 분명해야 하므로 별도 지갑 경로를 제안한다.
 */
@Serializable
data class AllowanceDto(
    val balance: Int,
    val updatedAt: String? = null,
)

@Serializable
data class AllowanceEntryDto(
    val id: String,
    val type: String,
    val amount: Int,
    val description: String = "",
    val dateLabel: String = "",
)

@Serializable
data class AllowanceTransferRequest(
    val amount: Int,
    val memo: String? = null,
)

@Serializable
data class AllowancePaymentRequest(
    val token: String,
    val amount: Int,
    val merchant: String? = null,
)

@Serializable
data class AllowancePaymentDto(
    val id: String,
    val amount: Int,
    val balanceAfter: Int,
    val approvedAt: String,
    val merchant: String? = null,
)
