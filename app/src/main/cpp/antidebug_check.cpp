// =============================================================================
// antidebug_check.cpp — 안티디버깅(디버거 attach 탐지)
// -----------------------------------------------------------------------------
// [무엇을 탐지하나] gdb/IDA/Android Studio 디버거 등이 프로세스에 붙었는지.
//   (1) /proc/self/status 의 TracerPid  : 추적 중인 디버거 PID. 없으면 0.
//   (2) ptrace(PTRACE_TRACEME) 선점       : 프로세스당 tracer 는 하나뿐이라는 성질 이용.
//
// [어떻게 우회되나 — 의도된 약점]
//   - /proc/self/status 읽기를 후킹해 TracerPid 를 0 으로 위조.
//   - ptrace syscall 자체를 후킹.
//
// [주의] 에뮬레이터/특정 플랫폼에서 오탐 가능. logcat 로그(native-lib.cpp)로 구분한다.
// =============================================================================

#include "defense.h"
#include <unistd.h>     // read/close
#include <fcntl.h>      // open, O_RDONLY
#include <cstring>      // strncmp, strstr
#include <cstdlib>      // atoi
#include <sys/ptrace.h> // ptrace, PTRACE_TRACEME

namespace {

// /proc/self/status 에서 "TracerPid:" 줄을 찾아 그 값이 0 이 아니면 디버거로 판정.
// 예시 줄:  "TracerPid:\t0"  (탭 뒤 숫자)
bool hasTracer() {
    int fd = open("/proc/self/status", O_RDONLY);
    if (fd < 0) {
        return false;
    }

    char buffer[4096] = {0};
    ssize_t total = read(fd, buffer, sizeof(buffer) - 1);
    close(fd);
    if (total <= 0) {
        return false;
    }
    buffer[total] = '\0';

    // "TracerPid:" 라벨 위치를 찾는다.
    const char *label = "TracerPid:";
    char *p = strstr(buffer, label);
    if (p == nullptr) {
        return false;
    }
    p += strlen(label);          // 라벨 다음(공백/탭 앞)으로 이동
    while (*p == ' ' || *p == '\t') {
        p++;                     // 공백·탭 건너뛰기
    }
    // atoi 는 숫자 문자열을 int 로. 0 이 아니면 누군가 추적 중이라는 뜻.
    return atoi(p) != 0;
}

// 스스로를 PTRACE_TRACEME 로 tracer 등록 시도.
//   - 이미 디버거가 붙어 있으면 이 호출은 -1(실패) 을 반환한다 → 디버거 존재.
//   - 성공(0)하면 이제 이 프로세스는 자기 자신이 tracer 라서, 이후 외부 디버거가
//     attach 하려 하면 실패한다(선점 효과). 즉 탐지이자 예방 두 역할.
bool selfTraceBlocked() {
    // 반환 -1 = TRACEME 실패 = 이미 다른 tracer 존재.
    long result = ptrace(PTRACE_TRACEME, 0, nullptr, nullptr);
    return result == -1;
}

} // namespace

bool check_debugger() {
    return hasTracer() || selfTraceBlocked();
}