package com.hack.kidhack.data.repository

import com.hack.kidhack.data.remote.dto.AppControlDto
import com.hack.kidhack.data.remote.dto.AppUsageDto
import com.hack.kidhack.data.remote.dto.ChatMessageDto
import com.hack.kidhack.data.remote.dto.ChatRoomDto
import com.hack.kidhack.data.remote.dto.ChildDto
import com.hack.kidhack.data.remote.dto.GeofenceDto
import com.hack.kidhack.data.remote.dto.InstalledAppDto
import com.hack.kidhack.data.remote.dto.LocationDto
import com.hack.kidhack.data.remote.dto.NotificationDto
import com.hack.kidhack.data.remote.dto.NotificationSettingsDto
import com.hack.kidhack.data.remote.dto.PairingCodeDto
import com.hack.kidhack.data.remote.dto.PaymentDto
import com.hack.kidhack.data.remote.dto.PointBalanceDto
import com.hack.kidhack.data.remote.dto.PointHistoryDto
import com.hack.kidhack.data.remote.dto.PostDto
import com.hack.kidhack.data.remote.dto.ScreenshotDto
import com.hack.kidhack.data.remote.dto.SubscriptionDto
import com.hack.kidhack.data.remote.dto.TokenResponse
import com.hack.kidhack.data.remote.dto.UserDto
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.LocationPoint
import com.hack.kidhack.domain.model.NotificationSettings
import com.hack.kidhack.domain.model.PairingCode
import com.hack.kidhack.domain.model.Payment
import com.hack.kidhack.domain.model.PointBalance
import com.hack.kidhack.domain.model.PointHistory
import com.hack.kidhack.domain.model.Post
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Screenshot
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.model.Subscription
import com.hack.kidhack.domain.model.User

fun UserDto.toDomain(): User = User(
    id = id,
    loginId = loginId,
    name = name,
    role = runCatching { Role.valueOf(role) }.getOrDefault(Role.PARENT),
    phone = phone,
    email = email,
    profileImageUrl = profileImageUrl,
)

fun TokenResponse.toSession(): Session = Session(
    accessToken = accessToken,
    refreshToken = refreshToken,
    user = user.toDomain(),
)

fun ChildDto.toDomain(): ChildProfile = ChildProfile(
    id = id,
    name = name,
    alias = alias,
    ageLabel = ageLabel,
    school = school,
    birthDate = birthDate,
    deviceModel = deviceModel,
    profileImageUrl = profileImageUrl,
    online = online,
)

fun LocationDto.toDomain(): LocationPoint = LocationPoint(
    lat = lat,
    lng = lng,
    recordedAt = recordedAt,
    label = label,
)

fun LocationPoint.toDto(): LocationDto = LocationDto(lat, lng, recordedAt, label)

fun GeofenceDto.toDomain(): Geofence = Geofence(
    id = id.orEmpty(),
    name = name,
    lat = lat,
    lng = lng,
    radius = radius,
    notifyOnArrival = notifyOnArrival,
)

fun NotificationDto.toDomain(): AppNotification = AppNotification(
    id = id,
    type = type,
    title = title,
    body = body,
    createdAt = createdAt,
    read = read,
)

fun NotificationSettingsDto.toDomain(): NotificationSettings = NotificationSettings(
    location = location,
    harmfulAccess = harmfulAccess,
    appUsage = appUsage,
    notice = notice,
)

fun NotificationSettings.toDto(): NotificationSettingsDto = NotificationSettingsDto(
    location = location,
    harmfulAccess = harmfulAccess,
    appUsage = appUsage,
    notice = notice,
)

fun ScreenshotDto.toDomain(): Screenshot = Screenshot(
    id = id,
    capturedAt = capturedAt,
    description = description,
    trigger = trigger,
    imageUrl = imageUrl,
)

fun PostDto.toDomain(): Post = Post(
    id = id,
    title = title,
    author = author,
    commentCount = commentCount,
    likeCount = likeCount,
    createdAtLabel = createdAtLabel,
    liked = liked,
)

fun ChatRoomDto.toDomain(): ChatRoom = ChatRoom(
    id = id,
    title = title,
    lastMessage = lastMessage,
    lastAt = lastAt,
    online = online,
)

fun ChatMessageDto.toDomain(): ChatMessage = ChatMessage(
    id = id,
    roomId = roomId,
    fromRole = runCatching { Role.valueOf(fromRole) }.getOrDefault(Role.PARENT),
    text = text,
    sentAt = sentAt,
)

fun PaymentDto.toDomain(): Payment = Payment(
    id = id,
    description = description,
    amount = amount,
    dateLabel = dateLabel,
    type = type,
)

fun PointBalanceDto.toDomain(): PointBalance = PointBalance(balance)

fun PointHistoryDto.toDomain(): PointHistory = PointHistory(id, description, point, dateLabel)

fun SubscriptionDto.toDomain(): Subscription = Subscription(planId, status, method)

fun AppUsageDto.toDomain(): AppUsage = AppUsage(appName, durationSec, category)

fun PairingCodeDto.toDomain(): PairingCode = PairingCode(pairingCode, expiresIn)

fun AppControlDto.toDomain(): com.hack.kidhack.domain.model.AppControlItem = com.hack.kidhack.domain.model.AppControlItem(
    packageName = packageName,
    name = appName,
    icon = icon ?: "❓",
    category = category ?: "기타",
    blocked = blocked,
    limitMinutes = limitMinutes,
    limit = if (blocked) "차단" else if (limitMinutes == null || limitMinutes == 0) "무제한" else "${limitMinutes / 60}시간 ${limitMinutes % 60}분"
)

fun InstalledAppDto.toDomain(): com.hack.kidhack.domain.model.InstalledApp = com.hack.kidhack.domain.model.InstalledApp(
    packageName = packageName,
    appName = appName,
    category = category
)
