package com.hack.kidhack.feature.child.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.background.capture.MediaProjectionRequestActivity
import com.hack.kidhack.core.common.KoreanParticles
import com.hack.kidhack.background.capture.ScreenCaptureService
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.feature.child.components.ChildScreenScaffold
import com.hack.kidhack.feature.child.components.RetryCard
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChildScreenshotScreen(
    onBack: () -> Unit = {},
    viewModel: ChildScreenshotViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current

    // 동의 → 캡처 → 업로드를 마치고 돌아오면 목록이 최신이어야 한다.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    ChildScreenScaffold(
        header = {
            Text(
                text = stringResource(R.string.child_screenshot_back),
                modifier = Modifier.clickable(onClick = onBack),
                color = Color.White,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.child_screenshot_title),
                color = Color.White,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 24.sp,
            )
            Text(
                text = stringResource(R.string.child_screenshot_subtitle),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
        },
    ) {
        if (state.bannerVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.brandLight)
                    .border(1.dp, colors.brand.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("📸", fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.child_screenshot_banner_title),
                        color = colors.brandDark,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = stringResource(
                            R.string.child_screenshot_banner_body_from,
                            KoreanParticles.subject(
                                state.requestedBy?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.child_request_default_sender),
                            ),
                        ),
                        color = colors.muted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    KidButton(
                        text = stringResource(R.string.child_screenshot_allow),
                        onClick = {
                            context.startActivity(
                                MediaProjectionRequestActivity.intent(
                                    context = context,
                                    requestId = state.pendingRequestId.orEmpty(),
                                    trigger = ScreenCaptureService.TRIGGER_MANUAL,
                                ),
                            )
                            viewModel.onCaptureLaunched()
                        },
                    )
                }
                Text(
                    text = stringResource(R.string.child_screenshot_dismiss),
                    modifier = Modifier.clickable(onClick = viewModel::dismissBanner),
                    color = colors.muted,
                    fontSize = 14.sp,
                )
            }
        }

        KidCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🖼️", fontSize = 40.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.child_screenshot_guide_title),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 16.sp,
                    color = colors.text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.child_screenshot_guide_body),
                    color = colors.muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Text(
            text = stringResource(R.string.child_screenshot_recent),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 14.sp,
            color = colors.text,
        )
        val loadError = state.error
        if (loadError != null) {
            RetryCard(message = loadError, onRetry = viewModel::refresh)
        }
        if (loadError == null && state.items.isEmpty() && !state.loading) {
            KidCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("📭", fontSize = 32.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.child_screenshot_empty),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 14.sp,
                        color = colors.text,
                    )
                    Text(
                        text = stringResource(R.string.child_screenshot_empty_hint),
                        color = colors.muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        state.items.forEach { item ->
            KidCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFE5E7EB), Color(0xFFD1D5DB)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🖼️", fontSize = 20.sp)
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.description,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 14.sp,
                            color = colors.text,
                        )
                        Text(text = item.capturedAt, color = colors.muted, fontSize = 12.sp)
                    }
                    Text(
                        text = stringResource(
                            if (item.trigger.equals("MANUAL", ignoreCase = true)) {
                                R.string.child_screenshot_mom
                            } else {
                                R.string.child_screenshot_scheduled
                            },
                        ),
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
        Spacer(Modifier.height(8.dp))
    }
}
