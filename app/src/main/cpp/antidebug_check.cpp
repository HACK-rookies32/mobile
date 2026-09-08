// =============================================================================
// antidebug_check.cpp — 안티디버깅(디버거 attach 탐지)
// -----------------------------------------------------------------------------
// [무엇을 탐지하나] gdb/IDA/Android Studio 디버거 등이 프로세스에 붙었는지.
//   - /proc/self/status 의 TracerPid : 추적 중인 디버거 PID. 없으면 0.
//
// [어떻게 우회되나 — 의도된 약점]
//   - /proc/self/status 읽기를 후킹해 TracerPid 를 0 으로 위조.
//
// [주의] 에뮬레이터/특정 플랫폼에서 오탐 가능. logcat 로그(native-lib.cpp)로 구분한다.
// =============================================================================

#include "defense.h"
#include <unistd.h>     // read/close
#include <fcntl.h>      // open, O_RDONLY
#include <cstring>      // strncmp, strstr
#include <cstdlib>      // atoi

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

} // namespace

bool check_debugger() {
    // TracerPid 단독 판정. 읽기만 하므로 몇 번을 호출해도 프로세스 상태를 바꾸지 않는다
    // (= 시작 검사·화면 진입 검사로 여러 번 불려도 안전).
    return hasTracer();
}