package app.zhizhang.ui.components

import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 用原生 TextView 渲染对话内容，长按选择时直接走 Android 系统的
 * ActionMode / 选择手柄 / 系统剪贴板，而不是 Compose 自绘的文本菜单。
 */
@Composable
fun PlatformSelectableText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                setTextIsSelectable(true)
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                background = null
                setTextColor(color.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSize.value)
                val weight = style.fontWeight?.weight ?: 400
                typeface = Typeface.create(
                    Typeface.DEFAULT,
                    if (weight >= 600) Typeface.BOLD else Typeface.NORMAL,
                )
                setLineSpacing(0f, 1.18f)
            }
        },
        update = { view ->
            if (view.text.toString() != text) view.text = text
            view.setTextColor(color.toArgb())
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSize.value)
        },
    )
}
