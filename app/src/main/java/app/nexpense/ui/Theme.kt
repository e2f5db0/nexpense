package app.nexpense.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF080B14)
val Surface = Color(0xFF111827)
val Blue = Color(0xFF60A5FA)
val NostrPurple = Color(0xFF8B5CF6)

private val colors = darkColorScheme(
    primary = Blue,
    onPrimary = Color(0xFF07101F),
    secondary = NostrPurple,
    onSecondary = Color.White,
    background = Navy,
    onBackground = Color(0xFFF3F4F6),
    surface = Surface,
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF1D2638),
    onSurfaceVariant = Color(0xFFB4BED1),
    error = Color(0xFFF87171),
)

@Composable
fun NexpenseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
