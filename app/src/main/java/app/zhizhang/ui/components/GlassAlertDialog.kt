package app.zhizhang.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import app.zhizhang.ui.theme.zh
import dev.chrisbanes.haze.hazeEffect

/** 统一替换 Material AlertDialog，让所有弹窗都使用与底栏一致的磨砂玻璃。 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(28.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        val extra = MaterialTheme.zh
        val haze = LocalHazeState.current
        val hazeStyle = liquidHazeStyle(tintAlpha = 0.34f)
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = shape,
                    ambientColor = extra.glassShadow,
                    spotColor = extra.glassShadow,
                )
                .clip(shape)
                .then(if (haze != null) Modifier.hazeEffect(haze, hazeStyle) else Modifier)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            extra.glassTop.copy(alpha = 0.96f),
                            extra.glassBottom.copy(alpha = 0.93f),
                        ),
                    ),
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            extra.glassBorder,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.34f),
                        ),
                    ),
                    shape = shape,
                )
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            if (icon != null) {
                icon()
                Spacer(Modifier.height(8.dp))
            }
            if (title != null) {
                title()
            }
            if (text != null) {
                Spacer(Modifier.height(10.dp))
                text()
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}
