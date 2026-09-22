import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// FCM: Firebase 콘솔(kidhack-38a89)에서 받은 app/google-services.json 이 있을 때만 플러그인을 적용한다.
// 파일이 없어도 빌드는 되게 — 그 빌드에선 FirebaseApp 초기화가 실패해 푸시만 조용히 꺼진다
// (PushTokenRegistrar 가 runCatching 으로 삼킨다). 파일은 git 에 올리지 않는다(.gitignore).
val hasGoogleServicesJson = rootProject.file("app/google-services.json").exists()
if (hasGoogleServicesJson) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.lifecycle("kidhack FCM: app/google-services.json 없음 — google-services 플러그인 미적용(푸시 비활성)")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun detectLanIpv4(): String? = runCatching {
    DatagramSocket().use { socket ->
        socket.connect(InetAddress.getByName("8.8.8.8"), 53)
        val addr = socket.localAddress
        if (addr is Inet4Address && !addr.isLoopbackAddress) addr.hostAddress else null
    }
}.getOrNull()

// Prefer host LAN IP on Windows emulators; 10.0.2.2 often times out for OkHttp only.
val apiBaseUrl: String =
    localProperties.getProperty("api.base.url")
        ?: detectLanIpv4()?.let { "http://$it:8080/api" }
        ?: "http://10.0.2.2:8080/api"
logger.lifecycle("kidhack BASE_URL = $apiBaseUrl")

// [보안 방어] 에뮬레이터 탐지 스위치. 기본은 꺼짐 — 개발·테스트를 AVD/Nox 에서 하기 때문에
// 항상 켜면 앱이 매 실행마다 스스로 종료돼 작업이 불가능해진다. 시연할 때만
//   gradlew installApi -PdetectEmulator=true
// 로 켠다. (release 빌드는 아래 buildTypes 에서 별도로 true 로 고정한다.)
val detectEmulator: Boolean =
    (project.findProperty("detectEmulator") as String?)?.toBoolean() ?: false
logger.lifecycle("kidhack DETECT_EMULATOR = $detectEmulator")

// Google Maps API 키. 우선순위: .env → local.properties (둘 다 git 에 커밋되지 않음)
val mapsApiKey: String = run {
    val fromEnv = Properties().apply {
        val f = rootProject.file(".env")
        if (f.exists()) f.inputStream().use { load(it) }
    }.getProperty("MAPS_API_KEY")
    (fromEnv ?: localProperties.getProperty("MAPS_API_KEY") ?: "").trim()
}


android {
    namespace = "com.hack.kidhack"
    compileSdk {
        version = release(37)
    }
    // [보안 방어] 네이티브 빌드에 사용할 NDK 버전 고정(설치본과 일치).
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.hack.kidhack"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BASE_URL", "\"$apiBaseUrl\"")
        manifestPlaceholders["usesCleartextTraffic"] = "false"

        // [VULN-MOBILE-SECRET-1] Google Maps API 키를 빌드 시 Manifest(<meta-data> API_KEY)에 주입. Firebase 키도 app/google-services.json 이 존재해서 플러그인을 사용하게 되면 APK에서 그대로 추출된다. 
        // .env 나 local.properties 에서 키를 읽어 주입하며 두 파일 다 github에 직접 올라가지는 않는다. 빌드된 apk 파일 내부에는 어쩔 수 없이 API 키가 남는다. Google Cloud Console 에서 API 키에 앱 패키지명과 서명 키의 SHA-1을 추가하여 앱 제한을 걸고, Maps SDK for Android에서만 유효하도록 API 제한을 걸어야한다.
        // 의도된 취약점(OWASP Mobile M2 Inadequate Supply Chain / M8). 보존: 고치지 말 것. 명세: mobile/docs/vuln-spec-mobile.md  평가항목: (금취분평)MOB-SER-025 앱 소스코드 내 운영정보 노출 방지 — 항목 정의의 "운영정보 : 서버정보(ID/PW), 고정된 암호키 등"에 해당하고 평가 예시 "애플리케이션을 디컴파일 및 역분석하여 코드상에 운영정보의 노출 여부 점검"에 직접 대응 / (주통기)해당 항목 없음(모바일 단말 보안 대응 항목 부재)
        
        // [보안 방어] 방어 검사 활성화 플래그. DefenseChecker.runAllChecks 의 기본값으로 쓰인다.
        //  - DEFENSE_STRICT : 무결성(서명)·디버거 검사 on/off. debug/mock 은 디버그 서명이라
        //    켜면 자기 자신을 위·변조로 오탐해 죽으므로 기본 false, api/release 에서 true.
        //  - DETECT_EMULATOR: 에뮬레이터 검사 on/off. 위 detectEmulator(-PdetectEmulator) 로 결정.
        buildConfigField("boolean", "DEFENSE_STRICT", "false")
        buildConfigField("boolean", "DETECT_EMULATOR", "$detectEmulator")

        // AndroidManifest의 ${MAPS_API_KEY} 자리에 주입
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey

        // [보안 방어] 빌드할 ABI 를 실기기(arm64-v8a)와 에뮬레이터(x86_64)로 제한한다.
        // x86_64 를 빠뜨리면 에뮬에서 .so 를 못 찾아 UnsatisfiedLinkError 가 난다.
        // (컴파일 옵션 -std=c++17 등은 Android.mk 에서 지정)
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    // [보안 방어] 데모 서명 설정.
    // release 를 고정 keystore 로 서명해 무결성 검사(integrity_check.cpp)가 대조할
    // 안정적인 서명 지문을 갖게 한다. 리패킹 후 재서명하면 지문이 달라져 탐지되는 시연을 위해서다.
    // 주의: R8/난독화 비활성(VULN-MOBILE-RE-1)은 그대로 유지한다 — 여기서는 "서명"만 추가한다.
    // keystore 비밀번호는 데모용이라 그대로 둔다(이 프로젝트는 의도적 취약점 실습용).
    signingConfigs {
        create("demo") {
            storeFile = file("demo.keystore")
            storePassword = "kidhack"
            keyAlias = "kidhack-demo"
            keyPassword = "kidhack"
        }
    }

    buildTypes {
        // 목업 UI 테스트 (Fake repos)
        debug {
            buildConfigField("boolean", "USE_MOCK", "true")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        // 로컬/실서버 API 연동 테스트 (debuggable, mock off)
        create("api") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            applicationIdSuffix = ".api"
            versionNameSuffix = "-api"
            buildConfigField("boolean", "USE_MOCK", "false")
            buildConfigField("String", "BASE_URL", "\"$apiBaseUrl\"")
            // [보안 방어] api 변형에서도 무결성·디버거 검사를 켠다(실기기 연동 시연용).
            // 그래서 demo keystore 로 서명해 정품 지문(562060aa...)과 일치시킨다 —
            // 서명이 없으면 무결성 검사가 자기 자신을 위·변조로 오탐해 앱이 죽는다.
            // 주의: 기존에 debug 키로 깔린 com.hack.kidhack.api 는 서명이 달라
            //       INSTALL_FAILED_UPDATE_INCOMPATIBLE 이 나므로 최초 1회 adb uninstall 필요.
            buildConfigField("boolean", "DEFENSE_STRICT", "true")
            signingConfig = signingConfigs.getByName("demo")
            // [VULN-MOBILE-NET-1] api 변형에서 평문 HTTP 허용(usesCleartextTraffic=true). networkSecurityConfig
            // 미설정 + 인증서 피닝 없음 → MITM 노출. 의도된 취약점(OWASP Mobile M5). 명세: mobile/docs/vuln-spec-mobile.md
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        // 배포·보안 실습용 (서명 필요). keystore 연결 후 signingConfig 지정.
        release {
            buildConfigField("boolean", "USE_MOCK", "false")
            buildConfigField(
                "String",
                "BASE_URL",
                "\"https://kidhackapp.com/api\"",
            )
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            // [보안 방어] release 는 실기기 배포용 → 무결성·디버거·에뮬레이터 검사를 모두 켠다.
            buildConfigField("boolean", "DEFENSE_STRICT", "true")
            buildConfigField("boolean", "DETECT_EMULATOR", "true")
            // [보안 방어] 데모 keystore 로 서명 → 무결성 검사가 대조할 안정적 지문 확보.
            signingConfig = signingConfigs.getByName("demo")
            // [VULN-MOBILE-RE-1] release 빌드에서의 optimization (enable=false) 적용 시 R8(코드 축소·최적화·난독화 등을 수행하는 도구), 서명관련 패스워드가 하드코딩되어 있으나 앱을 변조하려면 개인키(demo.keystore)가 필요하다. keystore는 gitignore로 무시되어 repository에 올라가지는 않는다.
            // 앱의 클래스/메서드/문자열/라이브러리 등이 그대로 노출되어 jadx/apktool 디컴파일 시 원본 그대로 확인이 가능하다. 의도된 취약점.
            // (OWASP Mobile M7 Insufficient Binary Protection). 보존: 고치지 말 것. 명세: mobile/docs/vuln-spec-mobile.md  참고) R8은 크기를 줄이기 위해 클래스/메서드/필드의 이름만 a, b 등으로 축약시키며 흔히 알고 있는 난독화작업은 하지 않는다. 파싱이 필요한 주요 부분은 이름 축약도 할 수 없다.  평가항목: (금취분평)MOB-FIN-020 [전자금융] 소스코드 난독화 적용 — 평가 예시 "실행 프로그램 또는 소스코드 난독화 미적용으로 인한 중요 로직의 해독 가능 여부"에 직접 해당 / (주통기)해당 항목 없음(전자금융 항목은 주통기 대응 열 없음). 주의) 명세 문서의 "release signingConfig 없음" 기술은 현재 코드와 불일치 — 위 :130 에서 demo keystore 로 서명된다(명세는 수정하지 않음).
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // [보안 방어] 네이티브 방어 라이브러리(libdefense.so)는 gradle 이 아니라 미리 빌드해
    // src/main/jniLibs/<abi>/ 에 넣어 둔다(AGP 가 자동 패키징).
    //
    // [왜 externalNativeBuild(cmake/ndkBuild)를 안 쓰나]
    //   이 프로젝트 경로에 한글("3. 개발\소스코드")이 있어 ndk-build(GNU make)가 파일을 못 찾고,
    //   SDK cmake.exe 는 이 머신에서 configure 중 크래시한다. 그래서 gradle 빌드 단계에서
    //   네이티브를 컴파일하지 않고, ASCII 경로에서 미리 만든 .so 만 넣는다.
    //   .so 재생성 방법은 src/main/cpp/build-defense-so.ps1 참조.
}

dependencies {
    // FCM (docs/fcm-integration.md). 서비스 클래스: background/push/KidhackFirebaseMessagingService.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.compose.navigation)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)
    implementation(libs.stomp.android)
    implementation(libs.rxjava)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
