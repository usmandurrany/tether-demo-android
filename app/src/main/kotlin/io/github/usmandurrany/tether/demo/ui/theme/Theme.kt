package io.github.usmandurrany.tether.demo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Indigo, like the desktop demo. A fixed scheme rather than the wallpaper's
 * (dynamic color), so the demo looks the same on every phone.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E3FF),
    onPrimaryContainer = Color(0xFF1E1B6B),
    inversePrimary = Color(0xFFBAC3FF),
    secondary = Color(0xFF4338CA),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E3FF),
    onSecondaryContainer = Color(0xFF1E1B6B),
    tertiary = Color(0xFF0F766E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF134E4A),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFCE4E4),
    onErrorContainer = Color(0xFF7A1A14),
    background = Color.White,
    onBackground = Color(0xFF1B1B21),
    surface = Color.White,
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFF1F2F8),
    onSurfaceVariant = Color(0xFF5B5E71),
    surfaceTint = Color(0xFF4F46E5),
    outline = Color(0xFF9496A6),
    outlineVariant = Color(0xFFE4E5EE),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F5FA),
    surfaceContainer = Color(0xFFF3F3FA),
    surfaceContainerHigh = Color(0xFFF1F2F8),
    surfaceContainerHighest = Color(0xFFE6E8F3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF1E1B6B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E3FF),
    inversePrimary = Color(0xFF4F46E5),
    secondary = Color(0xFFC7CDFF),
    onSecondary = Color(0xFF1E1B6B),
    secondaryContainer = Color(0xFF3730A3),
    onSecondaryContainer = Color(0xFFE0E3FF),
    tertiary = Color(0xFF5EEAD4),
    onTertiary = Color(0xFF134E4A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF282A2F),
    onSurfaceVariant = Color(0xFFC6C5D0),
    surfaceTint = Color(0xFFBAC3FF),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
)

/** Colors Material 3 has no slot for: chat bubbles, the chat wallpaper and presence. */
@Immutable
data class ChatColors(
    val bubbleOut: Color,
    val onBubbleOut: Color,
    val onBubbleOutMuted: Color,
    val bubbleIn: Color,
    val onBubbleIn: Color,
    val wallpaper: Color,
    val wallpaperDot: Color,
    val readTick: Color,
    val online: Color,
    val away: Color,
    val busy: Color,
    val offline: Color,
    /** The Log tab's console is dark in both themes. */
    val console: Color,
    val onConsole: Color,
    val consoleMuted: Color,
    val consoleSent: Color,
    val consoleReceived: Color,
    val consoleError: Color,
)

private val LightChatColors = ChatColors(
    bubbleOut = Color(0xFF4F46E5),
    onBubbleOut = Color.White,
    onBubbleOutMuted = Color(0xFFDADCFF),
    bubbleIn = Color.White,
    onBubbleIn = Color(0xFF1B1B21),
    wallpaper = Color(0xFFE6E8F3),
    wallpaperDot = Color(0xFFD3D6E8),
    readTick = Color(0xFF7DE3F4),
    online = Color(0xFF16A34A),
    away = Color(0xFFF59E0B),
    busy = Color(0xFFDC2626),
    offline = Color(0xFF9CA3AF),
    console = Color(0xFF14151F),
    onConsole = Color(0xFFD6D8E8),
    consoleMuted = Color(0xFF8A8DA3),
    consoleSent = Color(0xFFA5B4FC),
    consoleReceived = Color(0xFF5EEAD4),
    consoleError = Color(0xFFFCA5A5),
)

private val DarkChatColors = LightChatColors.copy(
    bubbleOut = Color(0xFF4338CA),
    bubbleIn = Color(0xFF282A2F),
    onBubbleIn = Color(0xFFE4E1E9),
    wallpaper = Color(0xFF0F1015),
    wallpaperDot = Color(0xFF1C1E26),
    console = Color(0xFF0B0C12),
)

val LocalChatColors = staticCompositionLocalOf { LightChatColors }

/** The demo's theme: Material 3 in indigo, light or dark with the system. */
@Composable
fun TetherTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalChatColors provides if (dark) DarkChatColors else LightChatColors) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}

/** Shortcut for the chat colors, next to MaterialTheme.colorScheme. */
val chatColors: ChatColors
    @Composable get() = LocalChatColors.current
