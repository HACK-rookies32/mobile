// 방어 체크의 공통 선언.
// 각 *_check.cpp 가 순수 C++ 함수를 구현하고, native-lib.cpp 가 JNI 로 노출한다.
// 2차(Lv2)에서 집계 함수·자체무결성(selfcheck)·위협 비트 상수를 추가했다.
#ifndef KIDHACK_DEFENSE_H
#define KIDHACK_DEFENSE_H

#include <jni.h>

#define DEFENSE_LOG_TAG "KidhackDefense"

// ── 개별 체크 (각 *_check.cpp 구현) ──────────────────────────────────────────
// non-static(외부 링키지)이라 selfcheck.cpp 가 함수 주소를 취득해 프롤로그를 감시할 수 있다.

// 루팅 흔적(su 바이너리, Magisk, test-keys 빌드)이 있으면 true.
bool check_root();

// Frida 흔적(frida/gadget 매핑, 27042 포트, gum 스레드)이 있으면 true.
bool check_frida();

// 디버거가 붙어 있으면(TracerPid != 0) true.
bool check_debugger();

// 실행 중 APK 서명이 정품 해시와 다르면(리패킹/재서명) true.
bool check_integrity(JNIEnv *env, jobject context);

// ── 자체무결성 (selfcheck.cpp, Layer C) ─────────────────────────────────────

// JNI_OnLoad 시점(공격자 후킹 전)에 감시 대상 함수들의 프롤로그를 baseline 으로 스냅샷.
void selfcheck_snapshot();

// 감시 대상 함수의 현재 프롤로그가 baseline 과 다르면(inline hook 트램폴린) true.
bool check_inline_hooks();

// ── 집계 (native-lib.cpp, Layer C) ──────────────────────────────────────────

// 위협 종류를 나타내는 비트 플래그. Kotlin 쪽 Threat 와 대응된다.
enum SecurityBit {
    SEC_ROOT      = 1 << 0,  // 루팅
    SEC_FRIDA     = 1 << 1,  // Frida
    SEC_DEBUGGER  = 1 << 2,  // 디버거
    SEC_INTEGRITY = 1 << 3,  // 위·변조(재서명)
    SEC_HOOK      = 1 << 4,  // inline hook(자체무결성 위반)
};

// 모든 검사를 네이티브 내부에서 직접 호출해 bitmask 로 취합한다.
//   - 개별 JNI 함수를 후킹해도 여기서는 C++ 함수를 직접 부르므로 영향받지 않는다(교차검증).
//   - checkIntegrity=false 면 무결성 검사를 건너뛴다(디버그 빌드 자멸 방지).
// 반환 0 = 안전, 그 외 = 위협 비트들의 OR.
int compute_security_state(JNIEnv *env, jobject context, bool checkIntegrity);

#endif // KIDHACK_DEFENSE_H
