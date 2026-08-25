package com.hack.kidhack.feature.child.onboarding

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.core.permission.KidhackPermissions
import com.hack.kidhack.domain.repository.ChildOnboardingStore
import com.hack.kidhack.feature.child.components.ChildScreenScaffold
import com.hack.kidhack.navigation.Route
import org.koin.androidx.compose.koinViewModel
import org.koin.core.context.GlobalContext

@Composable
fun ChildStartRoute(navController: NavHostController) {
    val colors = LocalKidhackColors.current
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
        val dest = if (store.isDone()) Route.ChildHome.path else Route.ChildOnboarding.path
        navController.navigate(dest) {
            popUpTo(Route.ChildStart.path) { inclusive = true }
        }
    }
}

@Composable
fun ChildOnboardingScreen(
    onFinished: () -> Unit,
    viewModel: ChildOnboardingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = LocalKidhackColors.current

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.next() }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.next() }

    val stepEmoji = when (state.step) {
        0 -> stringResource(R.string.child_onboarding_location_emoji)
        1 -> stringResource(R.string.child_onboarding_notif_emoji)
        else -> stringResource(R.string.child_onboarding_later_emoji)
    }
    val stepTitle = when (state.step) {
        0 -> stringResource(R.string.child_onboarding_location_title)
        1 -> stringResource(R.string.child_onboarding_notif_title)
        else -> stringResource(R.string.child_onboarding_later_title)
    }
    val stepBody = when (state.step) {
        0 -> stringResource(R.string.child_onboarding_location_body)
        1 -> stringResource(R.string.child_onboarding_notif_body)
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

        if (state.step == 2) {
            listOf(
                R.string.child_onboarding_later_usage,
                R.string.child_onboarding_later_accessibility,
                R.string.child_onboarding_later_overlay,
            ).forEach { res ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.card)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(res),
                        modifier = Modifier.weight(1f),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 14.sp,
                        color = colors.text,
                    )
                    Text(
                        text = stringResource(R.string.child_onboarding_later_badge),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.brandLight)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = colors.brand,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        KidButton(
            text = when (state.step) {
                2 -> stringResource(R.string.child_onboarding_done)
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
                    else -> viewModel.finish()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.step < 2) {
            KidButton(
                text = stringResource(R.string.child_onboarding_skip),
                onClick = viewModel::next,
                modifier = Modifier.fillMaxWidth(),
                variant = KidButtonVariant.Ghost,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
