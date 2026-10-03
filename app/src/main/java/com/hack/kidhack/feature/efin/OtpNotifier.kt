package com.hack.kidhack.feature.efin

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hack.kidhack.R
import com.hack.kidhack.background.alert.ServerAlertNotifications

/**
 * 거래 OTP 코드를 로컬 알림으로 띄운다 — 실제 SMS/OTP 문자가 도착하는 것처럼 보이게 한다.
 *
 * [상태] 2026-10-04: 거래 승인 흐름([TxAuthCoordinator])에서 이 알림 호출(notifyCode)을 제거했다.
 *   공격자가 '피해자 번호'로 거래하는 시연에서 OTP 가 공격자 자기 폰에 뜨면 소지기반 2차인증 전제가
 *   깨지기 때문 — 이제 OTP 는 발급 응답 패킷으로만 전달되고(노출 취약점 [VULN-EFIN-1] 보존), 공격자는
 *   TLS 캡처로 패킷에서 코드를 탈취해야 한다. 이 클래스는 재사용 대비 남겨 둔다(현재 호출부 없음).
 *   아래 설명은 호출이 살아 있던 시절의 취약점 기록이다.
 *
 * [VULN-MOBILE-EFIN-1] 서버는 mock 이라 실제 문자를 보내지 않고, 발급 API 응답에 코드를 실어 준다(백엔드 짝 [VULN-EFIN-1]). 앱은 그 코드를 받아 이 알림으로 표시한다 — FCM 서버 푸시와 동일한 알림 채널을 써서 화면상 구분되지 않는다. (실서버(api) 빌드에서도 응답 코드로 동일하게 동작한다.)
 * 아래 빌더는 setVisibility·setPublicVersion 을 지정하지 않아 기기 기본 설정에서 잠금화면에 코드 전문이 뜬다. 앱 전체에 FLAG_SECURE 가 없어 화면캡처로도 확보된다. 의도된 취약점(OWASP Mobile M1/M9). 보존: 고치지 말 것.
 * 평가항목: (금취분평)MOB-SER-020 화면 내 중요정보 평문노출 방지 — 항목의 중요정보 정의에 OTP 가 포함되고, 예외 조항("본인확인 및 화면캡처 방지 적용 시 예외 처리")은 FLAG_SECURE 부재로 요건 미충족(대표) · MOB-SER-051 통신구간 암호화 적용 — debug·api 변형이 평문 HTTP 를 쓸 때만 조건부 해당 / (주통기)SN 데이터 평문 전송 — MOB-SER-051 적용 구간에 한함(VULN-MOBILE-NET-1 과 동일 원인).
 */
class OtpNotifier(private val context: Context) {

    fun notifyCode(code: String) {
        ServerAlertNotifications.ensureChannel(context)
        val notification = NotificationCompat.Builder(context, ServerAlertNotifications.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("핵페이 인증번호")
            .setContentText("거래 인증번호 [$code] 를 입력해 주세요. (유효시간 3분)")
            .setStyle(NotificationCompat.BigTextStyle().bigText("거래 인증번호 [$code] 를 입력해 주세요. (유효시간 3분)"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .build()
        // 33+ 에서 권한이 없으면 조용히 무시된다(S8=API28 은 권한 불필요).
        NotificationManagerCompat.from(context).notify(OTP_NOTI_ID, notification)
    }

    companion object {
        /** 거래 OTP 알림 id — 서버알림(7300 대역)·자녀요청(7200 대역)과 겹치지 않게 7900. */
        const val OTP_NOTI_ID = 7900
    }
}