// =============================================================================
// native-lib.cpp — JNI 브릿지 (Kotlin ↔ C++ 연결 지점)
// -----------------------------------------------------------------------------
// [이 파일의 역할]
//   Kotlin 쪽 com.hack.kidhack.security.DefenseChecker 의 `external fun` 들이
//   호출되면, JVM 은 이름 규칙에 따라 아래 C 함수를 찾아 실행한다.
//   규칙: Java_<패키지경로에서 . 을 _ 로>_<클래스이름>_<함수이름>
//   예) Kotlin  com.hack.kidhack.security.DefenseChecker.nativeIsRooted()
//       C 함수  Java_com_hack_kidhack_security_DefenseChecker_nativeIsRooted
//
// [왜 로직을 여기 두지 않는가]
//   실제 탐지 로직은 root_check.cpp / frida_check.cpp / ... 에 나눠 둔다.
//   이 파일은 "JNI 규칙에 맞춘 껍데기" 만 모아 두는 얇은 계층이다.
//
// [왜 체크마다 함수를 나누는가]
//   이 데모의 목표는 "방어가 뚫리는 것을 보여주기" 다. 체크를 개별 함수로 노출하면
//   Frida 로 어느 함수를 후킹했는지(예: nativeIsRooted 의 반환값을 false 로 덮음)가
//   시연에서 분명히 드러난다. 하나로 합치면 후킹 지점이 모호해진다.
//
// [Frida 우회의 표적]
//   각 함수는 jboolean(사실상 1바이트) 을 돌려준다. 이 반환값 한 개만 뒤집으면
//   방어가 무력화된다 — 의도된 약점이다(교과서 수준·우회 가능).
// =============================================================================

#include <jni.h>          // JNIEnv, jobject, jboolean 등 JNI 기본 타입
#include <android/log.h>  // __android_log_print (logcat 출력)
#include "defense.h"      // check_root / check_frida / check_debugger / check_integrity 선언

// logcat 에 INFO 레벨로 한 줄 남기는 도우미 매크로.
// 태그(DEFENSE_LOG_TAG = "KidhackDefense")로 필터링하면 시연 중 방어 흐름만 볼 수 있다:
//   adb logcat -s KidhackDefense
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, DEFENSE_LOG_TAG, __VA_ARGS__)

// extern "C": C++ 컴파일러의 이름 맹글링(name mangling)을 끈다.
// 이걸 빼면 함수 이름이 컴파일 과정에서 변형돼 JVM 이 JNI 규칙대로 찾지 못한다. 필수.
extern "C" {

// -----------------------------------------------------------------------------
// nativeIsRooted() : 루팅 탐지
//   env  = JNI 환경 포인터(자바 객체/메서드 접근용). 여기선 안 쓴다.
//   thiz = 호출한 Kotlin 객체 자신. 여기선 안 쓴다(그래서 이름 생략).
//   반환 = 루팅 흔적이 있으면 JNI_TRUE, 없으면 JNI_FALSE
// -----------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL
Java_com_hack_kidhack_security_DefenseChecker_nativeIsRooted(JNIEnv *env, jobject /*thiz*/) {
    bool detected = check_root();                       // 실제 로직은 root_check.cpp
    LOGI("root check -> %s", detected ? "DETECTED" : "clean");
    return detected ? JNI_TRUE : JNI_FALSE;
}

// -----------------------------------------------------------------------------
// nativeIsFridaDetected() : Frida(동적 계측 도구) 탐지
// -----------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL
Java_com_hack_kidhack_security_DefenseChecker_nativeIsFridaDetected(JNIEnv *env, jobject /*thiz*/) {
    bool detected = check_frida();                      // 실제 로직은 frida_check.cpp
    LOGI("frida check -> %s", detected ? "DETECTED" : "clean");
    return detected ? JNI_TRUE : JNI_FALSE;
}

// -----------------------------------------------------------------------------
// nativeIsDebuggerAttached() : 디버거 attach 탐지(안티디버깅)
// -----------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL
Java_com_hack_kidhack_security_DefenseChecker_nativeIsDebuggerAttached(JNIEnv *env, jobject /*thiz*/) {
    bool detected = check_debugger();                   // 실제 로직은 antidebug_check.cpp
    LOGI("debugger check -> %s", detected ? "DETECTED" : "clean");
    return detected ? JNI_TRUE : JNI_FALSE;
}

// -----------------------------------------------------------------------------
// nativeCheckIntegrity(context) : 앱 무결성(서명) 검증
//   다른 셋과 달리 context 가 필요하다 — PackageManager 로 현재 APK 서명을
//   조회해야 하기 때문. context 는 Kotlin 에서 넘겨준 android.content.Context.
//   반환 = 서명이 정품 해시와 다르면(리패킹/재서명) JNI_TRUE(위반)
// -----------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL
Java_com_hack_kidhack_security_DefenseChecker_nativeCheckIntegrity(JNIEnv *env, jobject /*thiz*/,
                                                                   jobject context) {
    bool violated = check_integrity(env, context);      // 실제 로직은 integrity_check.cpp
    LOGI("integrity check -> %s", violated ? "VIOLATED" : "ok");
    return violated ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
