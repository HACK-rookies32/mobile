# ndk-build 전역 설정.
#
# APP_ABI / APP_PLATFORM 은 AGP(externalNativeBuild.ndkBuild)가 gradle 의
# abiFilters 와 minSdk 로 자동 주입하므로 여기서 지정하지 않는다.
# STL 만 명시한다 — 방어 코드는 예외/RTTI 를 안 쓰고 std::string 정도만 쓰므로 정적 링크.
APP_STL := c++_static