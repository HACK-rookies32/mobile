package com.hack.kidhack.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFFF58220)
val BrandDark = Color(0xFFD96D12)
val BrandLight = Color(0xFFFEF1E6)
val Safe = Color(0xFF16A34A)
val Warn = Color(0xFFF59E0B)
val Danger = Color(0xFFDC2626)
val Info = Color(0xFF2563EB)
val Surface = Color(0xFFF7F8FA)
val Card = Color(0xFFFFFFFF)
val Border = Color(0xFFE5E7EB)
val Muted = Color(0xFF6B7280)
val Text = Color(0xFF1F2937)

@Immutable
data class KidhackColors(
    val brand: Color,
    val brandDark: Color,
    val brandLight: Color,
    val safe: Color,
    val warn: Color,
    val danger: Color,
    val info: Color,
    val surface: Color,
    val card: Color,
    val border: Color,
    val muted: Color,
    val text: Color,
) {
    companion object {
        val Light = KidhackColors(
            brand = Brand,
            brandDark = BrandDark,
            brandLight = BrandLight,
            safe = Safe,
            warn = Warn,
            danger = Danger,
            info = Info,
            surface = Surface,
            card = Card,
            border = Border,
            muted = Muted,
            text = Text,
        )
    }
}

val LocalKidhackColors = staticCompositionLocalOf { KidhackColors.Light }
