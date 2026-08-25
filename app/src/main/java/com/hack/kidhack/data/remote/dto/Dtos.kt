package com.hack.kidhack.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val loginId: String, val password: String)

@Serializable
data class SignupRequest(
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
