# FCM 도입 가이드 (Android)

백엔드가 **FCM 사용**을 결정함에 따라, 알림·명령 전달의 주 경로를 FCM 으로 바꾼다.
앱에는 **SDK 에 의존하지 않는 라우팅 계층**이 이미 들어가 있다. 남은 것은
Firebase SDK 글루 3가지(플러그인·의존성·`google-services.json`)와 백엔드 발송 구현이다.

---

## 1. 지금 앱에 들어가 있는 것 (SDK 불필요, 빌드·테스트 통과)

| 파일 | 역할 |
|---|---|
| `background/push/PushMessage.kt` | FCM `data` 맵 → 내부 표현 파싱. Firebase 없이 단위 테스트됨 |
| `background/push/PushMessageRouter.kt` | 푸시 1건을 기존 처리 경로로 분기(보호자 알림 / 자녀 명령) |
| `background/push/PushTokenRegistrar.kt` | 토큰을 로그인 계정에 등록·해제 |
| `data/local/PushTokenStore.kt` | 마지막 토큰 보관(로그인 전에 온 토큰 대비) |
| `data/remote/api/Apis.kt` `DeviceApi` | `POST/DELETE /devices/push-token` |
| `KidhackApplication.kt` | 세션 생성/소멸에 토큰 등록/해제 연결 |

라우터는 **기존 함수를 그대로 재사용**한다 — 보호자 알림은 `ParentAlertSync.recordRemote`,
자녀 명령은 `ChildCommandNotifier`+`ChildAlertStore`+`ChildCommandBus`. 그래서 FCM 을 켜도
화면 이동·캡처/촬영·「최근 알림」 영구 기록이 지금과 똑같이 동작하고, 폴링과 FCM 이 같은
알림을 둘 다 물어와도 **id 로 합쳐져 중복 푸시가 없다**.

---

## 2. ops 가 해야 할 것 (빌드 설정)

이 3가지가 없으면 Firebase SDK 는 컴파일되지 않으므로 앱에 아직 넣지 않았다.

### 2-1. `google-services.json`
Firebase 콘솔에서 안드로이드 앱(`com.hack.kidhack`, api 변형은 `.api` suffix 주의)을 등록하고
받은 파일을 `app/` 에 둔다. **git 에 커밋하지 말 것**(`.gitignore` 에 추가).

> applicationId 가 3개다: `com.hack.kidhack`(debug), `com.hack.kidhack.api`(api),
> `com.hack.kidhack`(release). Firebase 콘솔에 필요한 것만 등록하면 된다. 목업(debug)은
> 서버가 없으니 api 변형만 등록해도 두 기기 테스트가 가능하다.

### 2-2. Gradle 플러그인 (조건부 적용 권장)
`google-services.json` 이 있을 때만 플러그인을 켜면, 파일이 없는 개발자도 빌드가 깨지지 않는다.

`gradle/libs.versions.toml`:
```toml
[versions]
firebaseBom = "34.1.0"          # 최신 안정 BOM 확인
googleServices = "4.4.2"

[libraries]
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-messaging = { group = "com.google.firebase", name = "firebase-messaging" }

[plugins]
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

최상위 `build.gradle.kts` 의 `plugins { }` 에 `alias(libs.plugins.google.services) apply false`.

`app/build.gradle.kts` 상단:
```kotlin
// google-services.json 이 있을 때만 플러그인을 적용한다. 없으면 조용히 건너뛴다.
if (rootProject.file("app/google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
```
`dependencies { }`:
```kotlin
implementation(platform(libs.firebase.bom))
implementation(libs.firebase.messaging)
```

### 2-3. `FirebaseMessagingService` 추가
아래 파일을 `app/src/main/java/com/hack/kidhack/background/push/KidhackFirebaseMessagingService.kt`
로 만든다. **라우터·레지스트라를 호출만 한다** — 로직은 이미 앱에 있다.

```kotlin
package com.hack.kidhack.background.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

class KidhackFirebaseMessagingService : FirebaseMessagingService() {
    private val router: PushMessageRouter by inject()
    private val registrar: PushTokenRegistrar by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data.isEmpty()) return
        val parsed = PushMessage.fromData(message.data)
        // 백그라운드 브로드캐스트는 프로세스가 곧 죽을 수 있어 완료를 기다린다.
        runBlocking { router.route(parsed) }
    }

    override fun onNewToken(token: String) {
        scope.launch { registrar.onTokenRefreshed(token) }
    }
}
```

`AndroidManifest.xml` 의 `<application>` 안:
```xml
<service
    android:name=".background.push.KidhackFirebaseMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>

<!-- 자녀 명령·안전 알림 채널을 기본 채널로 -->
<meta-data
    android:name="com.google.firebase.messaging.default_notification_channel_id"
    android:value="kidhack_child_request" />
```

앱은 이미 `POST_NOTIFICATIONS` 권한을 요청하므로 추가 권한은 없다.

---

## 3. 서버가 보내야 할 메시지 형식 (**data 전용**)

`notification` 필드를 쓰면 앱이 백그라운드일 때 시스템이 마음대로 트레이에 띄워
자녀 명령처럼 **우리가 통제해야 하는 푸시**를 가로챈다. 반드시 **`data` 전용**으로 보낸다.

### 보호자 알림 (자녀 사건 → 부모 기기)
```json
{
  "message": {
    "token": "<부모기기 FCM 토큰>",
    "data": {
      "kind": "PARENT_ALERT",
      "type": "HARMFUL",              // HARMFUL | LOCATION | ARRIVAL | GPS_OFF | APP_USAGE | NOTICE
      "title": "유해 사이트 접속 차단",
      "body": "민준 · 도박 사이트 차단",
      "childId": "child-minjun",
      "id": "alert-42"                // 합침 키. 폴링과 겹쳐도 이 id 로 중복 제거
    },
    "android": { "priority": "high" }
  }
}
```

### 자녀 명령 (부모 요청 → 자녀 기기)
```json
{
  "message": {
    "token": "<자녀기기 FCM 토큰>",
    "data": {
      "kind": "CHILD_COMMAND",
      "type": "SCREEN_CAPTURE",       // SCREEN_CAPTURE | CAMERA_SHOT (동의어는 Mappers.kt 참고)
      "requestId": "req-7",
      "childId": "child-minjun",
      "requestedBy": "엄마"
    },
    "android": { "priority": "high" }
  }
}
```

키 이름을 바꾸려면 `PushMessage.Companion` 의 상수와 서버만 맞추면 된다.

### 토큰 등록 엔드포인트
```
POST   /devices/push-token   body: { "token": "...", "platform": "ANDROID" }
DELETE /devices/push-token   body: { "token": "..." }
```
서버는 `token → userId` 매핑을 저장하고, 알림을 보낼 때 대상 계정의 토큰을 찾는다.
한 계정이 여러 기기일 수 있으니 **토큰을 리스트로** 들고 있는 게 안전하다.

---

## 4. 기존 경로는 어떻게 되나 — **폴링/소켓은 백업으로 유지**

FCM 은 대부분 몇 초 내 도착하지만 **보장되지 않는다**(도즈, 앱 강제 종료, 토큰 회전 순간).
그래서 기존 경로를 지우지 말고 백업으로 둔다. 셋 다 같은 저장소·같은 dedup 을 거치므로 겹쳐도 안전하다.

| 경로 | 역할 | 변화 |
|---|---|---|
| FCM | **주** 경로 | 신규 |
| `ParentAlertPollWorker` (15분) | 보호자 알림 백업 | 유지. 원하면 30분+로 완화 가능 |
| `ChildCommandService`(WebSocket) + `ChildCommandPollWorker` | 자녀 명령 백업 | 유지. FCM 검증 후 상주 소켓은 은퇴 검토(안드로이드 15 dataSync 6시간 제한 회피) |
| `ChildAlertUploadWorker` | 자녀 사건 업로드 | 유지 — FCM 은 **수신**만, 업로드는 여전히 필요 |

> **소켓 은퇴는 별도 결정.** FCM 이 자녀 명령을 안정적으로 나르는 게 검증되면
> `ChildCommandService`(상주 포그라운드 소켓)를 없애 배터리·정책 부담을 던다.
> 지금은 둘 다 켜 두어도 `ChildCommandBus`/`ChildAlertStore` 가 requestId 로 합쳐 중복이 없다.

---

## 5. 두 기기 테스트 체크리스트

1. api 변형에 `google-services.json` 넣고 `./gradlew installApi`
2. 백엔드: `/devices/push-token` 등록 + 위 형식으로 FCM 발송 구현
3. 기기1 부모 / 기기2 자녀 로그인 → 각 기기가 토큰을 등록하는지 서버 로그 확인
4. 자녀 기기에서 유해 사이트 접속 → 부모 기기에 **즉시** 푸시 + 대시보드 최근 알림
5. 부모가 화면 캡처/카메라 요청 → 자녀 기기에 **즉시** 요청 푸시
6. 비행기 모드로 FCM 을 막고 → 폴링(15분)이 백업으로 도는지 확인
