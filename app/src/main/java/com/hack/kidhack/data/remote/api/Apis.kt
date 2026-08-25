package com.hack.kidhack.data.remote.api

import com.hack.kidhack.data.remote.ApiEnvelope
import com.hack.kidhack.data.remote.PageResponse
import com.hack.kidhack.data.remote.dto.AppUsageDto
import com.hack.kidhack.data.remote.dto.AppUsageReportRequest
import com.hack.kidhack.data.remote.dto.BlocklistAddRequest
import com.hack.kidhack.data.remote.dto.BlocklistResponse
import com.hack.kidhack.data.remote.dto.ChatMessageDto
import com.hack.kidhack.data.remote.dto.ChatRoomDto
import com.hack.kidhack.data.remote.dto.CheckoutRequest
import com.hack.kidhack.data.remote.dto.ChildDto
import com.hack.kidhack.data.remote.dto.ChildUpdateRequest
import com.hack.kidhack.data.remote.dto.CommentRequest
import com.hack.kidhack.data.remote.dto.CreatePostRequest
import com.hack.kidhack.data.remote.dto.FindIdRequest
import com.hack.kidhack.data.remote.dto.FindIdResponse
import com.hack.kidhack.data.remote.dto.GeofenceDto
import com.hack.kidhack.data.remote.dto.GpsStatusRequest
import com.hack.kidhack.data.remote.dto.HarmfulAccessRequest
import com.hack.kidhack.data.remote.dto.LocationDto
import com.hack.kidhack.data.remote.dto.LoginRequest
import com.hack.kidhack.data.remote.dto.LoginResponse
import com.hack.kidhack.data.remote.dto.NotificationDto
import com.hack.kidhack.data.remote.dto.NotificationSettingsDto
import com.hack.kidhack.data.remote.dto.PairingCodeDto
import com.hack.kidhack.data.remote.dto.PairingConnectRequest
import com.hack.kidhack.data.remote.dto.PaymentDto
import com.hack.kidhack.data.remote.dto.PointBalanceDto
import com.hack.kidhack.data.remote.dto.PointHistoryDto
import com.hack.kidhack.data.remote.dto.PostDto
import com.hack.kidhack.data.remote.dto.RefreshRequest
import com.hack.kidhack.data.remote.dto.ResetPasswordRequest
import com.hack.kidhack.data.remote.dto.ScreenshotDto
import com.hack.kidhack.data.remote.dto.SignupRequest
import com.hack.kidhack.data.remote.dto.SubscribeRequest
import com.hack.kidhack.data.remote.dto.SubscriptionDto
import com.hack.kidhack.data.remote.dto.UserDto
import com.hack.kidhack.data.remote.dto.UserUpdateRequest
import com.hack.kidhack.data.remote.dto.UsePointRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
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
    @GET("children/{childId}/location/current")
    suspend fun current(@Path("childId") childId: String): ApiEnvelope<LocationDto>

    @GET("children/{childId}/location/history")
    suspend fun history(@Path("childId") childId: String): ApiEnvelope<List<LocationDto>>

    @POST("locations/batch")
    suspend fun batch(@Body body: List<LocationDto>): ApiEnvelope<Unit>

    @POST("devices/gps-status")
    suspend fun gpsStatus(@Body body: GpsStatusRequest): ApiEnvelope<Unit>
}

interface GeofenceApi {
    @GET("children/{childId}/geofences")
    suspend fun list(@Path("childId") childId: String): ApiEnvelope<List<GeofenceDto>>

    @POST("children/{childId}/geofences")
    suspend fun create(
        @Path("childId") childId: String,
        @Body body: GeofenceDto,
    ): ApiEnvelope<GeofenceDto>

    @DELETE("children/{childId}/geofences/{geofenceId}")
    suspend fun delete(
        @Path("childId") childId: String,
        @Path("geofenceId") geofenceId: String,
    ): ApiEnvelope<Unit>
}

interface NotificationApi {
    @GET("notifications")
    suspend fun list(
        @Query("type") type: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiEnvelope<PageResponse<NotificationDto>>

    @PATCH("notifications/{notificationId}")
    suspend fun markRead(@Path("notificationId") notificationId: String): ApiEnvelope<Unit>
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

    @Multipart
    @POST("community/attachments")
    suspend fun uploadAttachment(@Part file: MultipartBody.Part): ApiEnvelope<Unit>
}

interface ChatApi {
    @GET("chat/rooms")
    suspend fun rooms(): ApiEnvelope<List<ChatRoomDto>>

    @GET("chat/rooms/{roomId}/messages")
    suspend fun messages(
        @Path("roomId") roomId: String,
        @Query("before") before: String? = null,
        @Query("size") size: Int = 30,
    ): ApiEnvelope<List<ChatMessageDto>>
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

interface SubscriptionApi {
    @POST("subscriptions")
    suspend fun subscribe(@Body body: SubscribeRequest): ApiEnvelope<SubscriptionDto>

    @GET("subscriptions/me")
    suspend fun me(): ApiEnvelope<SubscriptionDto>

    @DELETE("subscriptions/me")
    suspend fun cancel(): ApiEnvelope<Unit>
}

interface AppUsageApi {
    @GET("children/{childId}/app-usage")
    suspend fun list(
        @Path("childId") childId: String,
        @Query("groupBy") groupBy: String = "APP",
    ): ApiEnvelope<List<AppUsageDto>>

    @POST("children/{childId}/app-usage")
    suspend fun report(
        @Path("childId") childId: String,
        @Body body: AppUsageReportRequest,
    ): ApiEnvelope<Unit>
}
