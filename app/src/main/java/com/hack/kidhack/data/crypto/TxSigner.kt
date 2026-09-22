package com.hack.kidhack.data.crypto

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 거래정보 전자서명(무결성) 생성 — 서버 [TxSignatureVerifier] 와 1:1 로 맞춘 규약.
 *
 * payload = `amount:refId:nonce` 를 공유 비밀키로 HMAC-SHA256 서명하고 hex 로 인코딩한다.
 * 송금은 refId=childId, QR 결제는 refId=0 을 쓴다(서버와 동일).
 *
 * ⚠️ [VULN-MOBILE-PAY-3] 공유 비밀키를 앱 소스 상수로 두는 것 자체가 결함이다. VULN-MOBILE-RE-1 로 난독화가 없어 APK 문자열 검색만으로 추출되고, 키를 알면 임의 금액·refId 로 서명을 만들 수 있어 서명 기반 무결성 검증이 무의미해진다. 서버 기본값이 같은 문자열이라(백엔드 TxSignatureVerifier 의 @Value 기본값 — VULN-EFIN-15) 별도 키 설정이 없으면 위조 서명이 그대로 통과한다. 의도된 취약점(OWASP Mobile M10/M8). 보존: 고치지 말 것.
 * 평가항목: (금취분평)MOB-SER-025 앱 소스코드 내 운영정보 노출 방지 — 항목 정의의 "고정된 암호키"에 직접 해당(대표) · MOB-FIN-004 [전자금융] 거래정보 무결성 검증(결과) / (주통기)해당 항목 없음. 서버가 서명 필드를 빼면 검증을 skip 하는 것은 별건([VULN-EFIN-2])이고, 앱은 정직하게 서명을 붙인다.
 */
object TxSigner {

    // [VULN-MOBILE-PAY-3] 하드코딩된 거래 서명 공유키. 서버 기본값(kidhack.efin.tx-signing-secret)과 동일 문자열이다. 실서비스라면 안전 배포가 필요하다. 보존: 고치지 말 것.
    private const val SECRET = "kidhack-efin-tx-secret"
    private val random = SecureRandom()

    /** 일회성 nonce(16바이트 hex). */
    fun newNonce(): String {
        val bytes = ByteArray(16).also { random.nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** amount:refId:nonce 를 HMAC-SHA256 서명한 hex 문자열. */
    fun sign(amount: Long, refId: Long, nonce: String): String {
        val payload = "$amount:$refId:$nonce"
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        }
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}