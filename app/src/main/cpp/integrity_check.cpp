// =============================================================================
// integrity_check.cpp — 앱 무결성(서명) 검증
// -----------------------------------------------------------------------------
// [무엇을 하나] 실행 중인 APK 의 서명 인증서 지문(SHA-256)을 만들어, 코드에 박아둔
//   정품 지문 상수(EXPECTED_SIGNATURE_SHA256)와 비교한다. 다르면 = 리패킹/재서명 = 위반.
//
// [3단계]
//   (1) JNI reflection : C++ 에서 안드로이드 자바 API 를 거꾸로 호출해 서명 바이트 취득.
//       - API 28 미만: PackageManager.GET_SIGNATURES  → PackageInfo.signatures
//       - API 28 이상: GET_SIGNING_CERTIFICATES        → PackageInfo.signingInfo.getApkContentsSigners()
//       (minSdk 26 이므로 두 경로 모두 구현)
//   (2) SHA-256       : 서명 바이트 → 64자리 16진 지문. NDK 기본 crypto 가 없어 직접 구현.
//   (3) 비교          : EXPECTED_SIGNATURE_SHA256 과 대조.
//
// [나중에 정한다 워크플로우]
//   정품 지문은 데모 keystore 를 만든 뒤에야 안다. 그래서 상수를 비워두면(=""),
//   차단하지 않고 실제 지문을 logcat 에 찍는다. 첫 빌드에서 그 값을 상수에 붙이면
//   그때부터 검증이 켜진다. 지문을 모르는 동안의 자멸을 막는 장치.
//
// [자멸 방지 2중] 여기에 더해 Kotlin 쪽에서 BuildConfig.DEBUG 면 호출 자체를 건너뛴다.
//
// [어떻게 우회되나] Frida 로 nativeCheckIntegrity 반환값을 false 로 덮거나 비교 직전 후킹.
// =============================================================================

#include "defense.h"
#include "crypto_native.h"   // crypto::sha256_hex (SHA-256 은 공용 유닛으로 이관)
#include <android/log.h>
#include <cstdint>
#include <string>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, DEFENSE_LOG_TAG, __VA_ARGS__)

// -----------------------------------------------------------------------------
// [정품 서명 지문] 데모 keystore 로 첫 빌드 후, logcat 에 찍힌 값을 여기 붙인다.
//   비어 있으면("") 검증을 건너뛰고 실제 지문만 로그로 남긴다(자멸 방지).
//   64자리 소문자 16진 문자열. 예: "a1b2c3...".
// -----------------------------------------------------------------------------
// 데모 keystore(app/demo.keystore, alias=kidhack-demo)로 서명한 release 의 지문.
// 첫 빌드에서 logcat("integrity: signature sha256 = ...")로 뽑아 채웠다.
// 리패킹 후 재서명하면 이 값과 달라져 위·변조로 탐지된다.
static const char *EXPECTED_SIGNATURE_SHA256 =
        "562060aa3dc6420bd21f4ad225a8961833f819f654808255e779adf8f7d30452";

// PackageManager 플래그 상수(안드로이드 SDK 값과 동일).
static const jint GET_SIGNATURES = 0x40;                 // 64, deprecated(API<28)
static const jint GET_SIGNING_CERTIFICATES = 0x08000000; // 134217728, API>=28

namespace {

// SHA-256 은 crypto_native.cpp(crypto::sha256_hex)로 이관했다. 아래 서명 취득 로직만 남는다.

// ===== JNI reflection: 현재 APK 의 첫 서명 바이트 취득 =========================
// 실패 시 nullptr 반환. 성공 시 호출자가 env->GetByteArrayElements 로 읽는다.
jbyteArray getSignatureBytes(JNIEnv *env, jobject context) {
    // context.getPackageManager()
    jclass contextClass = env->GetObjectClass(context);
    jmethodID getPM = env->GetMethodID(contextClass, "getPackageManager",
                                       "()Landroid/content/pm/PackageManager;");
    jmethodID getPkgName = env->GetMethodID(contextClass, "getPackageName",
                                            "()Ljava/lang/String;");
    if (getPM == nullptr || getPkgName == nullptr) return nullptr;

    jobject pm = env->CallObjectMethod(context, getPM);
    jstring pkgName = (jstring) env->CallObjectMethod(context, getPkgName);
    if (pm == nullptr || pkgName == nullptr) return nullptr;

    jclass pmClass = env->GetObjectClass(pm);
    jmethodID getPkgInfo = env->GetMethodID(pmClass, "getPackageInfo",
                                            "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
    if (getPkgInfo == nullptr) return nullptr;

    // Build.VERSION.SDK_INT 정적 필드 읽기 → 어느 API 경로를 쓸지 결정.
    jclass versionClass = env->FindClass("android/os/Build$VERSION");
    jfieldID sdkField = env->GetStaticFieldID(versionClass, "SDK_INT", "I");
    jint sdkInt = env->GetStaticIntField(versionClass, sdkField);

    jobjectArray signatures = nullptr;

    if (sdkInt >= 28) {
        // 최신 경로: getPackageInfo(pkg, GET_SIGNING_CERTIFICATES).signingInfo.getApkContentsSigners()
        jobject pkgInfo = env->CallObjectMethod(pm, getPkgInfo, pkgName, GET_SIGNING_CERTIFICATES);
        if (pkgInfo == nullptr) return nullptr;
        jclass pkgInfoClass = env->GetObjectClass(pkgInfo);
        jfieldID signingInfoField = env->GetFieldID(pkgInfoClass, "signingInfo",
                                                    "Landroid/content/pm/SigningInfo;");
        jobject signingInfo = env->GetObjectField(pkgInfo, signingInfoField);
        if (signingInfo == nullptr) return nullptr;
        jclass signingInfoClass = env->GetObjectClass(signingInfo);
        jmethodID getSigners = env->GetMethodID(signingInfoClass, "getApkContentsSigners",
                                                "()[Landroid/content/pm/Signature;");
        signatures = (jobjectArray) env->CallObjectMethod(signingInfo, getSigners);
    } else {
        // 구형 경로: getPackageInfo(pkg, GET_SIGNATURES).signatures
        jobject pkgInfo = env->CallObjectMethod(pm, getPkgInfo, pkgName, GET_SIGNATURES);
        if (pkgInfo == nullptr) return nullptr;
        jclass pkgInfoClass = env->GetObjectClass(pkgInfo);
        jfieldID signaturesField = env->GetFieldID(pkgInfoClass, "signatures",
                                                   "[Landroid/content/pm/Signature;");
        signatures = (jobjectArray) env->GetObjectField(pkgInfo, signaturesField);
    }

    if (signatures == nullptr || env->GetArrayLength(signatures) == 0) return nullptr;

    // 첫 번째 서명의 toByteArray() → 인증서 DER 바이트.
    jobject sig0 = env->GetObjectArrayElement(signatures, 0);
    jclass sigClass = env->GetObjectClass(sig0);
    jmethodID toByteArray = env->GetMethodID(sigClass, "toByteArray", "()[B");
    return (jbyteArray) env->CallObjectMethod(sig0, toByteArray);
}

} // namespace

bool check_integrity(JNIEnv *env, jobject context) {
    jbyteArray sigBytes = getSignatureBytes(env, context);
    if (sigBytes == nullptr) {
        // 서명을 못 읽으면 판단 불가 → 차단하지 않는다(오탐으로 앱이 죽는 것 방지).
        LOGI("integrity: signature unavailable, skip");
        return false;
    }

    jsize len = env->GetArrayLength(sigBytes);
    jbyte *raw = env->GetByteArrayElements(sigBytes, nullptr);
    std::string actual = crypto::sha256_hex(reinterpret_cast<const uint8_t *>(raw), (size_t) len);
    env->ReleaseByteArrayElements(sigBytes, raw, JNI_ABORT);  // 변경 안 했으니 되쓰지 않고 해제

    // 실제 지문을 항상 로그로 남긴다 → 첫 빌드에서 이 값을 EXPECTED_SIGNATURE_SHA256 에 붙인다.
    LOGI("integrity: signature sha256 = %s", actual.c_str());

    // 상수가 비어 있으면 아직 미설정 → 차단하지 않는다(자멸 방지).
    if (EXPECTED_SIGNATURE_SHA256[0] == '\0') {
        LOGI("integrity: EXPECTED hash not set, skip enforcement");
        return false;
    }

    // 지문 불일치 = 리패킹/재서명 = 위반.
    bool violated = (actual != EXPECTED_SIGNATURE_SHA256);
    return violated;
}