package app.zhizhang.ui.review

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.zhizhang.data.Categories
import app.zhizhang.data.Formats
import app.zhizhang.data.TransactionEntity
import app.zhizhang.data.TransactionStatus
import app.zhizhang.ui.theme.zh
import java.time.Instant
import java.time.ZoneId

@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onManualAdd: () -> Unit = {},
) {
    val viewModel: ReviewViewModel = viewModel()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    var showConfirmAll by remember { mutableStateOf(false) }

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
            Text(
                text = "待确认",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            if (items.isNotEmpty()) {
                TextButton(onClick = { showConfirmAll = true }) {
                    Text("全部确认")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item(key = "intro") {
                Text(
                    text = "这些是通知自动识别出的交易，但金额、商户或状态不够确定。确认后会作为正常流水保留。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (items.isEmpty() && events.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "没有待确认的交易。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            } else {
                items(items, key = { it.id }) { transaction ->
                    ReviewRow(
                        transaction = transaction,
                        zone = zone,
                        onConfirm = { viewModel.confirm(transaction.id) },
                        onEdit = { onEdit(transaction.id) },
                        onDelete = { viewModel.delete(transaction.id) },
                    )
                }
            }
            if (events.isNotEmpty()) {
                item(key = "events-title") {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "未识别的通知",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                items(events, key = { "event-${it.id}" }) { event ->
                    NotificationEventRow(
                        event = event,
                        onManualAdd = onManualAdd,
                        onDismiss = { viewModel.dismissEvent(event.id) },
                    )
                }
            }
        }
    }

    if (showConfirmAll) {
        AlertDialog(
            onDismissRequest = { showConfirmAll = false },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            title = { Text("确认全部 ${items.size} 笔？") },
            text = {
                Text(
                    text = "这些交易会保留为正常流水，待确认标记会被清除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmAll()
                        showConfirmAll = false
                    },
                ) { Text("全部确认") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmAll = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ReviewRow(
    transaction: TransactionEntity,
    zone: ZoneId,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val category = Categories.byId(transaction.categoryId)
    val date = Instant.ofEpochMilli(transaction.occurredAt).atZone(zone)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.zh.chip.copy(alpha = 0.55f))
            .padding(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(androidx.compose.ui.graphics.Color(category.color), androidx.compose.foundation.shape.CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.merchant.ifBlank { "未识别商户" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${date.monthValue}月${date.dayOfMonth}日 · ${category.name} · " +
                        "${transaction.status.label} · 置信度 ${(transaction.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = Formats.money(transaction.amountCents),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (transaction.transactionStatus == TransactionStatus.PENDING.name ||
            transaction.transactionStatus == TransactionStatus.UNKNOWN.name
        ) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onConfirm) { Text("确认无误") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onEdit) { Text("编辑") }
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}

@Composable
private fun NotificationEventRow(
    event: app.zhizhang.data.NotificationEventEntity,
    onManualAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.zh.chip.copy(alpha = 0.45f))
            .padding(13.dp),
    ) {
        Text(
            text = event.appLabel.ifBlank { event.packageName },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = event.parseError?.let { "未完成：$it" } ?: "未能识别出完整交易",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.zh.gold,
        )
        if (event.title.isNotBlank() || event.body.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOf(event.title, event.body).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onManualAdd) { Text("手动记一笔") }
            TextButton(onClick = onDismiss) { Text("忽略") }
        }
    }
}
