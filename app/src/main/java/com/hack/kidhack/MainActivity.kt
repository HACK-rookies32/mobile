package com.hack.kidhack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.hack.kidhack.core.designsystem.theme.KidhackTheme
import com.hack.kidhack.core.permission.PermissionRequester
import com.hack.kidhack.navigation.KidhackNavHost

val LocalPermissionRequester = staticCompositionLocalOf<PermissionRequester> {
    error("PermissionRequester not provided")
}

class MainActivity : ComponentActivity() {
    /** Registered before STARTED so system permission dialogs can be shown reliably. */
    val permissionRequester = PermissionRequester(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalPermissionRequester provides permissionRequester) {
                KidhackTheme {
                    KidhackNavHost()
                }
            }
        }
    }
}

@Composable
fun permissionRequester(): PermissionRequester = LocalPermissionRequester.current
