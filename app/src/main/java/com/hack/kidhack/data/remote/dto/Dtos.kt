package com.hack.kidhack.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/** Accepts JSON string or number as a string (signup `id` is numeric in v0.2). */
internal object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        val primitive = jsonDecoder.decodeJsonElement() as? JsonPrimitive ?: return ""
        return primitive.content
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

@Serializable
data class LoginRequest(val loginId: String, val password: String)

@Serializable
data class LoginResponse(
    val token: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val loginId: String? = null,
    val username: String? = null,
    val role: String? = null,
    val user: UserDto? = null,
) {
    fun resolvedAccessToken(): String? =
        accessToken?.takeIf { it.isNotBlank() } ?: token?.takeIf { it.isNotBlank() }
}

@Serializable
data class SignupRequest(
    val loginId: String,
    val username: String,
    val password: String,
    val nickname: String? = null,
    val email: String? = null,
    val phoneNumber: String,
    val birthDate: String,
    val address: String? = null,
    val profileImageUrl: String? = null,
    val role: String,
    val parentId: Long? = null,
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
data class FindIdResponse(val loginId: String)

@Serializable
data class UserDto(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String = "",
    val username: String? = null,
    val loginId: String? = null,
    val nickname: String? = null,
    val name: String? = null,
    val role: String? = null,
    val phoneNumber: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val profileImageUrl: String? = null,
)

@Serializable
data class UserUpdateRequest(
    val nickname: String? = null,
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val profileImageUrl: String? = null,
)

@Serializable
data class ChangePasswordRequest(
    val newPassword: String,
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
    val birthDate: String? = null,
    val school: String? = null,
)

/** POST /location/track 요청. 명세상 좌표가 문자열이다(places는 숫자). */
@Serializable
data class LocationTrackRequest(
    val childId: Long,
    val latitude: String,
    val longitude: String,
)

@Serializable
data class LocationTrackResponse(
    val id: Long,
    val childId: Long,
    val latitude: String,
    val longitude: String,
    val segmentStart: String? = null,
    val segmentEnd: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class RealtimeToggleResponse(
    val childId: Long,
    val realtimeEnabled: Boolean,
)

@Serializable
data class GpsStatusRequest(val gpsEnabled: Boolean, val reportedAt: String)

/** POST /location/places 요청. `type` = GEOFENCE | DESTINATION. */
@Serializable
data class PlaceRequest(
    val childId: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val type: String,
)

@Serializable
data class PlaceResponse(
    val id: Long,
    val childId: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val type: String,
)

/** GET /location/events/{childId}. `eventType` = ENTER | EXIT | ARRIVED. */
@Serializable
data class LocationEventDto(
    val id: Long,
    val childId: Long,
    val placeId: Long,
    val eventType: String,
    val occurredAt: String,
)

@Serializable
data class NotificationDto(
    val id: String,
    val recipientId: String,
    val type: String,
    val message: String,
    val read: Boolean = false,
    val createdAt: String,
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
data class CommentDto(
    val id: String,
    val author: String,
    val body: String,
    val createdAt: String,
)

@Serializable
data class PostDto(
    @SerialName("postId") val id: String,
    val title: String,
    @SerialName("authorNickname") val author: String,
    @SerialName("createdAt") val createdAtLabel: String,
    val likeCount: Int = 0,
    // TODO: 명세서 목록/상세 응답에 commentCount 없음 — 서버 미제공 시 0 기본값 유지
    val commentCount: Int = 0,
    val liked: Boolean = false,
    val imageUrls: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val comments: List<CommentDto> = emptyList(),
)

@Serializable
data class CreatePostRequest(
    val title: String,
    val body: String,
    val imageUrls: List<String>? = null,
    val links: List<String>? = null,
)

@Serializable
data class CommentRequest(val body: String)

@Serializable
data class ReportRequest(val reason: String, val detail: String? = null)

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

@Serializable
data class InstalledAppDto(val packageName: String, val appName: String, val category: String?)

@Serializable
data class InstalledAppsResponse(val apps: List<InstalledAppDto>)

@Serializable
data class AppControlDto(
    val packageName: String,
    val appName: String,
    val icon: String? = null,
    val category: String? = null,
    val blocked: Boolean,
    val limitMinutes: Int? = null,
)

@Serializable
data class AppControlUpdateRequest(
    val packageName: String,
    val blocked: Boolean,
    val limitMinutes: Int? = null,
)

@Serializable
data class InquiryDto(
    val id: String,
    val title: String,
    val body: String,
    val status: String,
    val createdAt: String,
    val imageUrls: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val answers: List<InquiryAnswerDto> = emptyList(),
)

@Serializable
data class InquiryAnswerDto(
    val id: String,
    val author: String,
    val body: String,
    val createdAt: String,
)

@Serializable
data class CreateInquiryRequest(
    val title: String,
    val body: String,
    val imageUrls: List<String>? = null,
    val links: List<String>? = null,
)

@Serializable
data class ChargePrepareRequest(val amount: Int, val method: String, val cardId: String)

@Serializable
data class ChargePrepareResponse(
    val chargeId: String,
    val paymentKey: String,
    val amount: Int,
    val status: String
)

@Serializable
data class ChargeConfirmRequest(
    val chargeId: String,
    val paymentKey: String,
    val amount: Int
)

@Serializable
data class ChargeConfirmResponse(
    val chargeId: String,
    val status: String,
    val walletBalance: Int
)

@Serializable
data class ChargeCancelRequest(val reason: String)

@Serializable
data class WalletDto(
    val walletId: String,
    val balance: Int,
    val updatedAt: String
)

@Serializable
data class WalletTransactionDto(
    val txId: String,
    val type: String,
    val amount: Int,
    val balanceAfter: Int,
    val refId: String?,
    val createdAt: String
)

@Serializable
data class TransferRequest(
    val childId: String,
    val amount: Int,
    val memo: String? = null
)

@Serializable
data class TransferResponse(
    val parentBalance: Int,
    val childBalance: Int,
    val txId: String
)

@Serializable
data class CardDto(
    val cardId: String,
    val last4: String,
    val expiry: String,
    val ownerName: String
)

@Serializable
data class CardRegisterRequest(
    val cardNumber: String,
    val expiry: String,
    val cvc: String,
    val cardPassword2: String,
    val birthDate: String,
    val ownerName: String
)
