package com.hack.kidhack.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import kotlin.system.exitProcess

/**
 * 앱 시작 입구 게이트.
 *
 * [com.hack.kidhack.KidhackApplication.onCreate] 가 [DefenseState.startupResult] 에 담아 둔
 * 시작 검사 결과를 읽어, 위협이 있으면 경고 후 종료하고 없으면 [content] 를 보여준다.
 * MainActivity 의 setContent 루트에서 앱 전체(KidhackNavHost)를 감싸는 용도.
 */
@Composable
fun StartupDefenseGate(content: @Composable () -> Unit) {
    val result = DefenseState.startupResult
    if (result != null && result.hasThreat) {
        DefenseBlockedDialog(result)
    } else {
        content()
    }
}

/**
 * 민감 화면(지갑/결제/단말) 입구 게이트.
 *
 * 진입할 때 방어 검사를 **새로** 돌린다 — 앱 시작 이후 런타임에 붙은 Frida/디버거까지
 * 잡기 위해서다. [remember] 로 이 화면이 살아 있는 동안 검사는 한 번만 수행한다.
 * 위협이 있으면 경고 후 종료, 없으면 [content] 를 보여준다.
 */
@Composable
fun DefenseGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // remember: 리컴포지션마다 다시 검사하지 않도록 최초 1회만 실행.
    val result = remember { DefenseChecker.runAllChecks(context) }
    if (result.hasThreat) {
        DefenseBlockedDialog(result)
    } else {
        content()
    }
}

/**
 * 위협 감지 시 띄우는 경고 다이얼로그. 확인/바깥터치/뒤로가기 어느 쪽이든 앱을 종료한다.
 *
 * @param result 표시할 위협 목록.
 */
@Composable
private fun DefenseBlockedDialog(result: DefenseResult) {
    val context = LocalContext.current
    val terminate = { terminateApp(context) }

    AlertDialog(
        // 바깥 터치·뒤로가기로 다이얼로그만 닫고 앱을 계속 쓰는 우회를 막는다 → 모두 종료로 연결.
        onDismissRequest = terminate,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = { Text("보안 위협 감지") },
        text = {
            Text(
                "안전하지 않은 환경이 감지되어 앱을 종료합니다.\n\n" +
                    result.description +
                    "\n\n정상 기기에서 다시 실행해 주세요.",
            )
        },
        confirmButton = {
            TextButton(onClick = terminate) { Text("종료") }
        },
    )
}

/**
 * 앱을 강제 종료한다.
 *  - finishAffinity(): 현재 태스크의 모든 액티비티를 닫는다.
 *  - exitProcess(0):   프로세스 자체를 끝낸다 → onCreate 에서 띄운 백그라운드 작업도 정리.
 */
private fun terminateApp(context: Context) {
    findActivity(context)?.finishAffinity()
    exitProcess(0)
}

/** LocalContext 는 ContextWrapper 로 감싸여 있을 수 있어, 실제 Activity 를 찾아 올라간다. */
private fun findActivity(context: Context): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}