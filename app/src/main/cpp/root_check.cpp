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

} // namespace

// defense.h 에 선언된 진입점. native-lib.cpp 의 JNI 함수가 이걸 호출한다.
bool check_root() {
    // 둘 중 하나라도 걸리면 루팅으로 판정. (교과서 수준: 검사 항목을 더 늘리지 않는다)
    return hasSuspiciousFile() || hasTestKeys();
}
