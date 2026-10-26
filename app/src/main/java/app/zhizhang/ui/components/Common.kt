package app.zhizhang.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhizhang.data.Category
import app.zhizhang.data.Formats
import app.zhizhang.data.TxSource
import app.zhizhang.data.TxType
import app.zhizhang.ui.theme.AmountHero
import app.zhizhang.ui.theme.zh
import java.time.YearMonth
import kotlin.math.abs

@Composable
fun ZhCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassPanel(
        modifier = modifier,
        onClick = onClick,
        contentPadding = contentPadding,
    ) {
        content()
    }
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MonthSwitcher(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canGoNext: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onMonthClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "上个月",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "${month.year}年${month.monthValue}月",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .then(
                    if (onMonthClick != null) {
                        Modifier.clickable(onClick = onMonthClick)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 8.dp, vertical = 5.dp),
            style = if (compact) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        IconButton(
            onClick = onNext,
            enabled = canGoNext,
            modifier = Modifier.size(36.dp).alpha(if (canGoNext) 1f else 0.25f),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "下个月",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CategoryBadge(
    category: Category,
    size: Dp = 40.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(
                color = Color(category.color).copy(alpha = 0.12f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = category.emoji,
            style = MaterialTheme.typography.titleSmall,
            color = Color(category.color),
        )
    }
}

@Composable
fun Pill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    fontSize: Int = 11,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontSize = fontSize.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun SourceTag(source: TxSource) {
    when (source) {
        TxSource.NOTIFICATION -> Pill(
            text = "自动",
            containerColor = MaterialTheme.zh.autoBadge,
            contentColor = MaterialTheme.zh.autoBadgeText,
        )
        TxSource.IMPORT -> Pill(
            text = "导入",
            containerColor = MaterialTheme.zh.chip,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TxSource.AI -> Pill(
            text = "AI",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        TxSource.MANUAL -> Unit
    }
}

@Composable
fun HeroAmount(
    cents: Long,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    val absolute = abs(cents)
    val integer = "%,d".format(absolute / 100)
    val decimal = ".%02d".format(absolute % 100)
    val size = when {
        integer.length >= 8 -> 32.sp
        integer.length >= 6 -> 38.sp
        else -> 44.sp
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = "¥",
            style = MaterialTheme.typography.titleLarge,
            color = color.copy(alpha = 0.62f),
            modifier = Modifier.padding(bottom = 7.dp, end = 2.dp),
        )
        Text(
            text = integer,
            style = AmountHero.copy(fontSize = size, lineHeight = (size.value + 6).sp),
            color = color,
        )
        Text(
            text = decimal,
            style = MaterialTheme.typography.headlineSmall,
            color = color.copy(alpha = 0.62f),
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

@Composable
fun RoundIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    tint: Color = MaterialTheme.colorScheme.onBackground,
) {
    GlassCircleButton(
        modifier = modifier.size(44.dp),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
fun BudgetDialog(
    currentBudgetCents: Long,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(currentBudgetCents) {
        mutableStateOf(if (currentBudgetCents > 0) Formats.centsToInput(currentBudgetCents) else "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("设置月度预算") },
        text = {
            Column {
                Text(
                    text = "用来提醒自己，不是给自己设限。留空即可关闭预算提醒。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { input ->
                        value = input.filter { it.isDigit() || it == '.' }.take(12)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("每月预算（元）") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String = "确定",
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title) },
        text = {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
fun TypeLabel(type: TxType) {
    val color = if (type == TxType.INCOME) {
        MaterialTheme.zh.income
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = type.label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

@Composable
fun ThinProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.zh.chip,
    height: Dp = 6.dp,
) {
    Box(
        modifier = modifier
            .height(height)
            .background(trackColor, RoundedCornerShape(50)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .background(color, RoundedCornerShape(50)),
        )
    }
}
