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
 * 이름 규칙(Java_<패키지>_<클래스>_<함수>)으로 JVM 이 .so 안의 함수를 찾아 실행한다.
 *
 * [교과서 수준·우회 가능] 각 검사는 개별 함수로 노출된다. Frida 로 어느 함수(예: nativeIsRooted)의
 * 반환값 하나만 false 로 덮으면 그 방어가 무력화된다 — 의도된 약점이다(시연에서 후킹 지점이 분명히 드러남).
 */
object DefenseChecker {

    private const val TAG = "KidhackDefense"

    /** [VULN-MOBILE-DEF-1] .so 로드 성공 여부. 실패하면(예: ABI 불일치, APK 에서 .so 삭제 후 재서명) 방어를 건너뛴다(앱은 죽이지 않음) — fail-open. 보존: 고치지 말 것. */
    private var libraryLoaded = false

    init {
        // libdefense.so 를 메모리에 적재. Android.mk 의 LOCAL_MODULE := defense 와 일치해야 한다.
        // 로드 실패로 앱이 죽는 것을 막기 위해 runCatching 으로 감싼다.
        libraryLoaded = runCatching { System.loadLibrary("defense") }
            .onFailure { Log.w(TAG, "libdefense.so load failed: ${it.message}") }
            .isSuccess
    }

    // ── 네이티브 함수 선언 (실제 구현은 .so 안) ──────────────────────────────
    // 반환값 Boolean = 위협이 탐지되면 true.

    /** 루팅 흔적(su/Magisk/test-keys)이 있으면 true. → root_check.cpp */
    private external fun nativeIsRooted(): Boolean

    /** 에뮬레이터(AVD/Nox 등) 지문이 있으면 true. → root_check.cpp(check_emulator) */
    private external fun nativeIsEmulator(): Boolean

    /** Frida 흔적(maps/포트)이 있으면 true. → frida_check.cpp */
    private external fun nativeIsFridaDetected(): Boolean

    /** 디버거가 붙어 있으면 true. → antidebug_check.cpp */
    private external fun nativeIsDebuggerAttached(): Boolean

    /** APK 서명이 정품과 다르면(리패킹) true. context 로 PackageManager 조회. → integrity_check.cpp */
    private external fun nativeCheckIntegrity(context: Context): Boolean

    /**
     * 방어 검사를 돌려 잡힌 위협 목록을 담은 [DefenseResult] 를 반환한다.
     *
     * 개별 네이티브 검사 4종을 순서대로 호출한다(교과서 수준: 각 반환값이 후킹 표적).
     *
     * @param context PackageManager 조회에 필요(무결성 검사).
     * @param checkIntegrity [VULN-MOBILE-DEF-2] 무결성 검사를 수행할지. 기본은 릴리스에서만 true — 검사 수행 여부가 APK 안의 BuildConfig.DEBUG 상수 하나에 묶여 있다. api 변형은 debug 를 상속하므로 실서버 연동 빌드도 서명 지문 대조를 하지 않는다. 보존: 고치지 말 것.
     *        (디버그 빌드는 디버그 서명이라 정품 지문과 달라 자기 자신을 죽이기 때문) 평가항목: (금취분평)MOB-FIN-018 [전자금융] 프로그램 무결성 검증 — 평가 예시 "설치파일(APK, IPA) 변조 후 재 설치 시, 정상 실행 가능 여부" / (주통기)해당 항목 없음. 비해당: 보안프로그램 임의해제 방지는 HTS-FIN-014 로 HTS 전용.
     * @param checkDebugger 디버거 검사를 수행할지. 기본은 [BuildConfig.DEFENSE_STRICT].
     *        (디버그 빌드에서 디버거를 붙이면 앱이 스스로를 종료해 개발이 막히기 때문.
     *         무결성과 같은 이유로 STRICT 빌드에서만 켠다.)
     * @param checkEmulator 에뮬레이터 검사를 수행할지. 기본은 [BuildConfig.DETECT_EMULATOR].
     *        (개발·테스트를 AVD/Nox 에서 하는데 항상 켜면 앱이 매번 스스로 종료돼 작업이
     *         불가능해진다. 그래서 평소엔 꺼두고, gradle 프로퍼티 -PdetectEmulator=true 로
     *         빌드하거나 release 빌드에서만 켠다.)
     */
    fun runAllChecks(
        context: Context,
        checkIntegrity: Boolean = BuildConfig.DEFENSE_STRICT,
        checkDebugger: Boolean = BuildConfig.DEFENSE_STRICT,
        checkEmulator: Boolean = BuildConfig.DETECT_EMULATOR,
    ): DefenseResult {
        // [VULN-MOBILE-DEF-1] .so 로드 실패 시 방어 불가 → 빈 결과(위협 없음)로 처리. 데모 목적상 fail-open. 빈 집합은 hasThreat=false 로 판정되어 시작·민감화면 게이트(DefenseGate.kt:24-28, 43-48)가 그대로 통과시키므로 루팅·Frida·디버거·무결성 4종이 한꺼번에 무효화된다. 무결성 검사도 같은 .so 안에 있어 리패킹을 탐지할 수단까지 함께 사라진다. 평가항목: (금취분평)MOB-FIN-011 [전자금융] OS 변조 탐지 기능 적용 · MOB-FIN-021 [전자금융] 디버깅 탐지기능 적용 · MOB-FIN-018 [전자금융] 프로그램 무결성 검증 / (주통기)해당 항목 없음. 비해당: MOB-FIN-012 악성코드 방지(악성코드 탐지 기능 자체가 없음). 보존: 고치지 말 것.
        if (!libraryLoaded) {
            Log.w(TAG, "defense library not loaded, skipping all checks")
            return DefenseResult(emptySet())
        }

        val threats = mutableSetOf<Threat>()
        if (nativeIsRooted()) threats += Threat.ROOT
        if (checkEmulator && nativeIsEmulator()) threats += Threat.EMULATOR
        if (nativeIsFridaDetected()) threats += Threat.FRIDA
        if (checkDebugger && nativeIsDebuggerAttached()) threats += Threat.DEBUGGER
        if (checkIntegrity && nativeCheckIntegrity(context)) threats += Threat.INTEGRITY

        return DefenseResult(threats)
    }
}
