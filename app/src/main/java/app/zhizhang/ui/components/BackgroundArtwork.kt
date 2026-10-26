package app.zhizhang.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.zhizhang.ui.theme.zh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BackgroundPreset(
    val id: String,
    val name: String,
    val description: String,
)

object BackgroundPresets {
    val default = BackgroundPreset("default", "默认", "现在的清透冷银")
    val all = listOf(
        default,
        BackgroundPreset("morning", "晨雾", "浅蓝与薄荷色的清晨"),
        BackgroundPreset("sunset", "晚霞", "暖橙与淡紫的日落"),
        BackgroundPreset("night", "夜航", "深蓝、月光和星点"),
    )
    fun byId(id: String): BackgroundPreset = all.firstOrNull { it.id == id } ?: default
}

/**
 * 背景绘制层，不含 Haze。主界面和设置页预览共用同一套画法，
 * 保证预览和实际效果一致。
 */
@Composable
fun BackgroundArtwork(
    presetId: String,
    opacity: Float,
    customImagePath: String?,
    modifier: Modifier = Modifier,
) {
    val alpha = opacity.coerceIn(0f, 1f)
    val base = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize().background(base)) {
        when (presetId) {
            "morning" -> PresetCanvas("morning", alpha)
            "sunset" -> PresetCanvas("sunset", alpha)
            "night" -> PresetCanvas("night", alpha)
            "custom" -> CustomArtwork(customImagePath, alpha)
            else -> DefaultArtwork(alpha)
        }
    }
}

@Composable
private fun DefaultArtwork(alpha: Float) {
    val extra = MaterialTheme.zh
    val background = MaterialTheme.colorScheme.background
    Canvas(Modifier.fillMaxSize().alpha(alpha)) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.verticalGradient(
                listOf(background, extra.canvasBottom),
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(extra.auraBlue.copy(alpha = 0.56f), Color.Transparent),
                center = Offset(w * 0.96f, h * 0.10f),
                radius = w * 0.78f,
            ),
            radius = w * 0.78f,
            center = Offset(w * 0.96f, h * 0.10f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(extra.auraMint.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(w * 0.02f, h * 0.03f),
                radius = w * 0.76f,
            ),
            radius = w * 0.76f,
            center = Offset(w * 0.02f, h * 0.03f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(extra.auraPeach.copy(alpha = 0.42f), Color.Transparent),
                center = Offset(w * 0.06f, h * 0.78f),
                radius = w * 0.74f,
            ),
            radius = w * 0.74f,
            center = Offset(w * 0.06f, h * 0.78f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(extra.auraBlue.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(w * 0.18f, h * 0.46f),
                radius = w * 0.54f,
            ),
            radius = w * 0.54f,
            center = Offset(w * 0.18f, h * 0.46f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(extra.auraPeach.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(w * 0.86f, h * 0.58f),
                radius = w * 0.48f,
            ),
            radius = w * 0.48f,
            center = Offset(w * 0.86f, h * 0.58f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(extra.auraLilac.copy(alpha = 0.34f), Color.Transparent),
                center = Offset(w * 0.92f, h * 0.92f),
                radius = w * 0.64f,
            ),
            radius = w * 0.64f,
            center = Offset(w * 0.92f, h * 0.92f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.26f), Color.Transparent),
                center = Offset(w * 0.55f, h * 0.18f),
                radius = w * 0.72f,
            ),
            radius = w * 0.72f,
            center = Offset(w * 0.55f, h * 0.18f),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.13f),
            radius = w * 0.52f,
            center = Offset(w * 1.02f, h * 0.08f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.09f),
            radius = w * 0.40f,
            center = Offset(w * 1.02f, h * 0.08f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
        )
    }
}

@Composable
private fun PresetCanvas(id: String, alpha: Float) {
    Canvas(Modifier.fillMaxSize().alpha(alpha)) {
        val w = size.width
        val h = size.height
        when (id) {
            "morning" -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color(0xFFF7FBFF),
                        0.5f to Color(0xFFDDEEF8),
                        1f to Color(0xFFC7E8DF),
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFFFF3CF).copy(alpha = 0.9f), Color.Transparent),
                        center = Offset(w * 0.76f, h * 0.18f),
                        radius = w * 0.30f,
                    ),
                    radius = w * 0.30f,
                    center = Offset(w * 0.76f, h * 0.18f),
                )
                val farHill = Path().apply {
                    moveTo(0f, h * 0.70f)
                    cubicTo(w * 0.25f, h * 0.58f, w * 0.46f, h * 0.76f, w, h * 0.64f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(farHill, Color(0xFFA9D9CB).copy(alpha = 0.42f))
                val nearHill = Path().apply {
                    moveTo(0f, h * 0.82f)
                    cubicTo(w * 0.30f, h * 0.70f, w * 0.66f, h * 0.90f, w, h * 0.74f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(nearHill, Color(0xFF75BDAE).copy(alpha = 0.30f))
                repeat(7) { i ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.42f),
                        radius = 1.6.dp.toPx(),
                        center = Offset(w * (0.10f + i * 0.12f), h * (0.30f + (i % 3) * 0.06f)),
                    )
                }
            }

            "sunset" -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color(0xFFFFF8EE),
                        0.52f to Color(0xFFFFD7C1),
                        1f to Color(0xFFD8C2E7),
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFFFB36B).copy(alpha = 0.82f), Color.Transparent),
                        center = Offset(w * 0.30f, h * 0.42f),
                        radius = w * 0.36f,
                    ),
                    radius = w * 0.36f,
                    center = Offset(w * 0.30f, h * 0.42f),
                )
                drawCircle(
                    color = Color(0xFFFFE3A8).copy(alpha = 0.82f),
                    radius = w * 0.12f,
                    center = Offset(w * 0.30f, h * 0.42f),
                )
                repeat(4) { i ->
                    drawRect(
                        color = Color.White.copy(alpha = 0.12f + i * 0.025f),
                        topLeft = Offset(0f, h * (0.62f + i * 0.075f)),
                        size = androidx.compose.ui.geometry.Size(w, h * 0.012f),
                    )
                }
                val cloud = Path().apply {
                    moveTo(w * 0.58f, h * 0.24f)
                    cubicTo(w * 0.66f, h * 0.18f, w * 0.80f, h * 0.20f, w * 0.86f, h * 0.26f)
                    cubicTo(w * 0.78f, h * 0.29f, w * 0.66f, h * 0.30f, w * 0.58f, h * 0.24f)
                    close()
                }
                drawPath(cloud, Color.White.copy(alpha = 0.20f))
            }

            "night" -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color(0xFF08101E),
                        0.55f to Color(0xFF152742),
                        1f to Color(0xFF30455D),
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFD9E7FF).copy(alpha = 0.88f), Color.Transparent),
                        center = Offset(w * 0.76f, h * 0.20f),
                        radius = w * 0.20f,
                    ),
                    radius = w * 0.20f,
                    center = Offset(w * 0.76f, h * 0.20f),
                )
                drawCircle(
                    color = Color(0xFF0B1627).copy(alpha = 0.9f),
                    radius = w * 0.165f,
                    center = Offset(w * 0.80f, h * 0.185f),
                )
                repeat(34) { i ->
                    val x = ((i * 47) % 100) / 100f
                    val y = ((i * 71 + 13) % 62) / 100f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.28f + (i % 4) * 0.12f),
                        radius = (i % 3 + 1) * 0.7.dp.toPx(),
                        center = Offset(w * x, h * y),
                    )
                }
                val ridge = Path().apply {
                    moveTo(0f, h * 0.76f)
                    lineTo(w * 0.22f, h * 0.63f)
                    lineTo(w * 0.39f, h * 0.72f)
                    lineTo(w * 0.58f, h * 0.57f)
                    lineTo(w * 0.78f, h * 0.71f)
                    lineTo(w, h * 0.62f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(ridge, Color(0xFF0A1524).copy(alpha = 0.78f))
            }
        }
    }
}

@Composable
private fun CustomArtwork(path: String?, alpha: Float) {
    var bitmap by remember(path) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = if (path.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeFile(path)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    if (bitmap == null) {
        DefaultArtwork(alpha)
    } else {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(alpha),
            contentScale = ContentScale.Crop,
        )
    }
}
