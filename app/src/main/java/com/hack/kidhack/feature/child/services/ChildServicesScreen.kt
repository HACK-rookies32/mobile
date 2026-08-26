package com.hack.kidhack.feature.child.services

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.component.UsageBar
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.RemoteCommandType
import com.hack.kidhack.feature.child.components.ChildScreenScaffold
import com.hack.kidhack.feature.child.components.RetryCard
import com.hack.kidhack.feature.child.formatDurationSec
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChildServicesScreen(
    viewModel: ChildServicesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current

    // 접근성·사용정보 접근은 일반 권한 다이얼로그로 못 받고 설정 화면으로 보내야 한다.
    val openSettings: (String) -> Unit = { action ->
        runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // 권한·GPS 는 설정에서 바뀔 수 있으므로 탭 복귀 때마다 실제 상태를 다시 읽는다.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    ChildScreenScaffold(
        header = {
            Text(
                text = stringResource(R.string.child_services_title),
                color = Color.White,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 24.sp,
            )
            Text(
                text = stringResource(R.string.child_services_subtitle),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
        },
    ) {
        val loadError = state.error
        if (loadError != null) {
            RetryCard(message = loadError, onRetry = viewModel::refresh)
        }

        KidCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔔", fontSize = 20.sp)
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.child_services_push_title),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 14.sp,
                        color = colors.text,
                    )
                    Text(
                        text = stringResource(
                            if (state.pushConnected) {
                                R.string.child_services_push_on_desc
                            } else {
                                R.string.child_services_push_off_desc
                            },
                        ),
                        color = colors.muted,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = stringResource(
                        if (state.pushConnected) {
                            R.string.child_services_push_on
                        } else {
                            R.string.child_services_push_off
                        },
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (state.pushConnected) Color(0xFFF0FDF4) else colors.surface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = if (state.pushConnected) colors.safe else colors.warn,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.sp,
                )
            }
        }

        KidCard {
            ServiceHeader(
                emoji = "📍",
                title = stringResource(R.string.child_services_location),
                subtitle = stringResource(R.string.child_services_location_desc),
                active = state.locationGranted && state.gpsEnabled,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFEFF6FF), Color(0xFFF0FDF4), Color(0xFFDBEAFE)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🗺️", fontSize = 28.sp)
                    Text(
                        text = state.locationLabel,
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = stringResource(R.string.child_services_current),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(colors.brand)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    color = Color.White,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = colors.border)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (state.locationUpdatedAt.isBlank()) {
                        stringResource(R.string.child_services_updated_unknown)
                    } else {
                        stringResource(R.string.child_services_updated_at, state.locationUpdatedAt)
                    },
                    modifier = Modifier.weight(1f),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                )
                val gpsOn = state.locationGranted && state.gpsEnabled
                Text(
                    text = stringResource(
                        when {
                            gpsOn -> R.string.child_services_gps_on
                            !state.locationGranted -> R.string.child_services_location_denied
                            else -> R.string.child_services_gps_off
                        },
                    ),
                    color = if (gpsOn) colors.safe else colors.warn,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.sp,
                )
            }
        }

        KidCard {
            // 취침 모드·스크린타임 제한은 기기에서 강제되지 않는다(GAP-12).
            // 「실행 중」이라고 표시하면 거짓말이 되므로 배지를 달지 않는다.
            ServiceHeader(
                emoji = "⏰",
                title = stringResource(R.string.child_services_limits),
                subtitle = stringResource(R.string.child_services_limits_desc),
            )
            LimitRow("⏱️", stringResource(R.string.child_services_screentime_limit), stringResource(R.string.child_services_screentime_value))
            LimitRow("🌙", stringResource(R.string.child_services_bedtime), stringResource(R.string.child_services_bedtime_value))
            LimitRow("✅", stringResource(R.string.child_services_status), stringResource(R.string.child_services_available), showDivider = false)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.child_services_bedtime_note),
                color = colors.muted,
                fontSize = 12.sp,
            )
        }

        KidCard {
            ServiceHeader(
                emoji = "🚫",
                title = stringResource(R.string.child_services_harmful),
                subtitle = stringResource(R.string.child_services_harmful_desc),
                active = state.harmfulBlockEnabled,
            )
            Spacer(Modifier.height(8.dp))
            val cats = listOf(
                R.string.child_services_cat_adult,
                R.string.child_services_cat_gambling,
                R.string.child_services_cat_violence,
                R.string.child_services_cat_drug,
            )
            cats.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { res ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (state.harmfulBlockEnabled) Color(0xFFF0FDF4) else colors.surface,
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (state.harmfulBlockEnabled) "✅" else "⚪", fontSize = 14.sp)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = stringResource(res),
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontSemibold,
                                fontSize = 12.sp,
                                color = colors.text,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = colors.border)
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (state.harmfulBlockEnabled) {
                    stringResource(R.string.child_services_blocked_today, state.blockedToday)
                } else {
                    stringResource(R.string.child_services_harmful_off)
                },
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 12.sp,
            )
            if (!state.harmfulBlockEnabled) {
                Spacer(Modifier.height(12.dp))
                KidButton(
                    text = stringResource(R.string.child_services_harmful_enable),
                    onClick = { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = KidButtonVariant.Secondary,
                )
            }
        }

        KidCard {
            ServiceHeader(
                emoji = "📊",
                title = stringResource(R.string.child_services_usage),
                subtitle = stringResource(R.string.child_services_usage_desc),
                active = state.usageAccessGranted,
            )
            Spacer(Modifier.height(8.dp))
            if (!state.usageAccessGranted) {
                Text(
                    text = stringResource(R.string.child_services_usage_no_access),
                    color = colors.warn,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                KidButton(
                    text = stringResource(R.string.child_services_usage_enable),
                    onClick = { openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = KidButtonVariant.Secondary,
                )
                Spacer(Modifier.height(12.dp))
            }
            if (state.usages.isEmpty()) {
                Text(
                    text = stringResource(
                        if (state.loading) R.string.child_services_loading
                        else R.string.child_services_usage_empty,
                    ),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
            }
            val max = state.usages.maxOfOrNull { it.durationSec }?.coerceAtLeast(1) ?: 1
            state.usages.forEach { usage ->
                UsageBar(
                    name = usage.appName,
                    timeLabel = formatDurationSec(usage.durationSec),
                    progress = usage.durationSec / max.toFloat(),
                    barColor = usageBarColor(usage.appName, colors.info),
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            HorizontalDivider(color = colors.border)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.child_services_total, state.totalLabel),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 12.sp,
            )
            if (state.usages.isNotEmpty() && !state.usageFromDevice) {
                Text(
                    text = stringResource(R.string.child_services_usage_sample),
                    color = colors.warn,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                )
            }
        }

        if (state.simulationAvailable) {
            KidCard {
                ServiceHeader(
                    emoji = "🧪",
                    title = stringResource(R.string.child_services_sim_title),
                    subtitle = stringResource(R.string.child_services_sim_desc),
                )
                Spacer(Modifier.height(12.dp))
                KidButton(
                    text = stringResource(R.string.child_services_sim_capture),
                    onClick = { viewModel.simulateRequest(RemoteCommandType.SCREEN_CAPTURE) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = KidButtonVariant.Secondary,
                )
                Spacer(Modifier.height(8.dp))
                KidButton(
                    text = stringResource(R.string.child_services_sim_camera),
                    onClick = { viewModel.simulateRequest(RemoteCommandType.CAMERA_SHOT) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = KidButtonVariant.Secondary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ServiceHeader(
    emoji: String,
    title: String,
    subtitle: String,
    /**
     * 실제 동작 여부. null 이면 배지를 그리지 않는다.
     *
     * 예전에는 이 배지가 하드코딩이라 접근성이 꺼져 있어도 「실행 중」으로 보였고,
     * 같은 카드 안에서 「차단 기능 켜기」 버튼과 모순됐다.
     */
    active: Boolean? = null,
) {
    val colors = LocalKidhackColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.brandLight),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 20.sp)
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                color = colors.text,
            )
            Text(text = subtitle, color = colors.muted, fontSize = 12.sp)
        }
        if (active != null) {
            Text(
                text = stringResource(
                    if (active) R.string.child_services_running else R.string.child_services_stopped,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) Color(0xFFF0FDF4) else colors.surface)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = if (active) colors.safe else colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun LimitRow(emoji: String, label: String, value: String, showDivider: Boolean = true) {
    val colors = LocalKidhackColors.current
    Column {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, fontSize = 14.sp)
            Spacer(Modifier.size(12.dp))
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 12.sp,
            )
            Text(
                text = value,
                color = colors.text,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 12.sp,
            )
        }
        if (showDivider) HorizontalDivider(color = colors.border)
    }
}

private fun usageBarColor(name: String, fallback: Color): Color = when {
    name.contains("YouTube", ignoreCase = true) -> Color(0xFFF87171)
    name.contains("카카오") -> Color(0xFFFACC15)
    else -> fallback
}
