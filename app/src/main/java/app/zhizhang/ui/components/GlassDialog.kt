package app.zhizhang.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.zhizhang.ui.theme.zh
import dev.chrisbanes.haze.hazeEffect

/** 与底栏一致的模糊玻璃弹窗；无 Haze（例如测试环境）时退化为磨砂玻璃。 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        val extra = MaterialTheme.zh
        val shape = RoundedCornerShape(26.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = shape,
                    ambientColor = extra.glassShadow,
                    spotColor = extra.glassShadow,
                )
                .liquidGlass(
                    shape = shape,
                    tint = extra.glassTop.copy(alpha = 0.34f),
                    lensHeight = 12.dp,
                    lensAmount = 26.dp,
                    blurRadius = 8.dp,
                )
                .padding(20.dp),
        ) {
            Column(content = content)
        }
    }
}
