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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nexpense.R

val Navy = Color(0xFF07050C)
val Surface = Color(0xFF130D1D)
val Blue = Color(0xFF60A5FA)
val NostrPurple = Color(0xFF8B5CF6)
val PixelFont = FontFamily(Font(R.font.press_start_2p))
private val BodyFont = FontFamily.Monospace

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

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = PixelFont, fontSize = 28.sp, lineHeight = 40.sp),
    headlineSmall = TextStyle(fontFamily = PixelFont, fontSize = 18.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = PixelFont, fontSize = 15.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    bodyLarge = TextStyle(fontFamily = BodyFont, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = BodyFont, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = PixelFont, fontSize = 10.sp, lineHeight = 16.sp),
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
