package com.hack.kidhack.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class ApiEnvelope<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
)

@Serializable
data class PageResponse<T>(
    val content: List<T> = emptyList(),
    val page: Int = 0,
    val size: Int = 20,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
)

enum class ApiErrorCode {
    INVALID_PARAMETER,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    INTERNAL_ERROR,
}

class ApiException(
    val code: ApiErrorCode,
    override val message: String,
) : Exception(message) {
    companion object {
        fun from(error: ApiError?): ApiException {
            val mapped = runCatching { ApiErrorCode.valueOf(error?.code.orEmpty()) }
                .getOrDefault(ApiErrorCode.INTERNAL_ERROR)
            return ApiException(mapped, error?.message ?: "알 수 없는 오류가 발생했습니다")
        }
    }
}

fun <T> ApiEnvelope<T>.unwrap(): T {
    if (!success) throw ApiException.from(error)
    return data ?: throw ApiException(ApiErrorCode.INTERNAL_ERROR, "응답 데이터가 없습니다")
}

fun <T> ApiEnvelope<T>.unwrapOrNull(): T? {
    if (!success) throw ApiException.from(error)
    return data
}
