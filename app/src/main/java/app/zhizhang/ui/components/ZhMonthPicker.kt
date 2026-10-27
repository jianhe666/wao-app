package app.zhizhang.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.zhizhang.ui.theme.zh
import app.zhizhang.ui.components.liquidGlass
import java.time.YearMonth

/** 快速跳年月的月历；只允许选择当前月及以前。 */
@Composable
fun ZhMonthPickerDialog(
    selectedMonth: YearMonth,
    maxMonth: YearMonth = YearMonth.now(),
    onDismiss: () -> Unit,
    onSelect: (YearMonth) -> Unit,
) {
    var year by remember(selectedMonth) { mutableIntStateOf(selectedMonth.year) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(28.dp),
                        tint = MaterialTheme.zh.glassTop.copy(alpha = 0.20f),
                        lensHeight = 14.dp,
                        lensAmount = 28.dp,
                        blurRadius = 6.dp,
                    ),
                shape = RoundedCornerShape(28.dp),
                color = Color.Transparent,
                border = null,
                shadowElevation = 20.dp,
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                    Text(
                        text = "选择月份",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "点年份或月份即可跳转",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { year -= 1 },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "上一年",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "${year}年",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        IconButton(
                            onClick = { if (year < maxMonth.year) year += 1 },
                            enabled = year < maxMonth.year,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "下一年",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if (year < maxMonth.year) 1f else 0.25f,
                                ),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        (maxMonth.year downTo maxMonth.year - 12).forEach { candidateYear ->
                            val selected = candidateYear == year
                            Text(
                                text = "$candidateYear",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                        } else {
                                            Color.Transparent
                                        },
                                    )
                                    .clickable { year = candidateYear }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    for (row in 0 until 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (column in 0 until 4) {
                                val month = row * 4 + column + 1
                                val target = YearMonth.of(year, month)
                                val allowed = !target.isAfter(maxMonth)
                                val selected = target == selectedMonth
                                val isCurrent = target == maxMonth
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            when {
                                                selected -> MaterialTheme.colorScheme.primary
                                                isCurrent -> MaterialTheme.zh.chip.copy(alpha = 0.55f)
                                                else -> Color.Transparent
                                            },
                                        )
                                        .then(
                                            if (allowed && !selected) {
                                                Modifier.border(
                                                    1.dp,
                                                    MaterialTheme.zh.hairline.copy(alpha = 0.35f),
                                                    RoundedCornerShape(12.dp),
                                                )
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .clickable(enabled = allowed) {
                                            onSelect(target)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${month}月",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            selected -> MaterialTheme.colorScheme.onPrimary
                                            allowed -> MaterialTheme.colorScheme.onSurface
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                onSelect(maxMonth)
                            },
                        ) { Text("回到本月") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("取消") }
                    }
                }
            }
        }
    }
}
