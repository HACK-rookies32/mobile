// =============================================================================
// frida_check.cpp — Frida 탐지
// -----------------------------------------------------------------------------
// [무엇을 탐지하나] Frida 는 실행 중인 앱 메모리에 JS 를 주입해 함수를 후킹하는
//   동적 계측 도구다(이 데모의 주 우회 무기). 두 가지 흔적으로 찾는다.
//   (1) /proc/self/maps 에 frida/gadget/gum 이름의 메모리 매핑이 있는지
//   (2) Frida 기본 포트(27042)가 열려 대기 중인지
//
// [어떻게 우회되나 — 의도된 약점]
//   - frida-gadget 을 이름 바꿔 빌드하거나 포트를 바꾸면 아래 검사를 피한다.
//   - Frida 스크립트로 nativeIsFridaDetected 반환값을 덮으면 무력화된다
//     (Frida 가 Frida 탐지를 뚫는 그림 → 시연 임팩트).
//
// [구현 노트] /proc/self/maps 는 C++ ifstream 대신 저수준 open/read 로 읽는다.
//   네이티브에서 syscall 이 더 가볍고, 바이너리에 남는 문자열이 적다(교과서 관행).
// =============================================================================

#include "defense.h"
#include "obfuscate.h"    // OBF() : 컴파일타임 문자열 암호화 (Layer B)
#include <unistd.h>       // open/read/close
#include <fcntl.h>        // O_RDONLY
#include <cstring>        // strstr
#include <sys/socket.h>   // socket/connect
#include <netinet/in.h>   // sockaddr_in
#include <arpa/inet.h>    // htons, inet_addr

namespace {

// /proc/self/maps 를 한 덩어리씩 읽으며 Frida 관련 문자열을 찾는다.
// self = 현재 프로세스 자신이므로 별도 권한 없이 읽을 수 있다.
// [Lv2] 경로("/proc/self/maps")와 시그니처("frida"/"gadget"/"gum-js")를 OBF 로 암호화.
bool hasFridaInMaps() {
    int fd = open(OBF("/proc/self/maps"), O_RDONLY);
    if (fd < 0) {
        return false;   // 못 열면 판단 불가 → 흔적 없음으로 처리
    }

    char buffer[512];
    ssize_t bytesRead;
    bool found = false;

    // read 는 읽은 바이트 수를 반환하고, 파일 끝에 도달하면 0 을 반환한다.
    // 512바이트씩 끊어 읽으며 각 조각에서 부분 문자열을 검사한다.
    // (주의: 경계에 걸친 문자열은 놓칠 수 있으나, 교과서 수준에선 충분하다.)
    while ((bytesRead = read(fd, buffer, sizeof(buffer) - 1)) > 0) {
        buffer[bytesRead] = '\0';                       // strstr 을 위해 널 종료
        if (strstr(buffer, OBF("frida")) != nullptr ||  // libfrida-gadget 등
            strstr(buffer, OBF("gadget")) != nullptr ||  // frida-gadget 주입 흔적
            strstr(buffer, OBF("gum-js")) != nullptr) {  // Frida 의 Gum 엔진 JS 루프
            found = true;
            break;
        }
    }

    close(fd);
    return found;
}

// Frida 기본 포트(27042)에 로컬 연결을 시도한다.
// connect 가 성공(0)하면 누군가 그 포트에서 대기 중 → frida-server 가능성.
bool isFridaPortOpen() {
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) {
        return false;
    }

    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(27042);                    // Frida 기본 포트
    addr.sin_addr.s_addr = inet_addr("127.0.0.1");   // 루프백(자기 자신)

    // connect 반환값 0 = 연결 성공 = 포트 열림.
    bool open = (connect(sock, reinterpret_cast<sockaddr *>(&addr), sizeof(addr)) == 0);
    close(sock);
    return open;
}

} // namespace

bool check_frida() {
    return hasFridaInMaps() || isFridaPortOpen();
}