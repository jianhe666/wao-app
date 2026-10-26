package app.zhizhang.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.text.Collator
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.zhizhang.data.Accounts
import app.zhizhang.data.Categories
import app.zhizhang.data.Formats
import app.zhizhang.data.TransactionEntity
import app.zhizhang.data.TxType
import app.zhizhang.domain.MergeCandidate
import app.zhizhang.ui.components.ConfirmDialog
import app.zhizhang.ui.components.CategoryBadge
import app.zhizhang.ui.components.GlassCircleButton
import app.zhizhang.ui.components.HeroAmount
import app.zhizhang.ui.components.MonthSwitcher
import app.zhizhang.ui.components.ThinProgressBar
import app.zhizhang.ui.components.ZhCard
import app.zhizhang.ui.components.ZhMonthPickerDialog
import app.zhizhang.ui.theme.AmountRow
import app.zhizhang.ui.theme.zh
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenReimbursements: () -> Unit = {},
    onOpenReview: () -> Unit = {},
) {
    val viewModel: HomeViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var pendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(LedgerFilter()) }

    val filteredTransactions = remember(state.groups, filter) {
        state.groups
            .flatMap { it.transactions }
            .filter { filter.type == null || it.txType == filter.type }
            .filter { filter.categoryIds.isEmpty() || it.categoryId in filter.categoryIds }
    }
    val filteredGroups = remember(state.groups, filteredTransactions) {
        filteredTransactions
            .groupBy {
                Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate()
            }
            .entries
            .sortedByDescending { it.key }
            .map { (date, list) ->
                TransactionGroup(
                    date = date,
                    transactions = list.sortedByDescending { it.occurredAt },
                    expenseCents = list.filter { it.txType == TxType.EXPENSE }.sumOf { it.amountCents },
                    incomeCents = list.filter { it.txType == TxType.INCOME }.sumOf { it.amountCents },
                )
            }
    }
    val collator = remember { Collator.getInstance(Locale.CHINA) }
    val sortedTransactions = remember(filteredTransactions, filter.sort, collator) {
        val base = when (filter.sort) {
            LedgerSort.TIME -> filteredTransactions.sortedBy { it.occurredAt }
            LedgerSort.AMOUNT -> filteredTransactions.sortedBy { it.amountCents }
            LedgerSort.NAME -> filteredTransactions.sortedWith { first, second ->
                collator.compare(
                    first.merchant.ifBlank { Categories.byId(first.categoryId).name },
                    second.merchant.ifBlank { Categories.byId(second.categoryId).name },
                )
            }
        }
        if (filter.ascending) base else base.reversed()
    }
    val showGrouped = filter.sort == LedgerSort.TIME && !filter.ascending

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 120.dp),
    ) {
        item(key = "header") {
            LedgerHeader(
                onAdd = onAdd,
                filterActive = filter.isActive,
                onFilter = { showFilter = true },
            )
            Spacer(Modifier.height(18.dp))
            MonthSwitcher(
                month = state.month,
                onPrevious = { viewModel.shiftMonth(-1) },
                onNext = { viewModel.shiftMonth(1) },
                canGoNext = state.month.isBefore(YearMonth.now(zone)),
                compact = true,
                onMonthClick = { showMonthPicker = true },
            )
            Spacer(Modifier.height(18.dp))
            SummaryBlock(state)
            if (state.pendingReimbursementCount > 0) {
                Spacer(Modifier.height(12.dp))
                ReimbursementBanner(
                    count = state.pendingReimbursementCount,
                    amountCents = state.pendingReimbursementCents,
                    onClick = onOpenReimbursements,
                )
            }
            if (state.reviewCount > 0) {
                Spacer(Modifier.height(10.dp))
                ReviewBanner(count = state.reviewCount, onClick = onOpenReview)
            }
            state.mergeCandidates.firstOrNull()?.let { candidate ->
                Spacer(Modifier.height(10.dp))
                MergeSuggestionCard(
                    candidate = candidate,
                    onMerge = {
                        viewModel.mergeDuplicate(
                            candidate.automatic.id,
                            candidate.userRecord.id,
                        )
                    },
                    onDismiss = { viewModel.dismissMerge(candidate) },
                )
            }
            if (filter.isActive) {
                Spacer(Modifier.height(12.dp))
                ActiveFilterRow(
                    filter = filter,
                    resultCount = filteredTransactions.size,
                    onClear = { filter = LedgerFilter() },
                )
            }
            Spacer(Modifier.height(22.dp))
        }

        when {
            state.loading -> item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 1.8.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            filteredTransactions.isEmpty() -> item(key = "empty") {
                if (filter.isActive) {
                    FilteredEmpty(onClear = { filter = LedgerFilter() })
                } else {
                    LedgerEmpty(onAdd = onAdd)
                }
            }

            !showGrouped -> item(key = "flat") {
                FlatLedgerCard(
                    transactions = sortedTransactions,
                    zone = zone,
                    onEdit = onEdit,
                    onDelete = { pendingDelete = it },
                )
            }

            else -> itemsIndexed(
                items = filteredGroups,
                key = { _, group -> group.date.toString() },
            ) { index, group ->
                DayHeader(group = group, today = today)
                Spacer(Modifier.height(8.dp))
                DayCard(
                    group = group,
                    zone = zone,
                    onEdit = onEdit,
                    onDelete = { pendingDelete = it },
                )
                if (index != filteredGroups.lastIndex) {
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }

    if (showMonthPicker) {
        ZhMonthPickerDialog(
            selectedMonth = state.month,
            maxMonth = YearMonth.now(zone),
            onDismiss = { showMonthPicker = false },
            onSelect = { month ->
                viewModel.selectMonth(month)
                filter = LedgerFilter()
                showMonthPicker = false
            },
        )
    }

    if (showFilter) {
        LedgerFilterSheet(
            initial = filter,
            onDismiss = { showFilter = false },
            onApply = {
                filter = it
                showFilter = false
            },
        )
    }

    pendingDelete?.let { transaction ->
        ConfirmDialog(
            title = "删除这笔账目？",
            body = "删除后无法恢复。",
            confirmText = "删除",
            destructive = true,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                viewModel.deleteTransaction(transaction.id)
                pendingDelete = null
            },
        )
    }
}

@Composable
private fun MergeSuggestionCard(
    candidate: MergeCandidate,
    onMerge: () -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val auto = candidate.automatic
    val user = candidate.userRecord
    fun timeOf(transaction: TransactionEntity): String {
        val value = Instant.ofEpochMilli(transaction.occurredAt).atZone(zone)
        return "${value.monthValue}月${value.dayOfMonth}日 %02d:%02d".format(value.hour, value.minute)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.zh.gold.copy(alpha = 0.09f))
            .border(
                1.dp,
                MaterialTheme.zh.gold.copy(alpha = 0.3f),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Text(
            text = "发现可能重复的账目",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.zh.gold,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = "自动记账 · ${auto.merchant.ifBlank { "未命名" }} ${Formats.signedMoney(auto.amountCents, auto.txType)} · ${timeOf(auto)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "对话记录 · ${user.merchant.ifBlank { "未命名" }} ${Formats.signedMoney(user.amountCents, user.txType)} · ${timeOf(user)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "金额相同、时间接近",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text("保留两笔", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onMerge) { Text("合并") }
        }
    }
}

@Composable
private fun ActiveFilterRow(
    filter: LedgerFilter,
    resultCount: Int,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.zh.chip.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buildString {
                append("筛选中")
                filter.type?.let { append(" · ${it.label}") }
                if (filter.categoryIds.isNotEmpty()) {
                    val names = filter.categoryIds.map { Categories.byId(it).name }
                    append(" · ")
                    append(if (names.size == 1) names.first() else "${names.first()} 等 ${names.size} 类")
                }
                if (filter.sort != LedgerSort.TIME || filter.ascending) {
                    append(" · ${filter.sort.label}")
                    append(if (filter.ascending) "正序" else "倒序")
                }
                append(" · $resultCount 笔")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "清除",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onClear)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun FlatLedgerCard(
    transactions: List<TransactionEntity>,
    zone: ZoneId,
    onEdit: (Long) -> Unit,
    onDelete: (TransactionEntity) -> Unit,
) {
    ZhCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        transactions.forEachIndexed { index, transaction ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp, end = 12.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.zh.hairline.copy(alpha = 0.3f),
                )
            }
            TransactionRow(
                transaction = transaction,
                zone = zone,
                showDate = true,
                onEdit = { onEdit(transaction.id) },
                onDelete = { onDelete(transaction) },
            )
        }
    }
}

private enum class LedgerSort(val label: String) {
    TIME("时间"),
    AMOUNT("金额"),
    NAME("首字母"),
}

private data class LedgerFilter(
    val type: TxType? = null,
    val categoryIds: Set<String> = emptySet(),
    val sort: LedgerSort = LedgerSort.TIME,
    val ascending: Boolean = false,
) {
    val isActive: Boolean
        get() = type != null || categoryIds.isNotEmpty() || sort != LedgerSort.TIME || ascending
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerFilterSheet(
    initial: LedgerFilter,
    onDismiss: () -> Unit,
    onApply: (LedgerFilter) -> Unit,
) {
    var type by remember(initial) { mutableStateOf(initial.type) }
    var categoryIds by remember(initial) { mutableStateOf(initial.categoryIds) }
    var sort by remember(initial) { mutableStateOf(initial.sort) }
    var ascending by remember(initial) { mutableStateOf(initial.ascending) }
    val categories = if (type == null) Categories.all else Categories.forType(type!!)
    LaunchedEffect(type) {
        val valid = categories.map { it.id }.toSet()
        categoryIds = categoryIds.filterTo(mutableSetOf()) { it in valid }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(
                text = "筛选流水",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "收支",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChoice("全部", type == null, Modifier.testTag("filter-type-all")) { type = null }
                FilterChoice("仅支出", type == TxType.EXPENSE, Modifier.testTag("filter-type-expense")) { type = TxType.EXPENSE }
                FilterChoice("仅收入", type == TxType.INCOME, Modifier.testTag("filter-type-income")) { type = TxType.INCOME }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "分类（可选）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(7.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                FilterCategoryRow(
                    label = "全部分类",
                    selected = categoryIds.isEmpty(),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("filter-category-all"),
                    onClick = { categoryIds = emptySet() },
                )
                categories.forEach { category ->
                    FilterCategoryRow(
                        label = if (type == null) {
                            "${category.name} · ${category.type.label}"
                        } else {
                            category.name
                        },
                        selected = category.id in categoryIds,
                        color = Color(category.color),
                        modifier = Modifier.testTag("filter-category-${category.id}"),
                        onClick = {
                            categoryIds = if (category.id in categoryIds) {
                                categoryIds - category.id
                            } else {
                                categoryIds + category.id
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "排序",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                LedgerSort.entries.forEach { option ->
                    FilterChoice(option.label, sort == option, Modifier.testTag("filter-sort-${option.name}")) { sort = option }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChoice(
                    text = "倒序",
                    selected = !ascending,
                    modifier = Modifier.testTag("filter-order-desc"),
                ) { ascending = false }
                FilterChoice(
                    text = "正序",
                    selected = ascending,
                    modifier = Modifier.testTag("filter-order-asc"),
                ) { ascending = true }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        type = null
                        categoryIds = emptySet()
                        sort = LedgerSort.TIME
                        ascending = false
                    },
                ) { Text("重置") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onApply(LedgerFilter(type, categoryIds, sort, ascending)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("filter-apply"),
                ) { Text("应用筛选") }
            }
        }
    }
}

@Composable
private fun FilterChoice(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.zh.chip.copy(alpha = 0.62f),
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                else MaterialTheme.zh.hairline.copy(alpha = 0.3f),
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun FilterCategoryRow(
    label: String,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) color.copy(alpha = 0.1f) else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) color else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text(
                text = "已选",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LedgerHeader(
    onAdd: () -> Unit,
    filterActive: Boolean,
    onFilter: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "流水",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (filterActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else MaterialTheme.zh.chip.copy(alpha = 0.55f),
                )
                .border(
                    1.dp,
                    if (filterActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    else MaterialTheme.zh.hairline.copy(alpha = 0.3f),
                    RoundedCornerShape(50),
                )
                .clickable(onClick = onFilter)
                .testTag("ledger-filter")
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "筛选",
                style = MaterialTheme.typography.labelSmall,
                color = if (filterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (filterActive) {
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
        }
        Spacer(Modifier.width(9.dp))
        GlassCircleButton(
            modifier = Modifier.size(38.dp),
            onClick = onAdd,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "记一笔",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ReimbursementBanner(
    count: Int,
    amountCents: Long,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.zh.chip.copy(alpha = 0.62f))
            .border(
                1.dp,
                MaterialTheme.zh.gold.copy(alpha = 0.28f),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "报销",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.zh.gold,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = "$count 笔待报销 · ${Formats.money(amountCents)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "查看",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ReviewBanner(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.58f))
            .border(
                1.dp,
                MaterialTheme.zh.gold.copy(alpha = 0.25f),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "待确认",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.zh.gold,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = "$count 笔自动识别交易需要确认",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "查看",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<app.zhizhang.data.Category>,
    selectedCategoryId: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        FilterChip(
            text = "全部",
            color = MaterialTheme.colorScheme.primary,
            selected = selectedCategoryId == null,
            onClick = { onSelect(null) },
            modifier = Modifier.testTag("filter-all"),
        )
        categories.forEach { category ->
            FilterChip(
                text = category.name,
                color = Color(category.color),
                selected = selectedCategoryId == category.id,
                onClick = { onSelect(category.id) },
                modifier = Modifier.testTag("filter-${category.id}"),
            )
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color.copy(alpha = 0.15f) else MaterialTheme.zh.chip.copy(alpha = 0.64f))
            .border(
                1.dp,
                if (selected) color.copy(alpha = 0.55f) else MaterialTheme.zh.hairline.copy(alpha = 0.35f),
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun FilteredEmpty(onClear: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "这个分类暂时没有账目",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "查看全部",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onClear)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SummaryBlock(state: HomeUiState) {
    Text(
        text = "本月支出",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(2.dp))
    HeroAmount(cents = state.expenseCents)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "收入 ${Formats.money(state.incomeCents)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.zh.income,
        )
        Box(
            Modifier
                .padding(horizontal = 8.dp)
                .size(2.5.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), CircleShape),
        )
        Text(
            text = "结余 ${Formats.money(state.balanceCents)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state.budgetCents > 0L) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "预算",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(state.budgetUsedRatio * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (state.budgetUsedRatio >= 0.9f) {
                    MaterialTheme.zh.expense
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        ThinProgressBar(
            progress = state.budgetProgress,
            modifier = Modifier.fillMaxWidth(),
            color = if (state.budgetUsedRatio >= 0.9f) {
                MaterialTheme.zh.expense
            } else {
                MaterialTheme.colorScheme.primary
            },
            height = 2.dp,
        )
    }
}

@Composable
private fun LedgerEmpty(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "这个月还没有账目",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "从第一笔开始。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "记一笔",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onAdd)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun DayHeader(group: TransactionGroup, today: LocalDate) {
    val prefix = when (group.date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> ""
    }
    val weekday = group.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
    val label = buildString {
        if (prefix.isNotEmpty()) {
            append(prefix)
            append(" · ")
        }
        append(group.date.monthValue)
        append("月")
        append(group.date.dayOfMonth)
        append("日")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "  $weekday",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        )
        Spacer(Modifier.weight(1f))
        if (group.expenseCents > 0L) {
            Text(
                text = Formats.money(group.expenseCents),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (group.incomeCents > 0L) {
            Text(
                text = "+${Formats.money(group.incomeCents)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.zh.income,
            )
        }
    }
}

@Composable
private fun DayCard(
    group: TransactionGroup,
    zone: ZoneId,
    onEdit: (Long) -> Unit,
    onDelete: (TransactionEntity) -> Unit,
) {
    ZhCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        group.transactions.forEachIndexed { index, transaction ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp, end = 12.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.zh.hairline.copy(alpha = 0.3f),
                )
            }
            TransactionRow(
                transaction = transaction,
                zone = zone,
                onEdit = { onEdit(transaction.id) },
                onDelete = { onDelete(transaction) },
            )
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: TransactionEntity,
    zone: ZoneId,
    showDate: Boolean = false,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val category = Categories.byId(transaction.categoryId)
    val account = Accounts.byId(transaction.accountId)
    val time = Instant.ofEpochMilli(transaction.occurredAt).atZone(zone).toLocalTime()
    val title = transaction.merchant.ifBlank { category.name }
    val isIncome = transaction.txType == TxType.INCOME
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(
            category = category,
            size = 34.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = if (showDate) {
                    "${Instant.ofEpochMilli(transaction.occurredAt).atZone(zone).monthValue}月" +
                        "${Instant.ofEpochMilli(transaction.occurredAt).atZone(zone).dayOfMonth}日 · " +
                        "${category.name} · ${account.name}"
                } else {
                    "${category.name} · ${account.name}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Formats.signedMoney(transaction.amountCents, transaction.txType),
                style = AmountRow,
                color = if (isIncome) MaterialTheme.zh.income else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = "%02d:%02d".format(time.hour, time.minute),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多操作",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(17.dp),
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                DropdownMenuItem(
                    text = { Text("编辑") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        showMenu = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.zh.expense) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.zh.expense,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                )
            }
        }
    }
}
