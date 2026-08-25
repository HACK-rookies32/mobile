import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
// Prefer host LAN IP on Windows emulators; 10.0.2.2 often times out for OkHttp only.
val apiBaseUrl: String =
    localProperties.getProperty("api.base.url", "http://10.0.2.2:8080/api")

android {
    namespace = "com.hack.kidhack"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.hack.kidhack"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BASE_URL", "\"$apiBaseUrl\"")
        manifestPlaceholders["usesCleartextTraffic"] = "false"
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
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        // 배포·보안 실습용 (서명 필요). keystore 연결 후 signingConfig 지정.
        release {
            buildConfigField("boolean", "USE_MOCK", "false")
            buildConfigField(
                "String",
                "BASE_URL",
                "\"https://api.childsafe.example.com/api\"",
            )
            manifestPlaceholders["usesCleartextTraffic"] = "false"
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
}

dependencies {
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
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
