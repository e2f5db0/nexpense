// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nexpense.R

val Navy = Color(0xFF07050C)
val Surface = Color(0xFF130D1D)
val Blue = Color(0xFF60A5FA)
val NostrPurple = Color(0xFF8B5CF6)
val PixelFont = FontFamily(Font(R.font.press_start_2p))

private val colors = darkColorScheme(
    primary = NostrPurple,
    onPrimary = Color.White,
    secondary = Color(0xFFC4B5FD),
    onSecondary = Navy,
    tertiary = Color(0xFFA3E635),
    background = Navy,
    onBackground = Color(0xFFF5F0FF),
    surface = Surface,
    onSurface = Color(0xFFF5F0FF),
    surfaceVariant = Color(0xFF21132F),
    onSurfaceVariant = Color(0xFFC5B8D4),
    outline = Color(0xFF6D43B0),
    error = Color(0xFFFF6B6B),
)

// Press Start 2P glyphs are exactly one em wide, so sizes stay small to
// avoid awkward wrapping; the pixel font is used everywhere in the app.
private val typography = Typography(
    displayLarge = TextStyle(fontFamily = PixelFont, fontSize = 22.sp, lineHeight = 34.sp),
    displayMedium = TextStyle(fontFamily = PixelFont, fontSize = 20.sp, lineHeight = 32.sp),
    displaySmall = TextStyle(fontFamily = PixelFont, fontSize = 18.sp, lineHeight = 28.sp),
    headlineLarge = TextStyle(fontFamily = PixelFont, fontSize = 16.sp, lineHeight = 26.sp),
    headlineMedium = TextStyle(fontFamily = PixelFont, fontSize = 14.sp, lineHeight = 24.sp),
    headlineSmall = TextStyle(fontFamily = PixelFont, fontSize = 12.sp, lineHeight = 20.sp),
    titleLarge = TextStyle(fontFamily = PixelFont, fontSize = 12.sp, lineHeight = 20.sp),
    titleMedium = TextStyle(fontFamily = PixelFont, fontSize = 11.sp, lineHeight = 18.sp),
    titleSmall = TextStyle(fontFamily = PixelFont, fontSize = 10.sp, lineHeight = 16.sp),
    bodyLarge = TextStyle(fontFamily = PixelFont, fontSize = 11.sp, lineHeight = 18.sp),
    bodyMedium = TextStyle(fontFamily = PixelFont, fontSize = 10.sp, lineHeight = 17.sp),
    bodySmall = TextStyle(fontFamily = PixelFont, fontSize = 9.sp, lineHeight = 15.sp),
    labelLarge = TextStyle(fontFamily = PixelFont, fontSize = 10.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontFamily = PixelFont, fontSize = 9.sp, lineHeight = 14.sp),
    labelSmall = TextStyle(fontFamily = PixelFont, fontSize = 8.sp, lineHeight = 12.sp),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

@Composable
fun NexpenseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
