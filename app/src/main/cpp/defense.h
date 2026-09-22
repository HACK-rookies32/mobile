// 방어 체크 4종의 공통 선언.
// 각 cpp 파일이 순수 C++ 함수 하나를 구현하고, native-lib.cpp 가 이를 JNI 로 노출한다.
#ifndef KIDHACK_DEFENSE_H
#define KIDHACK_DEFENSE_H

#include <jni.h>

#define DEFENSE_LOG_TAG "KidhackDefense"

// 루팅 흔적(su 바이너리, Magisk, test-keys 빌드)이 있으면 true.
bool check_root();

// 에뮬레이터(AVD/Nox/Genymotion 등) 지문(QEMU 프로퍼티, goldfish 디바이스 파일)이 있으면 true.
bool check_emulator();

// Frida 흔적(frida-server/gadget 매핑, 27042 포트, gum 스레드)이 있으면 true.
bool check_frida();

// 디버거가 붙어 있으면(TracerPid != 0 또는 ptrace 선점 실패) true.
bool check_debugger();

// 실행 중 APK 서명이 정품 해시와 다르면(리패킹/재서명) true.
// context 로 PackageManager 를 조회한다. 위반이면 true.
bool check_integrity(JNIEnv *env, jobject context);

#endif // KIDHACK_DEFENSE_H
