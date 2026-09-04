# =============================================================================
# build-defense-so.ps1 — 네이티브 방어 라이브러리(libdefense.so) 빌드 헬퍼
# -----------------------------------------------------------------------------
# [왜 이 스크립트가 필요한가]
#   이 프로젝트 경로에 한글("3. 개발\소스코드")이 있어 ndk-build(GNU make)가 소스를
#   못 읽고, SDK cmake.exe 는 이 머신에서 크래시한다. 그래서 gradle 빌드 단계에서
#   네이티브를 컴파일하지 않는다. 대신 이 스크립트가:
#     (1) cpp 소스를 ASCII 임시 경로로 복사   (make 에 한글 경로를 안 보여줌)
#     (2) ndk-build 로 libdefense.so 를 arm64-v8a / x86_64 로 빌드
#     (3) 결과 .so 를 이 모듈의 src/main/jniLibs/<abi>/ 로 복사 (AGP 가 패키징)
#   cpp 를 수정하면 이 스크립트를 다시 실행하면 된다.
#
# [사용법]  powershell -ExecutionPolicy Bypass -File build-defense-so.ps1
# =============================================================================

$ErrorActionPreference = "Stop"

# 이 스크립트가 있는 cpp 디렉토리(한글 경로여도 PowerShell 은 문제없음).
$cppDir = $PSScriptRoot
# 모듈 루트(app) → jniLibs 목적지.
$jniLibs = Join-Path $cppDir "..\jniLibs" | Resolve-Path -ErrorAction SilentlyContinue
if (-not $jniLibs) { $jniLibs = (New-Item -ItemType Directory -Force (Join-Path $cppDir "..\jniLibs")).FullName }

# NDK 경로 탐색(설치본 우선순위: local.properties → 기본 SDK 위치).
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }
$ndkRoot = Join-Path $sdk "ndk"
$ndk = Get-ChildItem $ndkRoot -Directory | Sort-Object Name -Descending | Select-Object -First 1
if (-not $ndk) { throw "NDK 를 찾을 수 없습니다: $ndkRoot" }
$ndkBuild = Join-Path $ndk.FullName "ndk-build.cmd"

# ASCII 임시 빌드 경로.
$stage = Join-Path $env:TEMP "kidhack_defense_build"
$jni = Join-Path $stage "jni"
New-Item -ItemType Directory -Force $jni | Out-Null

# cpp 소스/빌드스크립트를 임시 jni 로 복사(gradle 이 안 쓰는 CMakeLists.txt 는 제외).
Copy-Item (Join-Path $cppDir "*.cpp") $jni -Force
Copy-Item (Join-Path $cppDir "*.h") $jni -Force
Copy-Item (Join-Path $cppDir "Android.mk") $jni -Force
Copy-Item (Join-Path $cppDir "Application.mk") $jni -Force

Write-Host "NDK      : $($ndk.FullName)"
Write-Host "Staging  : $stage"
Write-Host "jniLibs  : $jniLibs"
Write-Host "ndk-build 실행..."

Push-Location $stage
try {
    & $ndkBuild "NDK_PROJECT_PATH=$stage" "APP_BUILD_SCRIPT=$jni\Android.mk" "NDK_APPLICATION_MK=$jni\Application.mk"
    if ($LASTEXITCODE -ne 0) { throw "ndk-build 실패(exit $LASTEXITCODE)" }
} finally {
    Pop-Location
}

# 결과 .so 를 jniLibs 로 복사.
foreach ($abi in @("arm64-v8a", "x86_64")) {
    $srcSo = Join-Path $stage "libs\$abi\libdefense.so"
    $dstDir = Join-Path $jniLibs $abi
    New-Item -ItemType Directory -Force $dstDir | Out-Null
    Copy-Item $srcSo (Join-Path $dstDir "libdefense.so") -Force
    Write-Host "복사됨: $abi\libdefense.so"
}

Write-Host "완료. jniLibs 에 libdefense.so 배치됨."