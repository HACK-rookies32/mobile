// =============================================================================
// root_check.cpp — 루팅 탐지
// -----------------------------------------------------------------------------
// [무엇을 탐지하나] 기기가 루팅되었는지 세 가지 흔적으로 판단한다.
//   (1) su 바이너리 존재       : 루팅의 핵심인 관리자 권한 획득 도구
//   (2) Magisk 흔적            : 오늘날 가장 흔한 루팅 솔루션의 파일/앱 자취
//   (3) 빌드 태그 test-keys    : 정품 펌웨어는 release-keys, 커스텀/루팅 이미지는 test-keys
//
// [Lv2 변경] 검사 대상 문자열(su 경로/Magisk/프로퍼티/"test-keys")을 전부 OBF() 로
//   컴파일타임 암호화한다 → strings/jadx/메모리 스캔으로 "무엇을 검사하는지" 안 보임(Layer B).
//   문자열 배열(정적 .rodata)은 평문이 그대로 남으므로, 각 경로를 OBF() 로 감싸
//   access() 에 바로 넘기는 형태로 바꿨다(임시 객체가 그 문장 동안만 유효 → 즉시 사용).
//
// [어떻게 우회되나 — 의도된 약점]
//   - Magisk DenyList/Zygisk 는 아래 경로 검사를 앱에게 숨겨 무력화한다.
//   - Frida 로 후킹해 반환값을 false 로 덮으면 무력화(단, Lv2 집계/자체무결성이 잡음).
// =============================================================================

#include "defense.h"
#include "obfuscate.h"              // OBF() : 컴파일타임 문자열 암호화
#include <unistd.h>                 // access() : 파일 존재/권한 확인
#include <sys/system_properties.h>  // __system_property_get() : ro.* 프로퍼티 읽기
#include <cstring>

namespace {

// access(path, F_OK) == 0 → 파일 존재. 하나라도 존재하면 루팅 흔적.
// 각 경로를 OBF() 로 감싸 암호화한다. (배열로 두면 평문이 .rodata 에 남아 OBF 효과가 사라짐)
bool hasSuspiciousFile() {
    return access(OBF("/system/bin/su"), F_OK) == 0            // 표준 su 경로들
        || access(OBF("/system/xbin/su"), F_OK) == 0
        || access(OBF("/sbin/su"), F_OK) == 0
        || access(OBF("/system/su"), F_OK) == 0
        || access(OBF("/vendor/bin/su"), F_OK) == 0
        || access(OBF("/su/bin/su"), F_OK) == 0
        || access(OBF("/data/local/bin/su"), F_OK) == 0
        || access(OBF("/data/local/xbin/su"), F_OK) == 0
        || access(OBF("/system/app/Superuser.apk"), F_OK) == 0 // 구형 SuperSU 앱
        || access(OBF("/sbin/.magisk"), F_OK) == 0             // Magisk 마운트 흔적
        || access(OBF("/data/adb/magisk"), F_OK) == 0          // Magisk 설치 디렉토리
        || access(OBF("/data/adb/modules"), F_OK) == 0;        // Magisk 모듈 디렉토리
}

// 시스템 프로퍼티 ro.build.tags 에 "test-keys" 가 있으면 커스텀/루팅 이미지 의심.
// 정품(구글 인증) 펌웨어는 "release-keys" 로 끝난다.
bool hasTestKeys() {
    char value[PROP_VALUE_MAX] = {0};   // PROP_VALUE_MAX = 92
    // 프로퍼티 이름도 OBF 로 숨긴다. 반환값은 문자열 길이(없으면 0).
    int len = __system_property_get(OBF("ro.build.tags"), value);
    if (len <= 0) {
        return false;
    }
    return strstr(value, OBF("test-keys")) != nullptr;
}

} // namespace

// defense.h 진입점. native-lib.cpp 의 JNI 함수와 집계 함수가 이걸 호출한다.
bool check_root() {
    return hasSuspiciousFile() || hasTestKeys();
}
