package com.hack.kidhack.feature.child.onboarding

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.core.permission.KidhackPermissions
import com.hack.kidhack.domain.repository.ChildOnboardingStore
import com.hack.kidhack.navigation.Route
import org.koin.core.context.GlobalContext

@Composable
fun ChildStartRoute(navController: NavHostController) {
    val colors = LocalKidhackColors.current
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.brand),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Color.White)
    }
    LaunchedEffect(Unit) {
        val store = GlobalContext.get().get<ChildOnboardingStore>()
        val ready = store.isDone() && KidhackPermissions.hasAllRequiredForChildControl(context)
        val dest = if (ready) Route.ChildHome.path else Route.ChildOnboarding.path
        navController.navigate(dest) {
            popUpTo(Route.ChildStart.path) { inclusive = true }
        }
    }
}

@Composable
fun ChildOnboardingScreen(
    onFinished: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = LocalKidhackColors.current

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    // 설정 앱에서 접근성을 켜고 돌아오면 즉시 반영돼야 한다.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }

    val openSettings: (String) -> Unit = { action ->
        runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.next() }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.next() }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.next() }

    val lastStep = state.totalSteps - 1
    val stepEmoji = when (state.step) {
        0 -> stringResource(R.string.child_onboarding_location_emoji)
        1 -> stringResource(R.string.child_onboarding_notif_emoji)
        2 -> stringResource(R.string.child_onboarding_camera_emoji)
        else -> stringResource(R.string.child_onboarding_later_emoji)
    }
    val stepTitle = when (state.step) {
        0 -> stringResource(R.string.child_onboarding_location_title)
        1 -> stringResource(R.string.child_onboarding_notif_title)
        2 -> stringResource(R.string.child_onboarding_camera_title)
        else -> stringResource(R.string.child_onboarding_later_title)
    }
    val stepBody = when (state.step) {
        0 -> stringResource(R.string.child_onboarding_location_body)
        1 -> stringResource(R.string.child_onboarding_notif_body)
        2 -> stringResource(R.string.child_onboarding_camera_body)
        else -> stringResource(R.string.child_onboarding_later_body)
    }

    ChildScreenScaffold(
        header = {
            Text(
                text = stringResource(R.string.child_onboarding_title),
                color = Color.White,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 24.sp,
            )
            Text(
                text = stringResource(R.string.child_onboarding_subtitle),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(state.totalSteps) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                if (index <= state.step) Color.White else Color.White.copy(alpha = 0.3f),
                            ),
                    )
                }
            }
        },
    ) {
        Text(
            text = stringResource(R.string.child_onboarding_step, state.step + 1, state.totalSteps),
            color = colors.brand,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 12.sp,
        )
        KidCard {
            Text(stepEmoji, fontSize = 36.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stepTitle,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 20.sp,
                color = colors.text,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stepBody,
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 14.sp,
            )
        }

        if (state.step == lastStep) {
            // 접근성·사용정보 접근·오버레이는 런타임 권한이 아니라 설정 앱에서만 켤 수 있다.
            // 앱이 코드로 켜는 건 OS 가 막아 두었으므로(WRITE_SECURE_SETTINGS 는 시스템 전용),
            // 여기서 바로 설정으로 보내 주고 결과를 확인해 준다.
            listOf(
                Triple(
                    R.string.child_onboarding_later_accessibility,
                    state.harmfulBlockEnabled,
                    Settings.ACTION_ACCESSIBILITY_SETTINGS,
                ),
                Triple(
                    R.string.child_onboarding_later_usage,
                    state.usageAccessGranted,
                    Settings.ACTION_USAGE_ACCESS_SETTINGS,
                ),
                Triple(
                    R.string.child_onboarding_later_overlay,
                    state.overlayGranted,
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                ),
            ).forEach { (labelRes, granted, action) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.card)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(labelRes),
                        modifier = Modifier.weight(1f),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 14.sp,
                        color = colors.text,
                    )
                    if (granted) {
                        Text(
                            text = stringResource(R.string.child_onboarding_granted),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF0FDF4))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = colors.safe,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 12.sp,
                        )
                    } else {
                        KidButton(
                            text = stringResource(R.string.child_onboarding_open_settings),
                            onClick = { openSettings(action) },
                            variant = KidButtonVariant.Secondary,
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.child_onboarding_special_note),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 12.sp,
            )
        }

        KidButton(
            text = when (state.step) {
                lastStep -> stringResource(R.string.child_onboarding_done)
                else -> stringResource(R.string.child_onboarding_allow)
            },
            onClick = {
                when (state.step) {
                    0 -> {
                        val granted = KidhackPermissions.location.all {
                            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                        }
                        if (granted) viewModel.next() else locationLauncher.launch(KidhackPermissions.location)
                    }
                    1 -> {
                        val permission = KidhackPermissions.notifications
                        if (permission == null) {
                            viewModel.next()
                        } else if (
                            ContextCompat.checkSelfPermission(context, permission) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.next()
                        } else {
                            notificationLauncher.launch(permission)
                        }
                    }
                    2 -> {
                        if (
                            ContextCompat.checkSelfPermission(context, KidhackPermissions.camera) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.next()
                        } else {
                            cameraLauncher.launch(KidhackPermissions.camera)
                        }
                    }
                    else -> viewModel.finish()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.step < lastStep) {
            KidButton(
                text = stringResource(R.string.child_onboarding_skip),
                onClick = viewModel::next,
                modifier = Modifier.fillMaxWidth(),
                variant = KidButtonVariant.Ghost,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
    ChildPermissionWizard(
        onFinished = onFinished,
        persistDone = true,
    )
}
