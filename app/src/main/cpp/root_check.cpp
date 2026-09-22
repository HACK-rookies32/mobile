// =============================================================================
// root_check.cpp — 루팅 탐지
// -----------------------------------------------------------------------------
// [무엇을 탐지하나] 기기가 루팅되었는지 세 가지 흔적으로 판단한다.
//   (1) su 바이너리 존재       : 루팅의 핵심인 관리자 권한 획득 도구
//   (2) Magisk 흔적            : 오늘날 가장 흔한 루팅 솔루션의 파일/앱 자취
//   (3) 빌드 태그 test-keys    : 정품 펌웨어는 release-keys, 커스텀/루팅 이미지는 test-keys
//
// [어떻게 우회되나 — 의도된 약점]
//   - Magisk DenyList/Zygisk 는 아래 경로 검사를 앱에게 숨겨 무력화한다.
//   - Frida 로 nativeIsRooted 의 반환값을 false 로 덮으면 이 로직 전체가 무의미해진다.
//   교과서적 기법이라 우회법도 잘 알려져 있다 → 시연용으로 적합.
// =============================================================================

#include "defense.h"
#include <unistd.h>                 // access() : 파일 존재/권한 확인
#include <sys/system_properties.h>  // __system_property_get() : ro.* 프로퍼티 읽기
#include <cstring>

namespace {

// 루팅 기기에서 흔히 발견되는 su 바이너리 및 Magisk 관련 경로들.
// 정상 기기에는 이 중 어느 것도 존재하지 않는다.
const char *kSuspiciousPaths[] = {
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/su",
        "/vendor/bin/su",
        "/su/bin/su",
        "/data/local/bin/su",
        "/data/local/xbin/su",
        "/system/app/Superuser.apk",   // 구형 SuperSU 앱
        "/sbin/.magisk",               // Magisk 마운트 흔적
        "/data/adb/magisk",            // Magisk 설치 디렉토리
        "/data/adb/modules",           // Magisk 모듈 디렉토리
};

// access(path, F_OK) 는 파일이 "존재하면" 0 을 반환한다.
// 하나라도 존재하면 루팅 흔적으로 본다.
bool hasSuspiciousFile() {
    for (const char *path : kSuspiciousPaths) {
        if (access(path, F_OK) == 0) {
            return true;
        }
    }
    return false;
}

// 시스템 프로퍼티 ro.build.tags 를 읽어 "test-keys" 가 들어 있는지 본다.
// 정품(구글 인증) 펌웨어는 "release-keys" 로 끝난다.
bool hasTestKeys() {
    char value[PROP_VALUE_MAX] = {0};   // PROP_VALUE_MAX = 92, 프로퍼티 최대 길이
    // 반환값은 문자열 길이. 프로퍼티가 없으면 0.
    int len = __system_property_get("ro.build.tags", value);
    if (len <= 0) {
        return false;
    }
    // strstr: value 안에 "test-keys" 부분 문자열이 있으면 그 위치 포인터, 없으면 nullptr.
    return strstr(value, "test-keys") != nullptr;
}

// =============================================================================
// 에뮬레이터 탐지용 헬퍼
// -----------------------------------------------------------------------------
// [왜 에뮬레이터를 탐지하나] 실제 사용자는 실기기로 앱을 쓴다. 에뮬레이터(AVD/Nox 등)
//   에서 실행 중이라는 것은 분석·리버싱·자동화 공격 환경일 가능성이 높다는 신호다.
//   기존 루팅 탐지(su/test-keys)는 google_apis AVD 처럼 루팅되지 않은 정식 에뮬레이터
//   이미지에서는 흔적이 없어 조용히 통과한다 — 그 공백을 이 검사가 메운다.
//
// [어떻게 우회되나 — 의도된 약점] 아래 프로퍼티/파일은 커스텀 ROM 이나 프로퍼티 조작으로
//   숨길 수 있고, Frida 로 nativeIsEmulator 반환값을 false 로 덮으면 그대로 무력화된다.
// =============================================================================

// 특정 시스템 프로퍼티 값이 기대 문자열과 정확히 같은지 본다.
bool propEquals(const char *key, const char *expected) {
    char value[PROP_VALUE_MAX] = {0};
    int len = __system_property_get(key, value);
    if (len <= 0) {
        return false;
    }
    return strcmp(value, expected) == 0;
}

// 특정 시스템 프로퍼티 값 안에 needle 부분 문자열이 들어 있는지 본다.
bool propContains(const char *key, const char *needle) {
    char value[PROP_VALUE_MAX] = {0};
    int len = __system_property_get(key, value);
    if (len <= 0) {
        return false;
    }
    return strstr(value, needle) != nullptr;
}

// 에뮬레이터에서만 존재하는 디바이스·라이브러리 경로. 실기기에는 이 중 어느 것도 없다.
// [왜 파일까지 보나] Nox 처럼 프로퍼티를 실기기(예: 삼성 SM-G977N)로 위장하는 에뮬레이터는
//   위의 hasEmulatorProperty() 를 회피한다. 하지만 VirtualBox/Nox 런타임 파일까지 지우지는
//   못해서, 이 파일 흔적으로 위장을 뚫을 수 있다. (물론 파일명 변경·Magisk 로 우회 가능)
const char *kEmulatorPaths[] = {
        // QEMU/goldfish 계열 (AOSP AVD)
        "/dev/qemu_pipe",                          // QEMU 파이프 (에뮬↔호스트 통신)
        "/dev/socket/qemud",                       // QEMU 데몬 소켓
        "/dev/goldfish_pipe",                      // goldfish 커널 파이프
        "/system/lib/libc_malloc_debug_qemu.so",   // QEMU 전용 malloc 디버그 라이브러리
        "/sys/qemu_trace",
        // VirtualBox/Nox 계열 (NoxPlayer 등, 프로퍼티 위장 시에도 남는 흔적)
        "/system/bin/nox-prop",                    // Nox 프로퍼티 데몬
        "/system/bin/noxd",                        // Nox 서비스 바이너리
        "/system/lib/libnoxspeedup.so",            // Nox 가속 라이브러리
        "/system/lib/vboxguest.ko",                // VirtualBox 게스트 커널 모듈
        "/dev/vboxguest",                          // VirtualBox 게스트 디바이스
        "/dev/socket/genyd",                       // Genymotion 데몬 소켓
};

bool hasEmulatorFile() {
    for (const char *path : kEmulatorPaths) {
        if (access(path, F_OK) == 0) {
            return true;
        }
    }
    return false;
}

// 시스템 프로퍼티에서 에뮬레이터 지문을 찾는다.
bool hasEmulatorProperty() {
    // QEMU 커널 플래그 — 대부분의 x86 에뮬레이터에서 "1".
    if (propEquals("ro.kernel.qemu", "1") || propEquals("ro.boot.qemu", "1")) {
        return true;
    }
    // 하드웨어 이름 — goldfish/ranchu(AOSP 에뮬), vbox86(Genymotion), nox(NoxPlayer).
    if (propContains("ro.hardware", "goldfish") ||
        propContains("ro.hardware", "ranchu") ||
        propContains("ro.hardware", "vbox86") ||
        propContains("ro.hardware", "nox")) {
        return true;
    }
    // 제품 모델·빌드 지문 — AVD 는 "sdk_gphone...", "Android SDK built for..." / "generic".
    if (propContains("ro.product.model", "sdk_gphone") ||
        propContains("ro.product.model", "Android SDK built for") ||
        propContains("ro.build.fingerprint", "generic") ||
        propContains("ro.build.fingerprint", "sdk_gphone")) {
        return true;
    }
    return false;
}

} // namespace

// defense.h 에 선언된 진입점. native-lib.cpp 의 JNI 함수가 이걸 호출한다.
bool check_root() {
    // 둘 중 하나라도 걸리면 루팅으로 판정. (교과서 수준: 검사 항목을 더 늘리지 않는다)
    return hasSuspiciousFile() || hasTestKeys();
}

// defense.h 에 선언된 진입점. 프로퍼티 지문 또는 QEMU 디바이스 파일 중 하나라도 걸리면
// 에뮬레이터로 판정한다. (교과서 수준: CPU 타이밍·센서 유무 같은 정교한 검사는 넣지 않는다)
bool check_emulator() {
    return hasEmulatorProperty() || hasEmulatorFile();
}
