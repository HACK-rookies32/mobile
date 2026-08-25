package com.hack.kidhack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hack.kidhack.background.command.ChildCommandBus
import com.hack.kidhack.background.command.ChildCommandNotifier
import com.hack.kidhack.core.designsystem.theme.KidhackTheme
import com.hack.kidhack.data.repository.toRemoteCommandType
import com.hack.kidhack.domain.model.RemoteCommand
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.navigation.KidhackNavHost
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val commandBus: ChildCommandBus by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        routeRemoteCommand(intent)
        setContent {
            KidhackTheme {
                KidhackNavHost()
            }
        }
    }

    /** launchMode=singleTask 라 앱이 떠 있는 상태의 알림 탭은 여기로 들어온다. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeRemoteCommand(intent)
    }

    /**
     * 부모 요청 알림에서 넘어온 인텐트를 [ChildCommandBus] 로 흘려 보낸다.
     * 실제 화면 이동은 `KidhackNavHost` 가 담당한다.
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
