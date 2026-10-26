package app.zhizhang.ui.aicategory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.zhizhang.data.CustomCategoryStore
import app.zhizhang.data.Categories
import app.zhizhang.data.Formats
import app.zhizhang.ui.components.AddCategoryDialog
import app.zhizhang.ui.components.CategoryPickerSheet
import app.zhizhang.ui.theme.zh
import java.time.Instant
import java.time.ZoneId

@Composable
fun AiCategoryHistoryScreen(onBack: () -> Unit) {
    val viewModel: AiCategoryHistoryViewModel = viewModel()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val context = LocalContext.current
    val categoryStore = remember { CustomCategoryStore(context) }
    var pickerItem by remember { mutableStateOf<AiCategoryHistoryItem?>(null) }
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
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "分类记录",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "只显示 AI 处理过的账单",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (items.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "还没有 AI 分类记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                }
            } else {
                items(items, key = { it.transaction.id }) { item ->
                    val tx = item.transaction
                    val category = Categories.byId(item.categoryId)
                    val date = Instant.ofEpochMilli(tx.occurredAt).atZone(zone)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.zh.chip.copy(alpha = 0.55f))
                            .clickable { pickerItem = item }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(Color(category.color), CircleShape),
                        )
                        Spacer(Modifier.width(9.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tx.merchant.ifBlank { category.name },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${date.monthValue}月${date.dayOfMonth}日 · ${Formats.money(tx.amountCents)} · ${category.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "修改",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    pickerItem?.let { item ->
        CategoryPickerSheet(
            type = item.transaction.txType,
            selectedCategoryId = item.categoryId,
            onSelect = { categoryId ->
                viewModel.updateCategory(item.transaction, categoryId)
                pickerItem = null
            },
            onDismiss = { pickerItem = null },
            onAddCategory = { showAddCategory = true },
        )
    }

    if (showAddCategory && pickerItem != null) {
        val item = pickerItem!!
        AddCategoryDialog(
            initialType = item.transaction.txType,
            onDismiss = { showAddCategory = false },
            onSave = { name, emoji, color, type ->
                val created = categoryStore.add(name, emoji, color, type)
                if (created != null) {
                    Categories.setCustomCategories(categoryStore.load())
                    viewModel.updateCategory(item.transaction, created.id)
                    showAddCategory = false
                    pickerItem = null
                }
            },
        )
    }
}
