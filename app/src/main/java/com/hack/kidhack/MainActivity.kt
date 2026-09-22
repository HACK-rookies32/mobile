package com.hack.kidhack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hack.kidhack.background.alert.ServerAlertBus
import com.hack.kidhack.background.alert.ServerAlertNotifier
import com.hack.kidhack.background.command.ChildCommandBus
import com.hack.kidhack.background.command.ChildCommandNotifier
import com.hack.kidhack.core.designsystem.theme.KidhackTheme
import com.hack.kidhack.data.repository.toRemoteCommandType
import com.hack.kidhack.domain.model.RemoteCommand
import com.hack.kidhack.domain.model.RemoteCommandType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.hack.kidhack.core.designsystem.theme.KidhackTheme
import com.hack.kidhack.core.permission.PermissionRequester
import com.hack.kidhack.navigation.KidhackNavHost
import com.hack.kidhack.security.StartupDefenseGate
import org.koin.android.ext.android.inject

val LocalPermissionRequester = staticCompositionLocalOf<PermissionRequester> {
    error("PermissionRequester not provided")
}

class MainActivity : ComponentActivity() {

    private val commandBus: ChildCommandBus by inject()
    private val serverAlertBus: ServerAlertBus by inject()
    /** Registered before STARTED so system permission dialogs can be shown reliably. */
    val permissionRequester = PermissionRequester(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        routeRemoteCommand(intent)
        routeParentAlert(intent)
        setContent {
            CompositionLocalProvider(LocalPermissionRequester provides permissionRequester) {
                KidhackTheme {
                    // [보안 방어] 앱 시작 입구 게이트. onCreate 가 담아 둔 시작 검사 결과에
                    // 위협이 있으면 경고 다이얼로그 후 강제 종료하고, 없으면 앱 화면을 띄운다.
                    StartupDefenseGate {
                        KidhackNavHost()
                    }
                }
            }
        }
    }

    /** launchMode=singleTask 라 앱이 떠 있는 상태의 알림 탭은 여기로 들어온다. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeRemoteCommand(intent)
        routeParentAlert(intent)
    }

    /** 부모 기기의 안전 알림 푸시 탭 → 알림 탭. 실제 이동은 `KidhackNavHost` 가 한다. */
    private fun routeParentAlert(intent: Intent?) {
        if (intent?.action != ServerAlertNotifier.ACTION_PARENT_ALERT) return
        serverAlertBus.post(intent.getStringExtra(ServerAlertNotifier.EXTRA_ALERT_ID).orEmpty())
        // 회전·복귀 때 같은 인텐트로 다시 이동하지 않도록 액션을 지운다.
        intent.action = null
    }

    /**
     * 부모 요청 알림에서 넘어온 인텐트를 [ChildCommandBus] 로 흘려 보낸다. 실제 화면 이동은 `KidhackNavHost` 가 담당한다.
     * [VULN-MOBILE-COMP-1] 이 액티비티는 LAUNCHER 라 android:exported="true" 가 필수다 — 결함은 exported 자체가 아니라, 외부에서 들어온 ACTION_REMOTE_COMMAND 인텐트를 발신자 검증(서명 수준 permission·호출 패키지 확인) 없이 그대로 commandBus 에 흘린다는 점이다. 타 앱이 `am start -a com.hack.kidhack.action.REMOTE_COMMAND --es type CAMERA_SHOT --es requestId X` 로 자녀를 카메라 요청 화면으로 강제 이동시킬 수 있다(KidhackNavHost 의 CAMERA_SHOT 분기). 의도된 취약점(OWASP Mobile M8). 보존: 고치지 말 것. 평가항목: (금취분평)MOB-SER-026 화면 강제실행에 의한 인증단계 우회 방지 — 평가 예시 "인텐트(Intent), Cycript 등 화면 강제 실행이 가능한 기능 및 도구를 이용하여 화면강제 실행 시 서비스 정상 사용가능 여부 점검"에 해당(항목명의 "인증단계 우회"까지는 성립하지 않으므로 근거는 화면 강제실행 축으로 한정) / (주통기)해당 항목 없음. 참고: CAMERA_SHOT 은 대응 백엔드 엔드포인트·부모 UI 진입점이 없어 사실상 미사용 기능이나 클라이언트 내비게이션 경로는 살아 있다.
     */
    private fun routeRemoteCommand(intent: Intent?) {
        if (intent?.action != ChildCommandNotifier.ACTION_REMOTE_COMMAND) return
        val requestId = intent.getStringExtra(ChildCommandNotifier.EXTRA_REQUEST_ID) ?: return
        val type = intent.getStringExtra(ChildCommandNotifier.EXTRA_TYPE).toRemoteCommandType()
        if (type == RemoteCommandType.UNKNOWN) return
        commandBus.post(
            RemoteCommand(
                requestId = requestId,
                type = type,
                childId = intent.getStringExtra(ChildCommandNotifier.EXTRA_CHILD_ID).orEmpty(),
                requestedBy = intent.getStringExtra(ChildCommandNotifier.EXTRA_REQUESTED_BY),
            ),
        )
        // 같은 인텐트로 두 번 라우팅되지 않도록 액션을 지운다(화면 회전·복귀 대비).
        intent.action = null
    }
}

@Composable
fun permissionRequester(): PermissionRequester = LocalPermissionRequester.current
