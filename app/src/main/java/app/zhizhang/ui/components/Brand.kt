package app.zhizhang.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.zhizhang.R
import app.zhizhang.ui.theme.zh

/** 与桌面启动图标同一套书页与勾选图形，不再使用汉字“智”。 */
@Composable
fun AppMark(
    size: Dp = 38.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.app_icon),
            contentDescription = "有数",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun BaiduMark(
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(R.drawable.ic_baidu_cloud),
        contentDescription = "百度网盘",
        modifier = modifier.size(size),
    )
}

@Composable
fun MerchantBadge(
    text: String,
    color: Color,
    size: Dp = 34.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.trim().take(1).ifBlank { "·" },
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            color = color,
        )
    }
}
