package app.zhizhang.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightScheme = lightColorScheme(
    primary = Cobalt,
    onPrimary = Color.White,
    primaryContainer = IceBlue,
    onPrimaryContainer = Color(0xFF10256A),
    secondary = Amber,
    onSecondary = Color(0xFF2A1D0B),
    secondaryContainer = Color(0xFFF4E9D4),
    onSecondaryContainer = Color(0xFF3A2A10),
    tertiary = Color(0xFF6A89B8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6EDF7),
    onTertiaryContainer = Color(0xFF1D334D),
    background = CanvasTop,
    onBackground = Ink,
    surface = Color(0xFFFCFAF5),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9EDF5),
    onSurfaceVariant = Muted,
    outline = Color(0xFFB8C0CE),
    outlineVariant = Color(0xFFE0E4EB),
    error = Color(0xFFC2443A),
    onError = Color.White,
    inverseSurface = Ink,
    inverseOnSurface = HeroText,
    inversePrimary = Color(0xFFAFC3FF),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB7C8FF),
    onPrimary = Color(0xFF0A1A4D),
    primaryContainer = Color(0xFF1B2D5A),
    onPrimaryContainer = Color(0xFFE5ECFF),
    secondary = Color(0xFFE2C184),
    onSecondary = Color(0xFF2B1D08),
    secondaryContainer = Color(0xFF3D2D13),
    onSecondaryContainer = Color(0xFFF4E5C6),
    tertiary = Color(0xFFE2C184),
    onTertiary = Color(0xFF2B1D08),
    tertiaryContainer = Color(0xFF3D2D13),
    onTertiaryContainer = Color(0xFFF4E5C6),
    background = NightCanvasTop,
    onBackground = NightInk,
    surface = Color(0xFF0F141D),
    onSurface = NightInk,
    surfaceVariant = Color(0xFF1A202B),
    onSurfaceVariant = NightMuted,
    outline = Color(0xFF454F5F),
    outlineVariant = Color(0xFF232A36),
    error = Color(0xFFFFB4A7),
    onError = Color(0xFF4A1510),
    inverseSurface = NightInk,
    inverseOnSurface = Ink,
    inversePrimary = Ink,
)

private val ZhShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(13.dp),
    medium = RoundedCornerShape(19.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

val LocalZhColors = staticCompositionLocalOf { LightZhColors }

val MaterialTheme.zh: ZhExtraColors
    @Composable
    @ReadOnlyComposable
    get() = LocalZhColors.current

@Composable
fun ZhizhangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    val extra = if (darkTheme) DarkZhColors else LightZhColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalZhColors provides extra) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ZhTypography,
            shapes = ZhShapes,
            content = content,
        )
    }
}
