package app.zhizhang.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.roundToInt
import app.zhizhang.data.Formats
import app.zhizhang.domain.CategorySlice
import app.zhizhang.ui.theme.zh
import kotlin.math.max

@Composable
fun CategoryDonut(
    slices: List<CategorySlice>,
    totalCents: Long,
    modifier: Modifier = Modifier,
    selectedSlice: CategorySlice? = null,
    onSliceSelected: (CategorySlice) -> Unit = {},
    onClearSelection: () -> Unit = {},
) {
    Box(
        modifier = modifier.testTag("category-donut"),
        contentAlignment = Alignment.Center,
    ) {
        val track = MaterialTheme.zh.chip
        val emptyText = MaterialTheme.colorScheme.onSurfaceVariant
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(slices) {
                    detectTapGestures { offset ->
                        val hit = sliceAt(
                            offset = offset,
                            size = Size(size.width.toFloat(), size.height.toFloat()),
                            slices = slices,
                            strokePx = 18.dp.toPx(),
                        )
                        if (hit != null) onSliceSelected(hit) else onClearSelection()
                    }
                },
        ) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2 + 2.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (totalCents > 0 && slices.isNotEmpty()) {
                var start = -90f
                val gap = 2.2f
                slices.forEach { slice ->
                    val sweep = slice.ratio * 360f
                    if (sweep > gap) {
                        val selected = selectedSlice?.category?.id == slice.category.id
                        val extra = if (selected) 7.dp.toPx() else 0f
                        val localStroke = stroke + extra
                        val localInset = inset - extra / 2f
                        drawArc(
                            color = Color(slice.category.color),
                            startAngle = start + gap / 2,
                            sweepAngle = sweep - gap,
                            useCenter = false,
                            topLeft = Offset(localInset, localInset),
                            size = Size(
                                size.width - localInset * 2,
                                size.height - localInset * 2,
                            ),
                            style = Stroke(width = localStroke, cap = StrokeCap.Butt),
                        )
                    }
                    start += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (selectedSlice != null) {
                Text(
                    text = selectedSlice.category.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(selectedSlice.category.color),
                    modifier = Modifier.testTag("donut-selected-category"),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${(selectedSlice.ratio * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = Formats.money(selectedSlice.amountCents),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = if (totalCents > 0) "本月支出" else "还没有支出",
                    style = MaterialTheme.typography.labelMedium,
                    color = emptyText,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (totalCents > 0) Formats.money(totalCents) else "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun sliceAt(
    offset: Offset,
    size: Size,
    slices: List<CategorySlice>,
    strokePx: Float,
): CategorySlice? {
    if (slices.isEmpty()) return null
    val center = Offset(size.width / 2f, size.height / 2f)
    val dx = (offset.x - center.x).toDouble()
    val dy = (offset.y - center.y).toDouble()
    val distance = kotlin.math.sqrt(dx * dx + dy * dy).toFloat()
    val outer = kotlin.math.min(size.width, size.height) / 2f - strokePx / 2f
    val inner = outer - strokePx
    if (distance !in inner..outer) return null
    var angle = Math.toDegrees(atan2(dy, dx)).toFloat()
    angle = (angle + 450f) % 360f
    var cursor = 0f
    for (slice in slices) {
        val sweep = slice.ratio * 360f
        if (angle >= cursor && angle < cursor + sweep) return slice
        cursor += sweep
    }
    return null
}

@Composable
fun DailyBarChart(
    values: List<Long>,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
    selectedIndex: Int? = null,
    onBarClick: (Int) -> Unit = {},
) {
    val primary = MaterialTheme.colorScheme.primary
    val gold = MaterialTheme.zh.gold
    val track = MaterialTheme.zh.chip
    val baseline = MaterialTheme.zh.hairline
    val maxValue = max(values.maxOrNull() ?: 0L, 1L)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .testTag("daily-bar-chart")
                .pointerInput(values.size) {
                    detectTapGestures { offset ->
                        if (values.isNotEmpty()) {
                            val index = (offset.x / (size.width / values.size))
                                .toInt()
                                .coerceIn(values.indices)
                            onBarClick(index)
                        }
                    }
                },
        ) {
            if (values.isEmpty()) return@Canvas
            drawLine(
                color = baseline,
                start = Offset(0f, size.height - 1.dp.toPx()),
                end = Offset(size.width, size.height - 1.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
            val gap = 3.dp.toPx()
            val slot = size.width / values.size
            val barWidth = (slot - gap).coerceAtLeast(1.5f)
            values.forEachIndexed { index, value ->
                val ratio = value.toFloat() / maxValue
                val barHeight = if (value == 0L) 2.dp.toPx() else (ratio * size.height * 0.88f)
                val left = index * slot + gap / 2
                val selected = index == selectedIndex
                val color = when {
                    selected -> gold
                    value == 0L -> track
                    index == highlightIndex -> primary
                    else -> primary.copy(alpha = 0.55f)
                }
                val width = if (selected) (barWidth + 2.dp.toPx()).coerceAtMost(slot - 1f) else barWidth
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left - (width - barWidth) / 2f, size.height - barHeight),
                    size = Size(width, barHeight),
                    cornerRadius = CornerRadius(width / 2, width / 2),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (values.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("1日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("8日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("15日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("22日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${values.size}日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DonutLegendRow(
    slice: CategorySlice,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .then(
                    Modifier,
                ),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(color = Color(slice.category.color))
            }
        }
        Spacer(Modifier.size(9.dp))
        Text(
            text = slice.category.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "${(slice.ratio * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = Formats.money(slice.amountCents),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}
