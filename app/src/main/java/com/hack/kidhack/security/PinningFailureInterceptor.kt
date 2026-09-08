package com.hack.kidhack.security

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * 인증서 피닝이 연결을 거부했을 때 그 사실을 logcat 에 분명히 남기는 인터셉터.
 *
 * [왜 필요한가] CertificatePinner 가 핀 불일치로 끊으면 OkHttp 는 [SSLPeerUnverifiedException]
 * 을 던진다. 이건 겉보기에 평범한 네트워크 오류와 구별되지 않아, 시연에서 "피닝이 막았다" 는
 * 사실이 드러나지 않는다. 그래서 이 예외만 골라 방어 태그(KidhackDefense)로 한 줄 남긴다.
 *
 * [무엇을 바꾸지 않는가] 로그만 남기고 예외는 그대로 다시 던진다(rethrow). 흐름 자체는 전혀
 * 바꾸지 않는다 — 연결은 여전히 실패하고, 상위 코드의 에러 처리도 그대로 동작한다.
 * (요청 범위가 "간단한 수준" 이라 차단 UI 나 재시도 로직은 넣지 않는다.)
 */
class PinningFailureInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        return try {
            chain.proceed(chain.request())
        } catch (e: SSLPeerUnverifiedException) {
            // 핀 불일치 = 프록시 MITM 등으로 인증서가 바뀐 상황. 어느 호스트였는지 함께 남긴다.
            Log.w(TAG, "pinning rejected: ${chain.request().url.host} - ${e.message}")
            throw e
        }
    }

    private companion object {
        const val TAG = "KidhackDefense"
    }
}
