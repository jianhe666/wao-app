package app.zhizhang.ui.components

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 根据背景明暗决定内容使用深色还是白色文字。 */
object BackgroundContrast {

    fun presetNeedsDarkText(presetId: String, opacity: Float): Boolean {
        return presetId == "night" && opacity >= 0.55f
    }

    suspend fun customNeedsDarkText(path: String?, opacity: Float): Boolean {
        if (path.isNullOrBlank() || opacity < 0.30f) return false
        return withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inSampleSize = 24 }
                val bitmap = BitmapFactory.decodeFile(path, options) ?: return@runCatching false
                var total = 0.0
                var count = 0
                val stepX = (bitmap.width / 12).coerceAtLeast(1)
                val stepY = (bitmap.height / 12).coerceAtLeast(1)
                var x = 0
                while (x < bitmap.width) {
                    var y = 0
                    while (y < bitmap.height) {
                        val color = bitmap.getPixel(x, y)
                        val r = (color shr 16 and 0xFF) / 255.0
                        val g = (color shr 8 and 0xFF) / 255.0
                        val b = (color and 0xFF) / 255.0
                        total += 0.2126 * r + 0.7152 * g + 0.0722 * b
                        count++
                        y += stepY
                    }
                    x += stepX
                }
                bitmap.recycle()
                val imageLum = if (count == 0) 1.0 else total / count
                // 透明度越低，露出的默认底色越多，整体越亮。
                val blended = imageLum * opacity + 0.92 * (1.0 - opacity)
                blended < 0.52
            }.getOrDefault(false)
        }
    }
}
