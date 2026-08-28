package com.hack.kidhack.data.remote.api

import com.hack.kidhack.data.remote.ApiEnvelope
import com.hack.kidhack.data.remote.PageResponse
import com.hack.kidhack.data.remote.dto.AllowanceDto
import com.hack.kidhack.data.remote.dto.AllowanceEntryDto
import com.hack.kidhack.data.remote.dto.AllowancePaymentDto
import com.hack.kidhack.data.remote.dto.AllowancePaymentRequest
import com.hack.kidhack.data.remote.dto.AllowanceTransferRequest
import com.hack.kidhack.data.remote.dto.AppControlDto
import com.hack.kidhack.data.remote.dto.AppControlUpdateRequest
import com.hack.kidhack.data.remote.dto.AppUsageAggregateResponse
import com.hack.kidhack.data.remote.dto.AppUsageReportRequest
import com.hack.kidhack.data.remote.dto.BlocklistAddRequest
import com.hack.kidhack.data.remote.dto.BlocklistResponse
import com.hack.kidhack.data.remote.dto.CardDto
import com.hack.kidhack.data.remote.dto.CardRegisterRequest
import com.hack.kidhack.data.remote.dto.ChangePasswordRequest
import com.hack.kidhack.data.remote.dto.ChargeCancelRequest
import com.hack.kidhack.data.remote.dto.ChargeConfirmRequest
import com.hack.kidhack.data.remote.dto.ChargeConfirmResponse
import com.hack.kidhack.data.remote.dto.ChargePrepareRequest
import com.hack.kidhack.data.remote.dto.ChargePrepareResponse
import com.hack.kidhack.data.remote.dto.ChatMessageDto
import com.hack.kidhack.data.remote.dto.ChatRoomDto
import com.hack.kidhack.data.remote.dto.CheckoutRequest
import com.hack.kidhack.data.remote.dto.CreateChatRoomRequest
import com.hack.kidhack.data.remote.dto.ChildDto
import com.hack.kidhack.data.remote.dto.ChildPhotoDto
import com.hack.kidhack.data.remote.dto.ChildUpdateRequest
import com.hack.kidhack.data.remote.dto.CommentRequest
import com.hack.kidhack.data.remote.dto.CreateInquiryRequest
import com.hack.kidhack.data.remote.dto.CreatePostRequest
import com.hack.kidhack.data.remote.dto.FindIdRequest
import com.hack.kidhack.data.remote.dto.FindIdResponse
import com.hack.kidhack.data.remote.dto.DashboardResponse
import com.hack.kidhack.data.remote.dto.DeviceEventRequest
import com.hack.kidhack.data.remote.dto.PushTokenRequest
import com.hack.kidhack.data.remote.dto.HarmfulAccessRequest
import com.hack.kidhack.data.remote.dto.InquiryDto
import com.hack.kidhack.data.remote.dto.InstalledAppDto
import com.hack.kidhack.data.remote.dto.InstalledAppsResponse
import com.hack.kidhack.data.remote.dto.LocationEventDto
import com.hack.kidhack.data.remote.dto.LocationTrackRequest
import com.hack.kidhack.data.remote.dto.LocationTrackResponse
import com.hack.kidhack.data.remote.dto.LoginRequest
import com.hack.kidhack.data.remote.dto.LoginResponse
import com.hack.kidhack.data.remote.dto.ChildAlertDto
import com.hack.kidhack.data.remote.dto.NotificationDto
import com.hack.kidhack.data.remote.dto.NotificationSettingsDto
import com.hack.kidhack.data.remote.dto.PairingCodeDto
import com.hack.kidhack.data.remote.dto.PairingConnectRequest
import com.hack.kidhack.data.remote.dto.PaymentDto
import com.hack.kidhack.data.remote.dto.PlaceRequest
import com.hack.kidhack.data.remote.dto.PlaceResponse
import com.hack.kidhack.data.remote.dto.PointBalanceDto
import com.hack.kidhack.data.remote.dto.PointHistoryDto
import com.hack.kidhack.data.remote.dto.PostDto
import com.hack.kidhack.data.remote.dto.RealtimeToggleResponse
import com.hack.kidhack.data.remote.dto.RefreshRequest
import com.hack.kidhack.data.remote.dto.RemoteCommandAckRequest
import com.hack.kidhack.data.remote.dto.ReportRequest
import com.hack.kidhack.data.remote.dto.ResetPasswordRequest
import com.hack.kidhack.data.remote.dto.ScreenshotDto
import com.hack.kidhack.data.remote.dto.SignupRequest
import com.hack.kidhack.data.remote.dto.UsePointRequest
import com.hack.kidhack.data.remote.dto.TransferRequest
import com.hack.kidhack.data.remote.dto.TransferResponse
import com.hack.kidhack.data.remote.dto.UserDto
import com.hack.kidhack.data.remote.dto.UserUpdateRequest
import com.hack.kidhack.data.remote.dto.WalletDto
import com.hack.kidhack.data.remote.dto.WalletTransactionDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface AuthApi {
    @POST("auth/signup")
    suspend fun signup(@Body body: SignupRequest): ApiEnvelope<UserDto>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): ApiEnvelope<LoginResponse>

    @POST("auth/logout")
    suspend fun logout(): ApiEnvelope<Unit>

    @POST("auth/token/refresh")
    suspend fun refresh(@Body body: RefreshRequest): ApiEnvelope<LoginResponse>

    @POST("auth/find-id")
    suspend fun findId(@Body body: FindIdRequest): ApiEnvelope<FindIdResponse>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): ApiEnvelope<Unit>
}

interface UserApi {
    @GET("users/me")
    suspend fun me(): ApiEnvelope<UserDto>

    @PATCH("users/me")
    suspend fun updateMe(@Body body: UserUpdateRequest): ApiEnvelope<UserDto>

    @PATCH("users/me/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): ApiEnvelope<Unit>

    @DELETE("users/me")
    suspend fun deleteMe(): ApiEnvelope<Unit>

    @GET("users/me/activities")
    suspend fun activities(
        @Query("type") type: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<PageResponse<PostDto>>

    @GET("users/me/notification-settings")
    suspend fun notificationSettings(): ApiEnvelope<NotificationSettingsDto>

    @PATCH("users/me/notification-settings")
    suspend fun patchNotificationSettings(
        @Body body: NotificationSettingsDto,
    ): ApiEnvelope<NotificationSettingsDto>
}

interface PairingApi {
    @POST("pairing/code")
    suspend fun issueCode(): ApiEnvelope<PairingCodeDto>

    @POST("pairing/connect")
    suspend fun connect(@Body body: PairingConnectRequest): ApiEnvelope<UserDto>
}

interface ChildApi {
    @GET("children")
    suspend fun children(): ApiEnvelope<List<ChildDto>>

    @GET("children/{childId}")
    suspend fun child(@Path("childId") childId: String): ApiEnvelope<ChildDto>

    @PATCH("children/{childId}")
    suspend fun update(
        @Path("childId") childId: String,
        @Body body: ChildUpdateRequest,
    ): ApiEnvelope<ChildDto>
}

interface LocationApi {
    /** 자녀 앱 전용(CHILD). 좌표를 올리면 서버가 지오펜스/도착을 판정한다. */
    @POST("location/track")
    suspend fun track(@Body body: LocationTrackRequest): ApiEnvelope<LocationTrackResponse>

    /** 최신순 세그먼트. 실시간 ON이면 첫 항목이 현재 위치. */
    @GET("location/track/{childId}")
    suspend fun history(@Path("childId") childId: String): ApiEnvelope<List<LocationTrackResponse>>

    /** body 없이 query `enabled`만 사용한다. */
    @PATCH("location/tracking/{childId}/realtime")
    suspend fun toggleRealtime(
        @Path("childId") childId: String,
        @Query("enabled") enabled: Boolean,
    ): ApiEnvelope<RealtimeToggleResponse>

}

interface DeviceApi {
    /** 기기 전원/GPS 꺼짐 보고. type = "POWER_OFF" | "GPS_OFF" (명세 4.16). */
    @POST("device/event")
    suspend fun reportEvent(@Body body: DeviceEventRequest): ApiEnvelope<Unit>

    /** FCM 토큰 등록·해제 (신규 제안 계약). */
    @POST("devices/push-token")
    suspend fun registerPushToken(@Body body: PushTokenRequest): ApiEnvelope<Unit>

    @HTTP(method = "DELETE", path = "devices/push-token", hasBody = true)
    suspend fun unregisterPushToken(@Body body: PushTokenRequest): ApiEnvelope<Unit>
}

interface DashboardApi {
    /** 위치·실시간·최근알림3·차단·공간 통합 조회 (명세 4.3). */
    @GET("children/{childId}/dashboard")
    suspend fun get(@Path("childId") childId: String): ApiEnvelope<DashboardResponse>
}

interface PlaceApi {
    @POST("location/places")
    suspend fun create(@Body body: PlaceRequest): ApiEnvelope<PlaceResponse>

    @GET("location/places/{childId}")
    suspend fun list(@Path("childId") childId: String): ApiEnvelope<List<PlaceResponse>>

    /** v0.2 명세에 없는 경로. 백엔드에 신설 요청한 계약을 선반영한 것이다(docs/spec-gaps.md). */
    @DELETE("location/places/{placeId}")
    suspend fun delete(@Path("placeId") placeId: String): ApiEnvelope<Unit>
}

interface LocationEventApi {
    @GET("location/events/{childId}")
    suspend fun events(@Path("childId") childId: String): ApiEnvelope<List<LocationEventDto>>
}

interface NotificationApi {
    /** 명세 4.5. childId는 서버가 지원하면 자녀별로 걸러 받는다. */
    @GET("notifications/{recipientId}")
    suspend fun list(
        @Path("recipientId") recipientId: String,
        @Query("childId") childId: String? = null,
    ): ApiEnvelope<List<NotificationDto>>

    @PATCH("notifications/{notificationId}")
    suspend fun markRead(@Path("notificationId") notificationId: String): ApiEnvelope<Unit>
}

/**
 * 자녀 안전 사건 동기화 (docs/child-push-contract.md 4절 제안 계약).
 * 자녀 앱이 upload, 보호자 앱이 list 를 쓴다. 서버에 아직 없어도 앱은 정상 동작한다.
 */
interface ChildAlertApi {
    @POST("children/{childId}/alerts")
    suspend fun upload(
        @Path("childId") childId: String,
        @Body body: List<ChildAlertDto>,
    ): ApiEnvelope<Unit>

    @GET("children/{childId}/alerts")
    suspend fun list(
        @Path("childId") childId: String,
        @Query("size") size: Int = 50,
    ): ApiEnvelope<List<ChildAlertDto>>
}

interface BlocklistApi {
    @GET("children/{childId}/blocklist")
    suspend fun get(@Path("childId") childId: String): ApiEnvelope<BlocklistResponse>

    @POST("children/{childId}/blocklist")
    suspend fun add(
        @Path("childId") childId: String,
        @Body body: BlocklistAddRequest,
    ): ApiEnvelope<Unit>

    @DELETE("children/{childId}/blocklist/{itemId}")
    suspend fun delete(
        @Path("childId") childId: String,
        @Path("itemId") itemId: String,
    ): ApiEnvelope<Unit>

    @POST("harmful-access/report")
    suspend fun report(@Body body: HarmfulAccessRequest): ApiEnvelope<Unit>
}

interface ScreenshotApi {
    @GET("children/{childId}/screenshots")
    suspend fun list(
        @Path("childId") childId: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<PageResponse<ScreenshotDto>>

    @Multipart
    @POST("children/{childId}/screenshots")
    suspend fun upload(
        @Path("childId") childId: String,
        @Part file: MultipartBody.Part,
        @Part("capturedAt") capturedAt: RequestBody,
        @Part("trigger") trigger: RequestBody,
    ): ApiEnvelope<ScreenshotDto>

    @POST("children/{childId}/screenshots/request")
    suspend fun requestCapture(@Path("childId") childId: String): ApiEnvelope<Unit>
}

interface CommunityApi {
    @GET("community/posts")
    suspend fun posts(
        @Query("keyword") keyword: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<PageResponse<PostDto>>

    @POST("community/posts")
    suspend fun create(@Body body: CreatePostRequest): ApiEnvelope<PostDto>

    @GET("community/posts/{postId}")
    suspend fun post(@Path("postId") postId: String): ApiEnvelope<PostDto>

    @PATCH("community/posts/{postId}")
    suspend fun update(
        @Path("postId") postId: String,
        @Body body: CreatePostRequest,
    ): ApiEnvelope<PostDto>

    @DELETE("community/posts/{postId}")
    suspend fun delete(@Path("postId") postId: String): ApiEnvelope<Unit>

    @POST("community/posts/{postId}/comments")
    suspend fun comment(
        @Path("postId") postId: String,
        @Body body: CommentRequest,
    ): ApiEnvelope<Unit>

    @POST("community/posts/{postId}/like")
    suspend fun like(@Path("postId") postId: String): ApiEnvelope<Unit>

    @POST("community/posts/{postId}/report")
    suspend fun reportPost(
        @Path("postId") postId: String,
        @Body body: ReportRequest,
    ): ApiEnvelope<Unit>

    @POST("community/posts/{postId}/comments/{commentId}/report")
    suspend fun reportComment(
        @Path("postId") postId: String,
        @Path("commentId") commentId: String,
        @Body body: ReportRequest,
    ): ApiEnvelope<Unit>

    @Multipart
    @POST("community/attachments")
    suspend fun uploadAttachment(@Part file: MultipartBody.Part): ApiEnvelope<Unit>
}

interface ChatApi {
    @POST("chat/rooms")
    suspend fun createOrGetRoom(@Body body: CreateChatRoomRequest): ApiEnvelope<ChatRoomDto>

    @GET("chat/rooms")
    suspend fun rooms(): ApiEnvelope<List<ChatRoomDto>>

    @GET("chat/rooms/{roomId}/messages")
    suspend fun messages(
        @Path("roomId") roomId: String,
        @Query("before") before: String? = null,
        @Query("size") size: Int = 30,
        @Query("page") page: Int = 0,
    ): ApiEnvelope<PageResponse<ChatMessageDto>>
}

interface PaymentApi {
    @POST("payments/checkout")
    suspend fun checkout(@Body body: CheckoutRequest): ApiEnvelope<PaymentDto>

    @GET("payments/history")
    suspend fun history(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<PageResponse<PaymentDto>>
}

interface PointApi {
    @GET("points/balance")
    suspend fun balance(): ApiEnvelope<PointBalanceDto>

    @GET("points/history")
    suspend fun history(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<PageResponse<PointHistoryDto>>

    @POST("points/use")
    suspend fun use(@Body body: UsePointRequest): ApiEnvelope<Unit>
}

interface AppUsageApi {
    @POST("monitoring/usage")
    suspend fun report(@Body body: AppUsageReportRequest): ApiEnvelope<Int>

    @GET("monitoring/usage/{childId}")
    suspend fun list(
        @Path("childId") childId: String,
        @Query("groupBy") groupBy: String = "DAY",
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): ApiEnvelope<AppUsageAggregateResponse>
}

/**
 * 부모 요청 처리 결과 응답 (신규 제안 계약, GAP-11).
 *
 * 소켓이 꺼진 상태로 FCM 이 도착하면 `socket.ack` 이 no-op 이므로, 결과를 서버로
 * 되돌릴 경로는 이 REST ack 뿐이다. 서버가 아직 제공하지 않으면 404 를 무시한다.
 *
 * 폴링(`GET .../commands/pending`)은 서버에 대응 경로가 없어 제거했다(2026-08-28).
 */
interface CommandApi {
    @POST("children/{childId}/commands/{requestId}/ack")
    suspend fun ack(
        @Path("childId") childId: String,
        @Path("requestId") requestId: String,
        @Body body: RemoteCommandAckRequest,
    ): ApiEnvelope<Unit>
}

/** 자녀 카메라 촬영본 업로드·조회·요청 (신규 제안 계약, ScreenshotApi 와 대칭). */
interface PhotoApi {
    @GET("children/{childId}/photos")
    suspend fun list(
        @Path("childId") childId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<PageResponse<ChildPhotoDto>>

    @Multipart
    @POST("children/{childId}/photos")
    suspend fun upload(
        @Path("childId") childId: String,
        @Part file: MultipartBody.Part,
        @Part("capturedAt") capturedAt: RequestBody,
        @Part("requestId") requestId: RequestBody?,
    ): ApiEnvelope<ChildPhotoDto>

    /** 202 를 기대한다. 서버는 자녀 소켓/pending 큐로 CAMERA_REQUEST 를 밀어 줘야 한다. */
    @POST("children/{childId}/photos/request")
    suspend fun requestShot(@Path("childId") childId: String): ApiEnvelope<Unit>
}

/** 루키즈페이 용돈 지갑 (신규 제안 계약, GAP-05). */
interface AllowanceApi {
    @GET("children/{childId}/allowance")
    suspend fun balance(@Path("childId") childId: String): ApiEnvelope<AllowanceDto>

    @GET("children/{childId}/allowance/history")
    suspend fun history(
        @Path("childId") childId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
    ): ApiEnvelope<PageResponse<AllowanceEntryDto>>

    /** 부모 전용: 용돈 보내기 */
    @POST("children/{childId}/allowance/send")
    suspend fun send(
        @Path("childId") childId: String,
        @Body body: AllowanceTransferRequest,
    ): ApiEnvelope<AllowanceDto>

    /** 부모 전용: 용돈 회수하기 */
    @POST("children/{childId}/allowance/withdraw")
    suspend fun withdraw(
        @Path("childId") childId: String,
        @Body body: AllowanceTransferRequest,
    ): ApiEnvelope<AllowanceDto>

    /** 자녀 전용: QR 결제 승인 */
    @POST("children/{childId}/allowance/payments")
    suspend fun pay(
        @Path("childId") childId: String,
        @Body body: AllowancePaymentRequest,
    ): ApiEnvelope<AllowancePaymentDto>
}

interface AppControlApi {
    @GET("children/{childId}/installed-apps")
    suspend fun getInstalledApps(@Path("childId") childId: String): ApiEnvelope<InstalledAppsResponse>

    @GET("children/{childId}/app-control")
    suspend fun getAppControlList(@Path("childId") childId: String): ApiEnvelope<List<AppControlDto>>

    @POST("children/{childId}/app-control")
    suspend fun updateAppControl(
        @Path("childId") childId: String,
        @Body body: AppControlUpdateRequest,
    ): ApiEnvelope<AppControlDto>

    @DELETE("children/{childId}/app-control/{packageName}")
    suspend fun deleteAppControl(
        @Path("childId") childId: String,
        @Path("packageName") packageName: String,
    ): ApiEnvelope<Unit>
}

interface InquiryApi {
    @GET("inquiries")
    suspend fun getInquiries(): ApiEnvelope<List<InquiryDto>>

    @POST("inquiries")
    suspend fun createInquiry(@Body body: CreateInquiryRequest): ApiEnvelope<InquiryDto>

    @GET("inquiries/{id}")
    suspend fun getInquiry(@Path("id") id: String): ApiEnvelope<InquiryDto>
}

interface PaymentChargeApi {
    @POST("payments/charge/prepare")
    suspend fun prepare(@Body body: ChargePrepareRequest): ApiEnvelope<ChargePrepareResponse>

    @POST("payments/charge/confirm")
    suspend fun confirm(@Body body: ChargeConfirmRequest): ApiEnvelope<ChargeConfirmResponse>

    @GET("payments/charges")
    suspend fun list(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): ApiEnvelope<PageResponse<ChargePrepareResponse>>

    @POST("payments/charges/{chargeId}/cancel")
    suspend fun cancel(@Path("chargeId") chargeId: String, @Body body: ChargeCancelRequest): ApiEnvelope<Unit>
}

interface WalletApi {
    @GET("wallets/me")
    suspend fun me(): ApiEnvelope<WalletDto>

    @GET("wallets/me/transactions")
    suspend fun transactions(
        @Query("type") type: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): ApiEnvelope<PageResponse<WalletTransactionDto>>

    @POST("wallets/transfer")
    suspend fun transfer(@Body body: TransferRequest): ApiEnvelope<TransferResponse>

    @POST("wallets/reclaim")
    suspend fun reclaim(@Body body: TransferRequest): ApiEnvelope<TransferResponse>

    @GET("children/{childId}/wallet")
    suspend fun childWallet(@Path("childId") childId: String): ApiEnvelope<WalletDto>
}

interface CardApi {
    @POST("payments/cards")
    suspend fun register(@Body body: CardRegisterRequest): ApiEnvelope<CardDto>

    @GET("payments/cards")
    suspend fun list(): ApiEnvelope<List<CardDto>>

    @DELETE("payments/cards/{cardId}")
    suspend fun delete(@Path("cardId") cardId: String): ApiEnvelope<Unit>
}
