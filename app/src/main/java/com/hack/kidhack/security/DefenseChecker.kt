package com.hack.kidhack.security

import android.content.Context
import android.util.Log
import com.hack.kidhack.BuildConfig

/**
 * 네이티브 방어 라이브러리(libdefense.so)로 통하는 Kotlin 창구.
 *
 * [왜 object 인가] 앱 전체가 하나의 검사기를 공유하면 되고, .so 로드는 한 번만 하면 된다.
 *
 * [무엇을 하나] 아래 `external fun` 들이 네이티브(native-lib.cpp)의 JNI 함수와 연결된다.
 * 단, Lv2 에서는 이름 맹글링 export 대신 JNI_OnLoad 의 RegisterNatives 로 동적 등록되므로
 * .so 심볼 테이블에 함수 이름이 남지 않는다(getExportByName 후킹 무효화).
 *
 * [Lv2 교차검증] 게이트가 실제로 쓰는 것은 개별 4콜이 아니라 집계 토큰 [nativeSecurityState]
 * (bitmask)다. 집계는 네이티브 내부에서 개별 체크를 직접 호출 + 자체무결성(inline hook 탐지)을
 * 합치므로, 개별 JNI boolean 을 후킹해도 게이트 결정은 바뀌지 않는다. 개별 external 4개는
 * "이걸 후킹해도 안 통한다"는 시연을 위해 보존한다.
 */
object DefenseChecker {

    private const val TAG = "KidhackDefense"

    // 집계 토큰 bitmask 비트(네이티브 SecurityBit 와 1:1 대응).
    private const val SEC_ROOT = 1 shl 0
    private const val SEC_FRIDA = 1 shl 1
    private const val SEC_DEBUGGER = 1 shl 2
    private const val SEC_INTEGRITY = 1 shl 3
    private const val SEC_HOOK = 1 shl 4

    /** .so 로드 성공 여부. 실패하면(예: ABI 불일치) 방어를 건너뛴다(앱은 죽이지 않음). */
    private var libraryLoaded = false

    init {
        // libdefense.so 를 메모리에 적재. CMake 의 add_library(defense ...) 이름과 일치해야 한다.
        // 로드 실패로 앱이 죽는 것을 막기 위해 runCatching 으로 감싼다.
        libraryLoaded = runCatching { System.loadLibrary("defense") }
            .onFailure { Log.w(TAG, "libdefense.so load failed: ${it.message}") }
            .isSuccess
    }

    // ── 네이티브 함수 선언 (실제 구현은 .so 안) ──────────────────────────────
    // 반환값 Boolean = 위협이 탐지되면 true.

    /** 루팅 흔적(su/Magisk/test-keys)이 있으면 true. → root_check.cpp */
    private external fun nativeIsRooted(): Boolean

    /** Frida 흔적(maps/포트)이 있으면 true. → frida_check.cpp */
    private external fun nativeIsFridaDetected(): Boolean

    /** 디버거가 붙어 있으면 true. → antidebug_check.cpp */
    private external fun nativeIsDebuggerAttached(): Boolean

    /** APK 서명이 정품과 다르면(리패킹) true. context 로 PackageManager 조회. → integrity_check.cpp */
    private external fun nativeCheckIntegrity(context: Context): Boolean

    /**
     * 집계 토큰: 모든 검사 + 자체무결성을 네이티브 내부에서 취합한 bitmask.
     * @param checkIntegrity 무결성 검사 포함 여부(디버그 빌드는 false 로 자멸 방지).
     * → native-lib.cpp compute_security_state
     */
    private external fun nativeSecurityState(context: Context, checkIntegrity: Boolean): Int

    /**
     * 서버 무결성 증명(attestation): 서버 발급 nonce 에 대해 네이티브가 HMAC 서명을 만든다.
     * 반환 "state:mac" (state=보안상태 정수, mac=HMAC hex). → native-lib.cpp jni_attest
     */
    private external fun nativeAttest(context: Context, nonce: String, checkIntegrity: Boolean): String

    /**
     * 결제 요청에 실을 attestation 값("state:mac")을 만든다. .so 미로드/실패 시 null.
     * 서버는 이 값과 nonce 로 HMAC 을 재검증하고 state==0 을 확인한다(로컬 우회만으론 못 넘음).
     */
    fun attest(context: Context, nonce: String): String? {
        if (!libraryLoaded) return null
        return runCatching { nativeAttest(context, nonce, !BuildConfig.DEBUG) }
            .onFailure { Log.w(TAG, "attest failed: ${it.message}") }
            .getOrNull()
    }

    /**
     * 방어 검사를 돌려 잡힌 위협 목록을 담은 [DefenseResult] 를 반환한다.
     *
     * Lv2: 개별 4콜 대신 **집계 토큰([nativeSecurityState])**을 디코드한다.
     * 개별 JNI boolean 후킹으로는 이 토큰이 바뀌지 않아 게이트를 못 뚫는다(교차검증).
     *
     * @param context PackageManager 조회에 필요(무결성 검사).
     * @param checkIntegrity 무결성 검사를 수행할지. 기본은 릴리스에서만 true.
     *        (디버그 빌드는 디버그 서명이라 정품 지문과 달라 자기 자신을 죽이기 때문)
     */
    fun runAllChecks(
        context: Context,
        checkIntegrity: Boolean = !BuildConfig.DEBUG,
    ): DefenseResult {
        // .so 로드 실패 시 방어 불가 → 빈 결과(위협 없음)로 처리. 데모 목적상 fail-open.
        if (!libraryLoaded) {
            Log.w(TAG, "defense library not loaded, skipping all checks")
            return DefenseResult(emptySet())
        }

        val state = nativeSecurityState(context, checkIntegrity)

        // bitmask → Threat 집합.
        val threats = mutableSetOf<Threat>()
        if (state and SEC_ROOT != 0) threats += Threat.ROOT
        if (state and SEC_FRIDA != 0) threats += Threat.FRIDA
        if (state and SEC_DEBUGGER != 0) threats += Threat.DEBUGGER
        if (state and SEC_INTEGRITY != 0) threats += Threat.INTEGRITY
        if (state and SEC_HOOK != 0) threats += Threat.TAMPER

        return DefenseResult(threats)
    }
}