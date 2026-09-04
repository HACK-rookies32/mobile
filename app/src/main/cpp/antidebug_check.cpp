// =============================================================================
// antidebug_check.cpp — 안티디버깅(디버거 attach 탐지)
// -----------------------------------------------------------------------------
// [무엇을 탐지하나] gdb/IDA/Android Studio 디버거 등이 프로세스에 붙었는지.
//   /proc/self/status 의 TracerPid : 추적 중인 디버거의 PID. 아무도 안 붙었으면 0.
//
// [왜 TracerPid 만 쓰나 — ptrace(PTRACE_TRACEME) 를 뺀 이유]
//   흔한 교과서 기법인 ptrace(PTRACE_TRACEME) "선점" 은 릴리스/실기기에서
//   SELinux·seccomp·yama ptrace_scope 때문에 디버거가 없어도 -1(EPERM)을 돌려줘
//   *오탐* 을 낸다(실측: 정상 S8 에서 TracerPid=0 인데 TRACEME 는 -1). 게다가 성공 시
//   프로세스를 traced 상태로 만드는 부작용이 있다. 그래서 신뢰할 수 있는 TracerPid 만 쓴다.
//
// [어떻게 우회되나 — 의도된 약점]
//   /proc/self/status 읽기를 후킹해 TracerPid 를 0 으로 위조하거나,
//   Frida 로 nativeIsDebuggerAttached 반환값을 false 로 덮으면 무력화된다.
// =============================================================================

#include "defense.h"
#include "obfuscate.h"  // OBF() : 컴파일타임 문자열 암호화 (Layer B)
#include <unistd.h>     // read/close
#include <fcntl.h>      // open, O_RDONLY
#include <cstring>      // strstr, strlen
#include <cstdlib>      // atoi

namespace {

// /proc/self/status 에서 "TracerPid:" 줄을 찾아 그 값이 0 이 아니면 디버거로 판정.
// 예시 줄:  "TracerPid:\t0"  (탭 뒤 숫자)
bool hasTracer() {
    int fd = open(OBF("/proc/self/status"), O_RDONLY);
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
    // OBF() 임시 객체는 그 문장에서만 유효하므로(변수에 담으면 dangling),
    // 로컬 배열에 복사해 두고(복사 시점엔 임시가 살아있음) 두 번(strstr/strlen) 쓴다.
    char label[16];
    strcpy(label, OBF("TracerPid:"));
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
    return hasTracer();
}