# 네이티브 방어 라이브러리(libdefense.so) 빌드 설정 — ndk-build 방식.
#
# [왜 CMake 가 아니라 ndk-build 인가]
#   이 개발 머신에서 SDK 번들 cmake.exe 가 Android 툴체인 configure 중 크래시
#   (STATUS_STACK_BUFFER_OVERRUN)한다. 반면 ndk-build(GNU make + 동일한 NDK clang)는
#   정상 동작한다. 그래서 빌드 시스템만 ndk-build 로 두고, 소스/로직은 그대로 쓴다.
#   (동일한 5개 소스를 libdefense.so 하나로 묶는다 — CMakeLists.txt 와 목적 동일)

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# 만들어질 라이브러리 이름 → libdefense.so. Kotlin 의 System.loadLibrary("defense") 와 일치.
LOCAL_MODULE := defense

# 방어 로직 소스(이 디렉토리 기준 상대경로). 4종 검사 + JNI 브릿지.
LOCAL_SRC_FILES := \
    native-lib.cpp \
    root_check.cpp \
    frida_check.cpp \
    integrity_check.cpp \
    antidebug_check.cpp

# __android_log_print(logcat 출력)을 위해 log 라이브러리 링크.
LOCAL_LDLIBS := -llog

# C++17 사용. JNI 함수는 Java_... 이름 규칙으로 export 되어야 JVM 이 찾는다(교과서 수준).
LOCAL_CPPFLAGS := -std=c++17

include $(BUILD_SHARED_LIBRARY)
