package app.zhizhang.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.zhizhang.data.Categories
import app.zhizhang.data.Category
import app.zhizhang.data.Formats
import app.zhizhang.data.TxType
import app.zhizhang.domain.CategorySlice
import app.zhizhang.domain.AccountSlice
import app.zhizhang.domain.Insight
import app.zhizhang.domain.InsightTone
import app.zhizhang.domain.MonthStats
import app.zhizhang.ui.components.CategoryDonut
import app.zhizhang.ui.components.DailyBarChart
import app.zhizhang.ui.components.DonutLegendRow
import app.zhizhang.ui.components.MonthSwitcher
import app.zhizhang.ui.components.SectionTitle
import app.zhizhang.ui.components.ZhCard
import app.zhizhang.ui.components.ZhMonthPickerDialog
import app.zhizhang.ui.theme.zh
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

@Composable
fun StatsScreen() {
    val viewModel: StatsViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val stats = state.stats
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val currentMonth = YearMonth.from(today)
    var showMonthPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 120.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Text(
            text = "统计",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        MonthSwitcher(
            month = state.month,
            onPrevious = { viewModel.shiftMonth(-1) },
            onNext = { viewModel.shiftMonth(1) },
            canGoNext = state.month.isBefore(currentMonth),
            onMonthClick = { showMonthPicker = true },
        )
        Spacer(Modifier.height(16.dp))

        when {
            state.loading || stats == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            stats.transactionCount == 0 -> {
                StatsEmpty(month = state.month)
            }

            else -> {
                SummaryCard(stats)
                Spacer(Modifier.height(14.dp))
                SpendingCard(stats)
                if (stats.accountSlices.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    AccountBreakdownCard(stats.accountSlices)
                }
                Spacer(Modifier.height(14.dp))
                TrendCard(
                    stats = stats,
                    highlightIndex = when {
                        state.month == currentMonth -> (today.dayOfMonth - 1).coerceAtLeast(0)
                        else -> stats.dailyExpenses.indices.maxByOrNull { stats.dailyExpenses[it] }
                    },
                    isCurrentMonth = state.month == currentMonth,
                )
                if (stats.topMerchants.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    SectionTitle(title = "常去的店")
                    Spacer(Modifier.height(10.dp))
                    MerchantCard(stats)
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (showMonthPicker) {
        ZhMonthPickerDialog(
            selectedMonth = state.month,
            maxMonth = currentMonth,
            onDismiss = { showMonthPicker = false },
            onSelect = { month ->
                viewModel.selectMonth(month)
                showMonthPicker = false
            },
        )
    }
}

@Composable
private fun SummaryCard(stats: MonthStats) {
    ZhCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryItem(
                label = "支出",
                amount = stats.expenseCents,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(
                modifier = Modifier.height(36.dp),
                color = MaterialTheme.zh.hairline,
            )
            SummaryItem(
                label = "收入",
                amount = stats.incomeCents,
                color = MaterialTheme.zh.income,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(
                modifier = Modifier.height(36.dp),
                color = MaterialTheme.zh.hairline,
            )
            SummaryItem(
                label = "结余",
                amount = stats.balanceCents,
                color = if (stats.balanceCents < 0) MaterialTheme.zh.expense else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    amount: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = Formats.money(amount),
            style = MaterialTheme.typography.titleSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SpendingCard(stats: MonthStats) {
    val slices = compactSlices(stats.slices)
    var selectedSlice by remember(slices) { mutableStateOf<CategorySlice?>(null) }
    ZhCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(18.dp),
    ) {
        SectionTitle(title = "支出构成")
        Spacer(Modifier.height(12.dp))
        if (stats.expenseCents == 0L) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "这个月还没有支出",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                CategoryDonut(
                    slices = slices,
                    totalCents = stats.expenseCents,
                    modifier = Modifier.size(190.dp),
                    selectedSlice = selectedSlice,
                    onSliceSelected = { selectedSlice = it },
                    onClearSelection = { selectedSlice = null },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (selectedSlice == null) {
                    "点按圆环查看分类明细"
                } else {
                    "已选中 ${selectedSlice?.category?.name}，点按其他颜色切换"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            slices.forEachIndexed { index, slice ->
                DonutLegendRow(slice = slice)
                if (index != slices.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun AccountBreakdownCard(slices: List<AccountSlice>) {
    val track = MaterialTheme.zh.chip
    ZhCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(18.dp),
    ) {
        SectionTitle(title = "账户支出")
        Spacer(Modifier.height(12.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
        ) {
            drawRoundRect(
                color = track,
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
            )
            var left = 0f
            slices.forEach { slice ->
                val width = size.width * slice.ratio
                if (width > 1f) {
                    drawRoundRect(
                        color = Color(slice.color),
                        topLeft = Offset(left, 0f),
                        size = Size(width, size.height),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                    )
                }
                left += width
            }
        }
        Spacer(Modifier.height(13.dp))
        slices.forEachIndexed { index, slice ->
            if (index > 0) Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(Color(slice.color), CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = slice.account.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(slice.ratio * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = Formats.money(slice.amountCents),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun TrendCard(
    stats: MonthStats,
    highlightIndex: Int?,
    isCurrentMonth: Boolean,
) {
    var selectedDay by remember(stats.month, stats.dailyExpenses) {
        mutableStateOf<Int?>(null)
    }
    ZhCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(18.dp),
    ) {
        SectionTitle(
            title = "每日支出",
            trailing = "日均 ${Formats.money(stats.dailyAverageCents)}",
        )
        Spacer(Modifier.height(14.dp))
        DailyBarChart(
            values = stats.dailyExpenses,
            highlightIndex = highlightIndex,
            selectedIndex = selectedDay,
            modifier = Modifier.fillMaxWidth(),
            onBarClick = { index ->
                selectedDay = if (selectedDay == index) null else index
            },
        )
        Spacer(Modifier.height(8.dp))
        val selectedCents = selectedDay?.let { stats.dailyExpenses.getOrNull(it) }
        Text(
            text = if (selectedDay != null && selectedCents != null) {
                "${stats.month.monthValue}月${selectedDay!! + 1}日 · ${Formats.money(selectedCents)}"
            } else {
                "点按柱形查看每日支出"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (selectedDay != null) MaterialTheme.zh.gold else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("daily-bar-detail"),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MerchantCard(stats: MonthStats) {
    ZhCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    ) {
        stats.topMerchants.forEachIndexed { index, merchant ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.zh.hairline)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = merchant.merchant,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${merchant.count} 次",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = Formats.money(merchant.amountCents),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun StatsEmpty(month: YearMonth) {
    ZhCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 40.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(MaterialTheme.zh.chip, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "${month.monthValue}月还没哇哦记账据",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "先记几笔。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun compactSlices(slices: List<CategorySlice>): List<CategorySlice> {
    if (slices.size <= 6) return slices
    val top = slices.take(5)
    val rest = slices.drop(5)
    val restAmount = rest.sumOf { it.amountCents }
    val total = slices.sumOf { it.amountCents }
    val group = Category(
        id = "other_group",
        name = "其他 ${rest.size} 类",
        emoji = "•••",
        color = 0xFF9A9488,
        type = TxType.EXPENSE,
    )
    return top + CategorySlice(
        category = group,
        amountCents = restAmount,
        ratio = if (total <= 0L) 0f else restAmount.toFloat() / total,
    )
}
