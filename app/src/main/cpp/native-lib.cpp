// =============================================================================
// native-lib.cpp — JNI 브릿지 + 집계 (Layer A 심볼 은닉 + Layer C 교차검증)
// -----------------------------------------------------------------------------
// [Lv2 변경 요약]
//   (A) 심볼 은닉: 예전엔 Java_com_hack_kidhack_..._nativeIsRooted 처럼 "이름 맹글링"으로
//       export 했다 → nm/Frida getExportByName 으로 바로 찾혔다. 이제는 그 export 를
//       모두 없애고, JNI_OnLoad 에서 RegisterNatives 로 "동적 등록"한다. 함수는 static
//       (hidden)이라 심볼 테이블에 이름이 안 남는다 → 공격자는 RegisterNatives/JNI_OnLoad
//       를 먼저 후킹해 함수 주소를 알아내야 한다(우회 1단계).
//   (B) 클래스명/메서드명 문자열도 OBF 로 암호화 → .rodata 스캔에도 안 보인다.
//   (C) 집계 compute_security_state: 개별 체크를 네이티브 내부에서 직접 호출 + 자체무결성
//       을 OR 하여 bitmask 로 반환. 게이트는 이 토큰으로만 판단하므로 개별 JNI boolean 을
//       후킹해도 게이트 결정은 안 바뀐다(교차검증, 우회 2단계).
//
// [보존] 개별 4개 체크(nativeIsRooted 등)는 데모용으로 등록을 유지한다 —
//   "이걸 후킹해도 게이트엔 안 통한다"를 시연에서 보여주기 위함.
// =============================================================================

#include <jni.h>
#include <android/log.h>
#include <cstring>        // strcpy
#include <cstdio>         // snprintf
#include <string>
#include "defense.h"
#include "obfuscate.h"    // OBF() : 컴파일타임 문자열 암호화
#include "crypto_native.h" // crypto::hmac_sha256_hex

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, DEFENSE_LOG_TAG, __VA_ARGS__)

// -----------------------------------------------------------------------------
// 집계: 모든 검사를 네이티브 내부에서 직접 호출해 bitmask 로 취합.
//   defense.h 에 선언(비-static) — selfcheck.cpp 가 이 함수 주소도 프롤로그 감시 대상에 넣는다.
// -----------------------------------------------------------------------------
int compute_security_state(JNIEnv *env, jobject context, bool checkIntegrity) {
    int state = 0;
    if (check_root())      state |= SEC_ROOT;
    if (check_frida())     state |= SEC_FRIDA;
    if (check_debugger())  state |= SEC_DEBUGGER;
    // 무결성은 릴리스에서만(디버그 빌드는 서명이 달라 자멸하므로 호출측에서 false 전달).
    if (checkIntegrity && check_integrity(env, context)) state |= SEC_INTEGRITY;
    // 자체무결성: 위 함수들이 inline hook 당했으면 비트를 세운다.
    if (check_inline_hooks()) state |= SEC_HOOK;
    return state;
}

// -----------------------------------------------------------------------------
// JNI 구현부 — 전부 static(외부에 심볼 노출 안 함). RegisterNatives 로만 연결된다.
//   각 JNI 함수의 2번째 인자 jobject 는 호출한 Kotlin 객체(DefenseChecker 싱글턴) 자신.
// -----------------------------------------------------------------------------
namespace {

// 개별 체크(데모용) — 후킹 시연 대상.
jboolean jni_is_rooted(JNIEnv *, jobject) {
    bool d = check_root();
    LOGI("root check -> %s", d ? "DETECTED" : "clean");
    return d ? JNI_TRUE : JNI_FALSE;
}
jboolean jni_is_frida(JNIEnv *, jobject) {
    bool d = check_frida();
    LOGI("frida check -> %s", d ? "DETECTED" : "clean");
    return d ? JNI_TRUE : JNI_FALSE;
}
jboolean jni_is_debugger(JNIEnv *, jobject) {
    bool d = check_debugger();
    LOGI("debugger check -> %s", d ? "DETECTED" : "clean");
    return d ? JNI_TRUE : JNI_FALSE;
}
jboolean jni_check_integrity(JNIEnv *env, jobject, jobject context) {
    bool v = check_integrity(env, context);
    LOGI("integrity check -> %s", v ? "VIOLATED" : "ok");
    return v ? JNI_TRUE : JNI_FALSE;
}

// 집계 토큰(게이트가 실제로 쓰는 것) — bitmask 반환.
jint jni_security_state(JNIEnv *env, jobject, jobject context, jboolean checkIntegrity) {
    int state = compute_security_state(env, context, checkIntegrity == JNI_TRUE);
    LOGI("security state -> 0x%02x", state);
    return static_cast<jint>(state);
}

// 서버 무결성 증명(attestation) — 서버 발급 nonce 에 대해 HMAC 서명을 만든다(Layer D).
//   state = 집계 보안상태(0=clean). message = "nonce|state". mac = HMAC_SHA256(내장키, message).
//   반환 문자열 = "state:mac". 내장키는 OBF 로 숨긴 서버 공유 비밀키.
//   [다단계] 로컬에서 state 를 0 으로 위조해도, 이 HMAC 를 위조하려면 .so 의 키 추출이 필요.
jstring jni_attest(JNIEnv *env, jobject, jobject context, jstring jnonce, jboolean checkIntegrity) {
    const char *nonce = env->GetStringUTFChars(jnonce, nullptr);

    int state = compute_security_state(env, context, checkIntegrity == JNI_TRUE);
    char stateStr[16];
    snprintf(stateStr, sizeof(stateStr), "%d", state);

    // 서명 대상: "nonce|state" (서버 AttestationVerifier 와 동일 규약).
    std::string message = std::string(nonce) + "|" + stateStr;

    // 서버와 공유하는 비밀키(OBF 로 난독). 로컬 버퍼에 복호화 후 HMAC.
    char key[40];
    strcpy(key, OBF("kidhack-attest-secret"));
    std::string mac = crypto::hmac_sha256_hex(
            reinterpret_cast<const uint8_t *>(key), strlen(key),
            reinterpret_cast<const uint8_t *>(message.c_str()), message.size());

    std::string result = std::string(stateStr) + ":" + mac;

    env->ReleaseStringUTFChars(jnonce, nonce);
    LOGI("attest -> state=%s", stateStr);
    return env->NewStringUTF(result.c_str());
}

} // namespace

// -----------------------------------------------------------------------------
// JNI_OnLoad — 라이브러리 로드 시 JVM 이 자동 호출. 여기서 native 메서드를 동적 등록한다.
// -----------------------------------------------------------------------------
JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void * /*reserved*/) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    // 클래스명은 OBF 로 숨긴다. FindClass 는 loadLibrary 호출자(DefenseChecker)의
    // 클래스로더로 찾으므로 정상 로드된다.
    char clsName[48];
    strcpy(clsName, OBF("com/hack/kidhack/security/DefenseChecker"));
    jclass clazz = env->FindClass(clsName);
    if (clazz == nullptr) {
        LOGI("JNI_OnLoad: DefenseChecker class not found");
        return JNI_ERR;
    }

    // 메서드명도 OBF 로 숨겨 로컬 버퍼에 복호화(RegisterNatives 호출 동안 유효해야 함).
    // 시그니처는 민감하지 않아 평문으로 둔다.
    char nRoot[24], nFrida[24], nDbg[28], nInteg[24], nState[24], nAttest[16];
    strcpy(nRoot,   OBF("nativeIsRooted"));
    strcpy(nFrida,  OBF("nativeIsFridaDetected"));
    strcpy(nDbg,    OBF("nativeIsDebuggerAttached"));
    strcpy(nInteg,  OBF("nativeCheckIntegrity"));
    strcpy(nState,  OBF("nativeSecurityState"));
    strcpy(nAttest, OBF("nativeAttest"));

    const JNINativeMethod methods[] = {
        { nRoot,  "()Z", reinterpret_cast<void *>(jni_is_rooted) },
        { nFrida, "()Z", reinterpret_cast<void *>(jni_is_frida) },
        { nDbg,   "()Z", reinterpret_cast<void *>(jni_is_debugger) },
        { nInteg, "(Landroid/content/Context;)Z",  reinterpret_cast<void *>(jni_check_integrity) },
        { nState, "(Landroid/content/Context;Z)I", reinterpret_cast<void *>(jni_security_state) },
        { nAttest, "(Landroid/content/Context;Ljava/lang/String;Z)Ljava/lang/String;",
          reinterpret_cast<void *>(jni_attest) },
    };

    if (env->RegisterNatives(clazz, methods,
                             sizeof(methods) / sizeof(methods[0])) != JNI_OK) {
        LOGI("JNI_OnLoad: RegisterNatives failed");
        return JNI_ERR;
    }

    // 공격자가 후킹하기 전, 지금(로드 직후) 원본 프롤로그를 baseline 으로 캡처.
    selfcheck_snapshot();

    LOGI("JNI_OnLoad: registered & snapshotted");
    return JNI_VERSION_1_6;
}
