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

fun LocationPoint.toDto(): com.hack.kidhack.data.remote.dto.LocationDto = com.hack.kidhack.data.remote.dto.LocationDto(lat, lng, recordedAt, label)

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
