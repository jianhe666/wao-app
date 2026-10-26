package app.zhizhang.ui.rules

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.zhizhang.data.Categories
import app.zhizhang.data.CategorySuggestion
import app.zhizhang.data.MerchantRuleEntity
import app.zhizhang.ui.components.CategoryPickerSheet
import app.zhizhang.ui.components.ConfirmDialog
import app.zhizhang.ui.components.MerchantBadge
import app.zhizhang.ui.components.ZhCard
import app.zhizhang.ui.theme.zh

@Composable
fun RulesScreen(onBack: () -> Unit) {
    val viewModel: RulesViewModel = viewModel()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val classifying by viewModel.classifying.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var pickerRule by remember { mutableStateOf<MerchantRuleEntity?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showAllRules by remember { mutableStateOf(false) }
    val visibleRules = if (showAllRules) rules else rules.take(5)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
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
                text = "分类学习",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${rules.size} 条",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 14.dp),
            )
        }
        Text(
            text = "改一次，就会记住。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 8.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item(key = "ai-panel") {
                AiClassifyPanel(
                    classifying = classifying,
                    message = message,
                    onClassify = viewModel::classifyWithAi,
                )
            }
            if (suggestions.isNotEmpty()) {
                item(key = "suggestion-title") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI 待确认 · ${suggestions.size} 个",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = "不确定的名称留给你确认",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = viewModel::confirmAllSuggestions) {
                            Text("全部确认")
                        }
                    }
                }
                items(suggestions, key = { "suggestion-${it.merchantKey}" }) { suggestion ->
                    SuggestionRow(
                        suggestion = suggestion,
                        onConfirm = { viewModel.confirmSuggestion(suggestion) },
                        onDismiss = { viewModel.dismissSuggestion(suggestion) },
                    )
                }
            }
            item(key = "rules-title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "已学习规则 · ${rules.size} 条",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    if (rules.size > 5) {
                        TextButton(onClick = { showAllRules = !showAllRules }) {
                            Text(if (showAllRules) "收起" else "展开全部")
                        }
                    }
                }
            }
            if (rules.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "还没有学习记录。修改一次分类后，智账会记住。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            } else {
                items(visibleRules, key = { it.merchantKey }) { rule ->
                    RuleRow(
                        rule = rule,
                        onEdit = { pickerRule = rule },
                        onDelete = { viewModel.delete(rule.merchantKey) },
                    )
                }
                item(key = "clear") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TextButton(onClick = { showClearDialog = true }) {
                            Text(
                                text = "清空学习记录",
                                color = MaterialTheme.zh.expense,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    pickerRule?.let { rule ->
        val category = Categories.byId(rule.categoryId)
        CategoryPickerSheet(
            type = category.type,
            selectedCategoryId = category.id,
            onSelect = { newCategoryId ->
                viewModel.updateCategory(rule.merchantKey, newCategoryId)
                pickerRule = null
            },
            onDismiss = { pickerRule = null },
        )
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = "清空学习记录？",
            body = "账目不会受影响。",
            confirmText = "清空",
            destructive = true,
            onDismiss = { showClearDialog = false },
            onConfirm = {
                viewModel.clearAll()
                showClearDialog = false
            },
        )
    }
}

@Composable
private fun AiClassifyPanel(
    classifying: Boolean,
    message: String?,
    onClassify: () -> Unit,
) {
    ZhCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI 自动分类",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "按商户名称建议分类；已学习的商户自动跳过，高置信度自动应用，不确定的留给你确认。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onClassify,
                enabled = !classifying,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
            ) {
                if (classifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 1.6.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("开始")
                }
            }
        }
        message?.let {
            Spacer(Modifier.size(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = if (it.contains("失败") || it.contains("先")) {
                    MaterialTheme.zh.gold
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.size(6.dp))
        Text(
            text = "已学习检测会跳过已有规则或已分类的商户。仅发送商户名称和分类列表，不发送金额、订单号或原始通知。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun SuggestionRow(
    suggestion: CategorySuggestion,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val category = Categories.byId(suggestion.categoryId)
    ZhCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MerchantBadge(
                text = category.name,
                color = Color(category.color),
                size = 32.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.merchant,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${category.name} · ${(suggestion.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onConfirm) { Text("确认") }
            TextButton(onClick = onDismiss) {
                Text("忽略", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RuleRow(
    rule: MerchantRuleEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val category = Categories.byId(rule.categoryId)
    ZhCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MerchantBadge(
                text = category.name,
                color = Color(category.color),
                size = 32.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.merchantKey,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "修改分类",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除规则",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
