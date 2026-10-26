package app.zhizhang.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.zhizhang.ui.theme.zh
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** 玻璃模糊的统一参数。真机 Android 12+ 会使用背景模糊，较旧系统自动退回磨砂半透明。 */
@Composable
fun liquidHazeStyle(
    tintAlpha: Float = 0.26f,
    darkTint: Boolean = isSystemInDarkTheme(),
): HazeStyle {
    val tintColor = if (darkTint) Color(0xFF080B12) else Color.White
    return HazeStyle(
        backgroundColor = MaterialTheme.colorScheme.background,
        tint = HazeTint(tintColor.copy(alpha = if (darkTint) tintAlpha * 0.82f else tintAlpha)),
        blurRadius = 20.dp,
        noiseFactor = 0.018f,
    )
}

/**
 * 冷银背景：大面积彩色折射 + 一道斜向高光。
 * 它同时作为 Haze 的 source，玻璃面板在它上面产生真实的背景模糊。
 */
@Composable
fun LiquidBackground(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    presetId: String = "default",
    opacity: Float = 1f,
    customImagePath: String? = null,
) {
    val sourceModifier = if (hazeState != null) {
        modifier.hazeSource(hazeState)
    } else {
        modifier
    }
    Box(
        modifier = sourceModifier.fillMaxSize(),
    ) {
        BackgroundArtwork(
            presetId = presetId,
            opacity = opacity,
            customImagePath = customImagePath,
        )
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val extra = MaterialTheme.zh
    Box(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = shape,
                ambientColor = extra.glassShadow,
                spotColor = extra.glassShadow,
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(extra.glassTop, extra.glassBottom),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        extra.glassBorder,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                        extra.gold.copy(alpha = 0.30f),
                    ),
                ),
                shape = shape,
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            extra.glassHighlight.copy(alpha = 0.56f),
                            Color.Transparent,
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun GlassCircleButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val extra = MaterialTheme.zh
    val haze = LocalHazeState.current
    val hazeStyle = liquidHazeStyle(tintAlpha = 0.34f)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction)
            .shadow(6.dp, CircleShape, ambientColor = extra.glassShadow, spotColor = extra.glassShadow)
            .clip(CircleShape)
            .then(if (haze != null) Modifier.hazeEffect(haze, hazeStyle) else Modifier)
            .background(Brush.verticalGradient(listOf(extra.glassTop, extra.glassBottom)))
            .border(1.dp, extra.glassBorder.copy(alpha = 0.72f), CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** 蓝色/中性液态玻璃操作按钮，用于发送、加号等高频操作。 */
@Composable
fun LiquidActionButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val extra = MaterialTheme.zh
    val haze = LocalHazeState.current
    val hazeStyle = liquidHazeStyle(tintAlpha = 0.30f)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressScale(interaction, pressedScale = 0.90f)
            .shadow(6.dp, CircleShape, ambientColor = extra.glassShadow, spotColor = extra.glassShadow)
            .clip(CircleShape)
            .then(if (haze != null) Modifier.hazeEffect(haze, hazeStyle) else Modifier)
            .background(
                Brush.linearGradient(
                    listOf(
                        tint.copy(alpha = if (enabled) 0.92f else 0.32f),
                        tint.copy(alpha = if (enabled) 0.68f else 0.22f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.48f), CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
