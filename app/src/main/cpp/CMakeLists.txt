# 네이티브 방어 라이브러리(libdefense.so) 빌드 설정.
# 방어 로직을 Kotlin 이 아닌 C/C++ 로 내리는 이유: jadx 로 원본에 가깝게 디컴파일되는
# Kotlin/Java 와 달리, .so 는 어셈블리로 컴파일돼 정적 분석 난이도가 올라간다.
# 다만 이 데모의 목표는 "교과서 수준(우회 가능)" 방어이므로, 체크별로 JNI 함수를 분리해
# Frida 후킹 지점이 분명히 드러나도록 둔다.
cmake_minimum_required(VERSION 3.22.1)

project("defense")

add_library(defense SHARED
        native-lib.cpp
        root_check.cpp
        frida_check.cpp
        integrity_check.cpp
        antidebug_check.cpp)

# __android_log_print 로 방어 통과/탐지를 logcat 에 남긴다(시연 가시성).
find_library(log-lib log)
target_link_libraries(defense ${log-lib})
