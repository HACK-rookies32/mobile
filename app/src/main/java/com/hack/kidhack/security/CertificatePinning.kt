package com.hack.kidhack.security

import okhttp3.CertificatePinner

/**
 * SSL(인증서) 피닝 설정.
 *
 * [무엇을 하나] OkHttp 가 TLS 핸드셰이크로 받은 서버 인증서 체인을, 시스템 트러스트스토어
 * 검증을 통과한 "뒤에" 우리가 박아 둔 공개키 해시(핀)와 한 번 더 대조한다. 하나도 안 맞으면
 * 연결을 끊는다. 이렇게 하면 사용자가 기기에 심은 프록시 CA(Burp/mitmproxy)로도
 * 중간자 공격(MITM)이 되지 않는다 — 프록시 인증서의 공개키는 우리 핀과 다르기 때문.
 *
 * [핀은 무엇인가 — SPKI SHA-256] 인증서 전체가 아니라 "공개키 정보(SubjectPublicKeyInfo)"
 * 의 SHA-256 해시다. 인증서가 같은 키로 갱신되면 핀은 그대로라 앱을 재배포하지 않아도 된다.
 *
 * [왜 두 개를 핀하나 — leaf + 중간 CA]
 *   - leaf(kidhackapp.com): 가장 정확하지만, AWS ACM 은 인증서 자동 갱신 시 키가 바뀔 수
 *     있어 leaf 핀만 두면 어느 날 앱이 서버에 못 붙는다.
 *   - 중간 CA(Amazon RSA 2048 M04): leaf 가 갱신돼도 같은 CA 로 재발급되면 이 핀이 살아
 *     백업 역할을 한다. 루트까지 핀하지 않는 이유는 범위가 너무 넓어 방어 가치가 낮아서다.
 *
 * [교과서 수준·우회 가능] 이 피닝은 objection·frida-multiple-unpinning 같은 공개 스크립트가
 * OkHttp 의 CertificatePinner.check$okhttp 를 후킹해 무력화한다 — 의도된 약점이다(시연에서
 * "피닝 → 표준 도구로 우회 → 프록시로 평문 캡처" 흐름을 보여주기 위함).
 *
 * ---------------------------------------------------------------------------
 * 핀 값 출처: https://kidhackapp.com (AWS ACM 발급, 만료 2027-03-21)
 * 재추출 방법 (Git Bash, openssl 은 C:\Program Files\Git\usr\bin\openssl.exe):
 *   echo | openssl s_client -connect kidhackapp.com:443 -servername kidhackapp.com -showcerts 2>/dev/null \
 *     | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der \
 *     | openssl dgst -sha256 -binary | openssl enc -base64
 * ---------------------------------------------------------------------------
 */
object CertificatePinning {

    /** leaf 인증서(CN=kidhackapp.com)의 공개키 핀. */
    private const val PIN_LEAF = "sha256/KSJgHibV2TE7YcRVK4G5IB+iDdOD+WCoaxvAEOvZiec="

    /** 중간 CA(Amazon RSA 2048 M04)의 공개키 핀 — leaf 갱신 대비 백업. */
    private const val PIN_INTERMEDIATE = "sha256/G9LNNAql897egYsabashkzUCTEJkWBzgoEtk8X/678c="

    /** 운영 서버 도메인. release BASE_URL(https://kidhackapp.com/api)과 일치. */
    private const val HOST = "kidhackapp.com"

    /**
     * OkHttpClient 에 붙일 피너.
     *
     * 주의: 피닝은 TLS(https) 연결에만 적용된다. debug/api 빌드처럼 http 평문으로 붙는
     * 경우엔 핸드셰이크 자체가 없어 이 피너가 관여하지 않는다(그 빌드는 그대로 통과).
     */
    val pinner: CertificatePinner = CertificatePinner.Builder()
        .add(HOST, PIN_LEAF, PIN_INTERMEDIATE)
        .add("*.$HOST", PIN_LEAF, PIN_INTERMEDIATE)
        .build()
}
