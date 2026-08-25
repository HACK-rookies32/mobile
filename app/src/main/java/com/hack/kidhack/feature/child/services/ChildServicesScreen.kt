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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.hack.kidhack.feature.child.formatDurationSec
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChildServicesScreen(
    viewModel: ChildServicesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

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
        KidCard {
            ServiceHeader("📍", stringResource(R.string.child_services_location), stringResource(R.string.child_services_location_desc))
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
                    text = stringResource(R.string.child_services_updated),
                    modifier = Modifier.weight(1f),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                )
                Text(
                    text = stringResource(
                        if (state.gpsEnabled) R.string.child_services_gps_on else R.string.child_services_gps_off,
                    ),
                    color = colors.brand,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.sp,
                )
            }
        }

        KidCard {
            ServiceHeader("⏰", stringResource(R.string.child_services_limits), stringResource(R.string.child_services_limits_desc))
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
            ServiceHeader("🚫", stringResource(R.string.child_services_harmful), stringResource(R.string.child_services_harmful_desc))
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
                                .background(Color(0xFFF0FDF4))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("✅", fontSize = 14.sp)
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
                text = stringResource(R.string.child_services_blocked_today, state.blockedToday),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 12.sp,
            )
        }

        KidCard {
            ServiceHeader("📊", stringResource(R.string.child_services_usage), stringResource(R.string.child_services_usage_desc))
            Spacer(Modifier.height(8.dp))
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
private fun ServiceHeader(emoji: String, title: String, subtitle: String) {
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
        Text(
            text = stringResource(R.string.child_services_running),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF0FDF4))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = colors.safe,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 12.sp,
        )
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
