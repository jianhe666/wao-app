package app.zhizhang.ui.aicategory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.zhizhang.data.AiTransactionCategorySuggestion
import app.zhizhang.data.CustomCategoryStore
import app.zhizhang.data.Categories
import app.zhizhang.data.Formats
import app.zhizhang.data.TransactionEntity
import app.zhizhang.ui.components.AddCategoryDialog
import app.zhizhang.ui.components.CategoryPickerSheet
import app.zhizhang.ui.components.liquidGlass
import app.zhizhang.ui.theme.zh
import java.time.Instant
import java.time.ZoneId

@Composable
fun AiCategoryScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit = {},
) {
    val viewModel: AiCategoryViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val context = LocalContext.current
    val categoryStore = remember { CustomCategoryStore(context) }
    var pickerSuggestion by remember { mutableStateOf<AiTransactionCategorySuggestion?>(null) }
    var showAddCategory by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = "AI 智能分类",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Beta",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenHistory) {
                Text("分类记录")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item(key = "intro") {
                Column {
                    Text(
                        text = "按每笔账单的商户和备注分类，相同商户的不同消费也可以分到不同类别。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "ranges") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    AiCategoryRange.entries.forEach { range ->
                        RangeChip(
                            text = range.label,
                            selected = state.range == range,
                            onClick = { viewModel.selectRange(range) },
                        )
                    }
                }
            }
            item(key = "action") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.message ?: "正在读取账单…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = viewModel::analyze,
                            enabled = !state.loading && !state.analyzing && state.candidates.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            if (state.analyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 1.6.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text("开始分类")
                            }
                        }
                    }
                    state.error?.let {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.zh.expense,
                        )
                    }
                }
            }
            if (state.pending.isNotEmpty()) {
                item(key = "pending-title") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI 待确认 · ${state.pending.size} 笔",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = "点确认后会直接修改这笔账单的分类",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = viewModel::cancelAll) {
                            Text("全部取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = viewModel::confirmAll) {
                            Text("全部确认")
                        }
                    }
                }
                items(state.pending, key = { "pending-${it.transaction.id}" }) { suggestion ->
                    PendingCategoryRow(
                        suggestion = suggestion,
                        zone = zone,
                        onEditCategory = { pickerSuggestion = suggestion },
                        onConfirm = { viewModel.confirm(suggestion) },
                        onIgnore = { viewModel.cancel(suggestion) },
                    )
                }
            }
            if (state.candidates.isNotEmpty() && state.pending.isEmpty() && state.appliedCount > 0) {
                item(key = "applied") {
                    Text(
                        text = "本地已按确认记录分类 ${state.appliedCount} 笔",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.zh.income,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }

    pickerSuggestion?.let { suggestion ->
        CategoryPickerSheet(
            type = suggestion.transaction.txType,
            selectedCategoryId = suggestion.categoryId,
            onSelect = { categoryId ->
                viewModel.changeSuggestionCategory(suggestion, categoryId)
                pickerSuggestion = null
            },
            onDismiss = { pickerSuggestion = null },
            onAddCategory = { showAddCategory = true },
        )
    }

    if (showAddCategory && pickerSuggestion != null) {
        val suggestion = pickerSuggestion!!
        AddCategoryDialog(
            initialType = suggestion.transaction.txType,
            onDismiss = { showAddCategory = false },
            onSave = { name, emoji, color, type ->
                val created = categoryStore.add(name, emoji, color, type)
                if (created != null) {
                    app.zhizhang.data.Categories.setCustomCategories(categoryStore.load())
                    viewModel.changeSuggestionCategory(suggestion, created.id)
                    showAddCategory = false
                    pickerSuggestion = null
                }
            },
        )
    }
}

@Composable
private fun RangeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .liquidGlass(
                shape = RoundedCornerShape(50),
                tint = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.zh.glassTop.copy(alpha = 0.12f),
                lensHeight = 5.dp,
                lensAmount = 12.dp,
                blurRadius = 2.dp,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun PendingCategoryRow(
    suggestion: AiTransactionCategorySuggestion,
    zone: ZoneId,
    onEditCategory: () -> Unit,
    onConfirm: () -> Unit,
    onIgnore: () -> Unit,
) {
    val tx = suggestion.transaction
    val oldCategory = Categories.byId(tx.categoryId)
    val newCategory = Categories.byId(suggestion.categoryId)
    val date = Instant.ofEpochMilli(tx.occurredAt).atZone(zone)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(16.dp),
                tint = MaterialTheme.zh.glassTop.copy(alpha = 0.10f),
                lensHeight = 8.dp,
                lensAmount = 18.dp,
                blurRadius = 3.dp,
            )
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(Color(newCategory.color), CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.merchant.ifBlank { oldCategory.name },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${date.monthValue}月${date.dayOfMonth}日 · ${Formats.money(tx.amountCents)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${oldCategory.name} → ${newCategory.name}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onEditCategory)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.size(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "置信度 ${(suggestion.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.zh.gold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onConfirm) { Text("确认") }
            TextButton(onClick = onIgnore) {
                Text("取消分类", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
