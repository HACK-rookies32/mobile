// =============================================================================
// selfcheck.cpp — 자체무결성 / anti-inline-hook (Layer C)
// -----------------------------------------------------------------------------
// [무엇을 하나] 방어 함수 자신이 후킹당했는지 탐지한다.
//   Frida 의 Interceptor.attach 는 대상 함수 앞부분(프롤로그)에 "다른 곳으로 점프하는"
//   트램폴린 코드를 덮어쓴다. 그래서 함수의 첫 바이트들이 원본과 달라진다.
//
// [어떻게] JNI_OnLoad 시점(=앱 로드 직후, 공격자가 후킹하기 전)에 감시 대상 함수들의
//   프롤로그 첫 32바이트를 baseline 으로 복사(snapshot)해 둔다. 이후 check_inline_hooks()
//   가 같은 바이트를 다시 읽어 baseline 과 비교 → 다르면 후킹으로 판정.
//   같은 런타임 코드끼리 비교하므로 ASLR/재배치 영향이 없어 오탐이 없다.
//
// [왜 교차검증이 되나] 집계 함수 compute_security_state 는 개별 체크를 네이티브에서
//   직접 호출한다. 공격자가 check_root 를 inline hook 해 false 를 반환시키면, 그 반환값은
//   속일 수 있어도 프롤로그 바이트가 바뀌므로 이 검사가 SEC_HOOK 비트를 세운다.
//
// [우회 — 다단계] 공격자는 (a)프롤로그를 안 건드리는 방식(예: GOT/PLT 후킹, ret 후킹)을
//   쓰거나 (b)check_inline_hooks 자체와 baseline 을 함께 무력화해야 한다 → 우회 난이도 상승.
// =============================================================================

#include "defense.h"
#include <cstdint>
#include <cstring>   // memcpy, memcmp

namespace {

constexpr int SNAP_LEN = 32;    // 프롤로그 감시 바이트 수(트램폴린이 덮는 앞부분 커버)
constexpr int NUM_FUNCS = 4;    // 감시 대상 함수 개수

// 감시 대상 함수의 코드 주소. 함수 포인터 → const void* (POSIX ABI 에서 지원).
const void *g_targets[NUM_FUNCS];
// 각 함수의 원본 프롤로그 baseline.
uint8_t g_baseline[NUM_FUNCS][SNAP_LEN];
// 스냅샷 완료 여부(스냅샷 전에는 판단 보류).
bool g_snapped = false;

// 감시 대상 목록을 채운다. compute_security_state 는 native-lib.cpp 에 정의됨(주소는 링크시 결정).
void fill_targets() {
    g_targets[0] = reinterpret_cast<const void *>(&check_root);
    g_targets[1] = reinterpret_cast<const void *>(&check_frida);
    g_targets[2] = reinterpret_cast<const void *>(&check_debugger);
    g_targets[3] = reinterpret_cast<const void *>(&compute_security_state);
}

} // namespace

// JNI_OnLoad 에서 1회 호출. 후킹 전 원본 프롤로그를 baseline 으로 저장.
void selfcheck_snapshot() {
    fill_targets();
    for (int i = 0; i < NUM_FUNCS; ++i) {
        // 함수 코드 영역(r-x)에서 첫 SNAP_LEN 바이트를 복사.
        memcpy(g_baseline[i], g_targets[i], SNAP_LEN);
    }
    g_snapped = true;
}

// 감시 대상 중 하나라도 프롤로그가 baseline 과 다르면(트램폴린 삽입) true.
bool check_inline_hooks() {
    if (!g_snapped) {
        return false;   // 스냅샷 전이면 판단 보류(오탐 방지)
    }
    for (int i = 0; i < NUM_FUNCS; ++i) {
        if (memcmp(g_baseline[i], g_targets[i], SNAP_LEN) != 0) {
            return true;
        }
    }
    return false;
}
