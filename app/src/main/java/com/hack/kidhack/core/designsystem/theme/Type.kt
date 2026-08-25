package com.hack.kidhack.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Nunito (Figma) has no Hangul coverage.
 * Using the platform sans-serif so Korean glyphs resolve to Noto Sans CJK / One UI Sans
 * on typical Korean devices, with the Figma 900 / 700 / 600 weight ladder.
 */
val KidhackFontFamily: FontFamily = FontFamily.SansSerif

val FontBlack = FontWeight.W900
val FontBold = FontWeight.W700
val FontSemibold = FontWeight.W600

val KidhackTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontBlack,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        color = Text,
    ),
    displayMedium = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontBlack,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        color = Text,
    ),
    headlineLarge = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontBlack,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        color = Text,
    ),
    headlineMedium = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontBlack,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        color = Text,
    ),
    titleLarge = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = Text,
    ),
    titleMedium = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = Text,
    ),
    titleSmall = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Text,
    ),
    bodyLarge = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontSemibold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = Text,
    ),
    bodyMedium = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontSemibold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Text,
    ),
    bodySmall = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontSemibold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = Muted,
    ),
    labelLarge = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Text,
    ),
    labelMedium = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = Muted,
    ),
    labelSmall = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontSemibold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp,
        color = Muted,
    ),
)
