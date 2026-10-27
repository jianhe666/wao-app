package app.zhizhang.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.zhizhang.ui.theme.zh
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import dev.chrisbanes.haze.hazeEffect

/**
 * 所有原生控件共用的液态玻璃表面。
 * 有 backdrop + Android 12+ 时走真实背景采样；否则用 Haze/磨砂降级。
 */
val LocalGlassTransparency = androidx.compose.runtime.staticCompositionLocalOf { 0.30f }

@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(18.dp),
    tint: Color = MaterialTheme.zh.glassTop.copy(alpha = 0.16f),
    lensHeight: Dp = 10.dp,
    lensAmount: Dp = 22.dp,
    blurRadius: Dp = 5.dp,
    backdropOverride: com.kyant.backdrop.Backdrop? = null,
): Modifier {
    val backdrop = backdropOverride ?: LocalGlassBackdrop.current
    val haze = LocalHazeState.current
    val transparency = LocalGlassTransparency.current.coerceIn(0f, 1f)
    val opacity = 1f - transparency
    val clarity = (0.25f + 0.75f * opacity).coerceIn(0.25f, 1f)
    val canLens = backdrop != null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        !isRobolectric
    val cornerShape = shape as? CornerBasedShape ?: RoundedCornerShape(18.dp)

    return if (canLens && backdrop != null) {
        clip(shape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { cornerShape },
                effects = {
                    vibrancy()
                    blur((blurRadius * (0.20f + 1.9f * opacity)).toPx())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        lens(
                            refractionHeight = lensHeight.toPx(),
                            refractionAmount = (lensAmount * (0.55f + 0.55f * opacity)).toPx(),
                            depthEffect = true,
                            chromaticAberration = true,
                        )
                    }
                },
                onDrawSurface = {
                    if (tint.alpha > 0f) {
                        drawRect(tint.copy(alpha = (tint.alpha * (0.35f + 0.9f * opacity)).coerceIn(0.035f, 0.42f)))
                    }
                },
            )
            .border(0.7.dp, Color.White.copy(alpha = 0.18f), shape)
    } else {
        val hazeStyle = liquidHazeStyle(tintAlpha = 0.28f)
        clip(shape)
            .then(if (haze != null) hazeEffect(haze, hazeStyle) else Modifier)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.zh.glassTop.copy(alpha = (0.12f + 0.32f * opacity) * clarity),
                        MaterialTheme.zh.glassBottom.copy(alpha = (0.05f + 0.20f * opacity) * clarity),
                    ),
                ),
            )
            .border(0.7.dp, Color.White.copy(alpha = 0.20f), shape)
    }
}
