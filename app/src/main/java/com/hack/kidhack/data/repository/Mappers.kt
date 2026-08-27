package com.hack.kidhack.data.repository

import com.hack.kidhack.data.remote.dto.AllowanceDto
import com.hack.kidhack.data.remote.dto.AllowanceEntryDto
import com.hack.kidhack.data.remote.dto.AllowancePaymentDto
import com.hack.kidhack.data.remote.dto.AppControlDto
import com.hack.kidhack.data.remote.dto.AppUsageDto
import com.hack.kidhack.data.remote.dto.CardDto
import com.hack.kidhack.data.remote.dto.ChargePrepareResponse
import com.hack.kidhack.data.remote.dto.ChatMessageDto
import com.hack.kidhack.data.remote.dto.ChatRoomDto
import com.hack.kidhack.data.remote.dto.ChildDto
import com.hack.kidhack.data.remote.dto.ChildPhotoDto
import com.hack.kidhack.data.remote.dto.CommentDto
import com.hack.kidhack.data.remote.dto.GeofenceDto
import com.hack.kidhack.data.remote.dto.InquiryAnswerDto
import com.hack.kidhack.data.remote.dto.InquiryDto
import com.hack.kidhack.data.remote.dto.InstalledAppDto
import com.hack.kidhack.data.remote.dto.LocationDto
import com.hack.kidhack.data.remote.dto.ChildAlertDto
import com.hack.kidhack.data.remote.dto.NotificationDto
import com.hack.kidhack.data.remote.dto.NotificationSettingsDto
import com.hack.kidhack.data.remote.dto.PairingCodeDto
import com.hack.kidhack.data.remote.dto.PaymentDto
import com.hack.kidhack.data.remote.dto.PointBalanceDto
import com.hack.kidhack.data.remote.dto.PointHistoryDto
import com.hack.kidhack.data.remote.dto.PostDto
import com.hack.kidhack.data.remote.dto.RemoteCommandDto
import com.hack.kidhack.data.remote.dto.ScreenshotDto
import com.hack.kidhack.data.remote.dto.SubscriptionDto
import com.hack.kidhack.data.remote.dto.LoginResponse
import com.hack.kidhack.data.remote.dto.TransferResponse
import com.hack.kidhack.data.remote.dto.UserDto
import com.hack.kidhack.domain.model.Allowance
import com.hack.kidhack.domain.model.AllowanceEntry
import com.hack.kidhack.domain.model.AllowancePayment
import com.hack.kidhack.domain.model.AllowanceType
import com.hack.kidhack.data.remote.dto.WalletDto
import com.hack.kidhack.data.remote.dto.WalletTransactionDto
import com.hack.kidhack.domain.model.AppNotification
import com.hack.kidhack.domain.model.ChildAlertEvent
import com.hack.kidhack.domain.model.AppUsage
import com.hack.kidhack.domain.model.Card
import com.hack.kidhack.domain.model.Charge
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.ChatRoom
import com.hack.kidhack.domain.model.ChildPhoto
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.model.Comment
import com.hack.kidhack.domain.model.Geofence
import com.hack.kidhack.domain.model.Inquiry
import com.hack.kidhack.domain.model.InquiryAnswer
import com.hack.kidhack.domain.model.LocationPoint
import com.hack.kidhack.domain.model.NotificationSettings
import com.hack.kidhack.domain.model.PairingCode
import com.hack.kidhack.domain.model.Payment
import com.hack.kidhack.domain.model.PointBalance
import com.hack.kidhack.domain.model.PointHistory
import com.hack.kidhack.domain.model.Post
import com.hack.kidhack.domain.model.RemoteCommand
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Screenshot
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.model.Subscription
import com.hack.kidhack.domain.model.TransferResult
import com.hack.kidhack.domain.model.User
import com.hack.kidhack.domain.model.Wallet
import com.hack.kidhack.domain.model.WalletTransaction

fun UserDto.toDomain(): User {
    val resolvedLogin = loginId?.takeIf { it.isNotBlank() }.orEmpty()
    val resolvedName = username?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }
        ?: nickname?.takeIf { it.isNotBlank() }
        ?: resolvedLogin
    return User(
        id = id,
        loginId = resolvedLogin,
        name = resolvedName,
        role = role.toRoleOrNull() ?: Role.PARENT,
        phone = phoneNumber?.takeIf { it.isNotBlank() } ?: phone.orEmpty(),
        email = email,
        profileImageUrl = profileImageUrl,
    )
}

/**
 * 서버가 role 을 어떤 표기로 주든 받아들인다.
 *
 * `Role.valueOf` 만 쓰면 스프링 시큐리티의 `ROLE_CHILD` 나 소문자 `child` 가 예외로 떨어지고,
 * 그때마다 조용히 PARENT 로 처리돼 **자녀 계정이 부모로 로그인되는** 문제가 생겼다.
 * 판별할 수 없으면 마음대로 정하지 말고 null 을 돌려준다. 무엇으로 대체할지는 호출자가 정한다.
 */
fun String?.toRoleOrNull(): Role? {
    val raw = this?.trim()?.uppercase()?.removePrefix("ROLE_")?.takeIf { it.isNotBlank() } ?: return null
    return when (raw) {
        "PARENT", "GUARDIAN" -> Role.PARENT
        "CHILD", "KID" -> Role.CHILD
        else -> Role.entries.firstOrNull { it.name == raw }
    }
}

fun LoginResponse.toSession(user: User): Session = Session(
    accessToken = resolvedAccessToken().orEmpty(),
    refreshToken = refreshToken.orEmpty(),
    user = user,
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

fun ChildAlertDto.toDomain(): ChildAlertEvent = ChildAlertEvent(id, type, title, body, occurredAt)

fun ChildAlertEvent.toDto(): ChildAlertDto = ChildAlertDto(id, type, title, body, occurredAt)

fun NotificationDto.toDomain(): AppNotification = AppNotification(
    id = id,
    type = type,
    title = title,
    body = body,
    createdAt = createdAt,
    read = read,
    childId = childId?.takeIf { it.isNotBlank() },
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
    imageUrls = imageUrls,
    links = links,
    comments = comments.map { it.toDomain() }
)

fun CommentDto.toDomain(): Comment = Comment(
    id = id,
    author = author,
    text = body,
    createdAt = createdAt
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
    fromRole = fromRole.toRoleOrNull() ?: Role.PARENT,
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

fun RemoteCommandDto.toDomain(): RemoteCommand = RemoteCommand(
    requestId = requestId,
    type = type.toRemoteCommandType(),
    childId = childId,
    requestedBy = requestedBy,
    requestedAt = requestedAt,
)

/** 서버/소켓이 쓸 법한 이름들을 넉넉하게 받아 준다. 모르는 값은 UNKNOWN 으로 떨어뜨린다. */
fun String?.toRemoteCommandType(): RemoteCommandType = when (this?.uppercase()) {
    "SCREEN_CAPTURE", "CAPTURE", "CAPTURE_REQUEST", "SCREENSHOT" -> RemoteCommandType.SCREEN_CAPTURE
    "CAMERA_SHOT", "CAMERA", "CAMERA_REQUEST", "PHOTO" -> RemoteCommandType.CAMERA_SHOT
    else -> RemoteCommandType.UNKNOWN
}

fun ChildPhotoDto.toDomain(): ChildPhoto = ChildPhoto(
    id = id,
    capturedAt = capturedAt,
    imageUrl = imageUrl,
)

fun AllowanceDto.toDomain(): Allowance = Allowance(balance = balance, updatedAt = updatedAt)

fun AllowanceEntryDto.toDomain(): AllowanceEntry = AllowanceEntry(
    id = id,
    type = runCatching { AllowanceType.valueOf(type.uppercase()) }
        .getOrDefault(AllowanceType.PAYMENT),
    amount = amount,
    description = description,
    dateLabel = dateLabel,
)

fun AllowancePaymentDto.toDomain(): AllowancePayment = AllowancePayment(
    id = id,
    amount = amount,
    balanceAfter = balanceAfter,
    approvedAt = approvedAt,
    merchant = merchant,
 )

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

fun InquiryDto.toDomain(): Inquiry = Inquiry(
    id = id,
    title = title,
    body = body,
    status = status,
    createdAt = createdAt,
    imageUrls = imageUrls,
    links = links,
    answers = answers.map { it.toDomain() }
)

fun InquiryAnswerDto.toDomain(): InquiryAnswer = InquiryAnswer(
    id = id,
    author = author,
    body = body,
    createdAt = createdAt
)

fun WalletDto.toDomain(): Wallet = Wallet(
    walletId = walletId,
    balance = balance,
    updatedAt = updatedAt
)

fun WalletTransactionDto.toDomain(): WalletTransaction = WalletTransaction(
    txId = txId,
    type = type,
    amount = amount,
    balanceAfter = balanceAfter,
    refId = refId,
    createdAt = createdAt
)

fun ChargePrepareResponse.toDomain(): Charge = Charge(
    chargeId = chargeId,
    amount = amount,
    status = status,
    paymentKey = paymentKey,
    method = ""
)

fun CardDto.toDomain(): Card = Card(
    cardId = cardId,
    last4 = last4,
    expiry = expiry,
    ownerName = ownerName
)

fun TransferResponse.toDomain(): TransferResult = TransferResult(
    parentBalance = parentBalance,
    childBalance = childBalance,
    txId = txId
)
