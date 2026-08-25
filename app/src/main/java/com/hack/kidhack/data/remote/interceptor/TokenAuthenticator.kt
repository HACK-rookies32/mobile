package com.hack.kidhack.data.remote.interceptor

import com.hack.kidhack.core.common.KidhackJson
import com.hack.kidhack.data.remote.ApiEnvelope
import com.hack.kidhack.data.remote.dto.RefreshRequest
import com.hack.kidhack.data.remote.dto.TokenResponse
import com.hack.kidhack.data.remote.unwrap
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.domain.model.User
import com.hack.kidhack.domain.repository.SessionEvents
import com.hack.kidhack.domain.repository.SessionRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.serializer
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.atomic.AtomicBoolean

class TokenAuthenticator(
    private val sessionRepository: SessionRepository,
    private val sessionEvents: SessionEvents,
    private val baseUrl: String,
    private val refreshClient: OkHttpClient,
) : Authenticator {

    private val refreshing = AtomicBoolean(false)

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("Authorization") == null) return null
        if (responseCount(response) >= 2) {
            failLogout()
            return null
        }
        val refreshToken = sessionRepository.refreshToken() ?: run {
            failLogout()
            return null
        }
        if (!refreshing.compareAndSet(false, true)) return null
        return try {
            val session = refresh(refreshToken) ?: run {
                failLogout()
                return null
            }
            runBlocking { sessionRepository.save(session) }
            response.request.newBuilder()
                .header("Authorization", "Bearer ${session.accessToken}")
                .build()
        } finally {
            refreshing.set(false)
        }
    }

    private fun refresh(refreshToken: String): Session? {
        val url = baseUrl.trimEnd('/') + "/auth/token/refresh"
        val bodyJson = KidhackJson.instance.encodeToString(
            RefreshRequest.serializer(),
            RefreshRequest(refreshToken),
        )
        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        refreshClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val payload = resp.body?.string().orEmpty()
            val envelope = KidhackJson.instance.decodeFromString(
                serializer<ApiEnvelope<TokenResponse>>(),
                payload,
            )
            val data = envelope.unwrap()
            return Session(
                accessToken = data.accessToken,
                refreshToken = data.refreshToken,
                user = User(
                    id = data.user.id,
                    loginId = data.user.loginId,
                    name = data.user.name,
                    role = runCatching { Role.valueOf(data.user.role) }.getOrDefault(Role.PARENT),
                    phone = data.user.phone,
                    email = data.user.email,
                    profileImageUrl = data.user.profileImageUrl,
                ),
            )
        }
    }

    private fun failLogout() {
        runBlocking { sessionRepository.clear() }
        sessionEvents.emitLogout()
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
