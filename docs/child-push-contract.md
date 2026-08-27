# 자녀 앱 푸시 계약 (Firebase 미사용)

GAP-11 「캡처 요청 202 푸시 페이로드 미정의」에 대한 **Android 측 제안 계약**이다.
FCM 을 쓰지 않고 **상주 WebSocket(실시간) + REST 폴링(백업)** 으로 부모 → 자녀 요청을 전달한다.

백엔드가 키 이름을 바꾸고 싶다면 아래 4개 파일만 고치면 된다.

| 계약 | 파일 |
|---|---|
| WebSocket 프레임 | `data/ws/ChildCommandFrame.kt` |
| REST DTO | `data/remote/dto/Dtos.kt` (`RemoteCommandDto`, `RemoteCommandAckRequest`) |
| REST 경로 | `data/remote/api/Apis.kt` (`CommandApi`, `PhotoApi`) |
| 타입 문자열 매핑 | `data/repository/Mappers.kt` (`String.toRemoteCommandType`) |

---

## 1. WebSocket `wss://{origin}/ws/commands`

인증은 채팅 소켓과 동일하게 `Authorization: Bearer {accessToken}` 헤더 + `?token=` 쿼리 둘 다 붙인다.
클라이언트는 30초 ping, 끊기면 최대 30초까지 지수 백오프로 재연결한다.

### 서버 → 자녀

```json
{ "type": "CAPTURE_REQUEST", "requestId": "req-1", "childId": "child-1", "requestedBy": "엄마", "requestedAt": "2026-08-25T10:00:00Z" }
{ "type": "CAMERA_REQUEST",  "requestId": "req-2", "childId": "child-1", "requestedBy": "엄마" }
{ "type": "PING" }
```

`type` 은 아래 값을 모두 허용한다(대소문자 무시).

- 화면 캡처: `SCREEN_CAPTURE` | `CAPTURE` | `CAPTURE_REQUEST` | `SCREENSHOT`
- 카메라 촬영: `CAMERA_SHOT` | `CAMERA` | `CAMERA_REQUEST` | `PHOTO`

### 자녀 → 서버

```json
{ "type": "HELLO", "childId": "child-1" }
{ "type": "PONG" }
{ "type": "ACK", "requestId": "req-1", "status": "DONE" }
```

`status`: `RECEIVED` | `DONE` | `DENIED` | `FAILED`

---

## 2. REST (백업 경로 · 신규)

소켓이 끊겨 있던 동안 쌓인 요청을 15분마다 긁어온다.
**아직 서버에 없어도 앱은 정상 동작한다** — 실패 시 빈 목록으로 처리하고 소켓 경로만 쓴다.

```
GET  /children/{childId}/commands/pending
     → { "success": true, "data": [ { "requestId", "type", "childId", "requestedBy", "requestedAt" } ] }

POST /children/{childId}/commands/{requestId}/ack
     body: { "status": "DONE", "message": null }
```

## 3. 루키즈페이 용돈 지갑 (신규 · GAP-05)

명세의 `points` 경로는 **구독 적립 포인트**라 「부모 → 자녀 송금」 필드가 없다.
자녀 QR 결제는 송금자·수신자가 분명해야 하므로 별도 지갑 경로를 제안한다.

```
GET  /children/{childId}/allowance             -> { "balance": 12000, "updatedAt": "..." }
GET  /children/{childId}/allowance/history     -> [ { "id", "type", "amount", "description", "dateLabel" } ]
POST /children/{childId}/allowance/send        { "amount": 10000, "memo": "이번 주 용돈" }   # 부모
POST /children/{childId}/allowance/withdraw    { "amount": 3000,  "memo": null }             # 부모
POST /children/{childId}/allowance/payments    { "token", "amount", "merchant" }             # 자녀 QR
```

`type`: `SEND` | `WITHDRAW` | `PAYMENT` (부호 있는 `amount` — 받으면 +, 회수·결제면 −)

**잔액 검증의 최종 권한은 서버**다. 자녀가 QR 을 띄워 둔 사이 부모가 회수할 수 있으므로,
`payments` 는 잔액을 다시 확인하고 부족하면 거절해야 한다. 클라이언트도 결제 확정 직전에
`GET .../allowance` 를 한 번 더 호출하지만 그건 UX 용 방어일 뿐이다.

QR 페이로드 형식(가맹점 단말이 파싱):

```
KIDHACKPAY1|{childId}|{amount}|{token}|{expiresAtMillis}
```

`token` 은 1회용 난수이고 발급 후 180초 뒤 만료된다.

---

## 4. 업로드

- 화면 캡처: **기존 명세 그대로** `POST /children/{childId}/screenshots`
  (multipart: `file`, `capturedAt`, `trigger=SCHEDULED|MANUAL`)
- 카메라 촬영: **신규** `POST /children/{childId}/photos`
  (multipart: `file`, `capturedAt`, `requestId` — `requestId` 는 자녀가 스스로 찍은 경우 생략)

스크린샷 엔드포인트를 재사용하고 싶다면 `PhotoApi` 를 지우고
`ChildCameraViewModel` 이 `ScreenshotRepository.upload(..., trigger = "MANUAL")` 을 부르게 하면 된다.

---

## 5. 안드로이드 제약 (기획·백엔드 공유 필요)

1. **MediaProjection 토큰은 저장할 수 없다.** 안드로이드는 화면 녹화를 매번 사용자 동의로만 허용한다.
   따라서 「부모 요청 → 자동 캡처」는 불가능하고, 반드시 **자녀가 알림을 눌러 동의**해야 한다.
   REQ-M-12 의 1시간 주기 캡처도 같은 이유로 「1시간마다 동의 유도 알림」으로 구현돼 있다.
2. **카메라도 동일**하다. 백그라운드에서 몰래 찍을 수 없으며 자녀가 셔터를 눌러야 전송된다.
3. `ChildCommandService` 는 `dataSync` 포그라운드 타입을 쓴다.
   안드로이드 15 부터 `dataSync` 는 하루 6시간 제한이 있으므로, 스토어 배포 시
   `specialUse` 선언 + 심사가 필요하다. (`AndroidManifest.xml` 주석 참고)
4. 알림 권한(`POST_NOTIFICATIONS`)이 없으면 요청이 도착해도 자녀에게 보여 줄 방법이 없다.
   온보딩 2단계에서 반드시 받도록 되어 있다.

---

## 6. 서버 없이 테스트하기

디버그 빌드(`USE_MOCK=true`)에서 자녀 계정으로 로그인 후
**서비스 현황 탭 → 「요청 테스트 (개발용)」** 카드의 버튼을 누르면
실제 알림이 뜨고 캡처/촬영 전체 흐름이 그대로 돈다.
