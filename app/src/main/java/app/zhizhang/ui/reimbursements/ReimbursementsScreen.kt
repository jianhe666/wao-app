package app.zhizhang.ui.reimbursements

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
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
import app.zhizhang.data.Accounts
import app.zhizhang.data.Categories
import app.zhizhang.data.Formats
import app.zhizhang.data.TransactionEntity
import app.zhizhang.ui.components.AccountPickerSheet
import app.zhizhang.ui.theme.zh
import java.time.Instant
import java.time.ZoneId

@Composable
fun ReimbursementsScreen(onBack: () -> Unit) {
    val viewModel: ReimbursementsViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    var selectedAccountFor by remember { mutableStateOf<TransactionEntity?>(null) }

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
                text = "待报销",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 120.dp),
        ) {
            item(key = "summary") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.zh.chip.copy(alpha = 0.7f))
                        .border(
                            1.dp,
                            MaterialTheme.zh.gold.copy(alpha = 0.28f),
                            RoundedCornerShape(22.dp),
                        )
                        .padding(18.dp),
                ) {
                    Text(
                        text = "待报销金额",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = Formats.money(state.pendingCents),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${state.pending.size} 笔支出等待报销",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(22.dp))
                SectionTitle("待报销")
            }

            if (state.pending.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "没有待报销的支出。\n在记账时把「报销」打开，或通过 AI 说“这笔可以报销”。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            } else {
                items(state.pending, key = { "pending-${it.id}" }) { transaction ->
                    ReimbursementRow(
                        transaction = transaction,
                        zone = zone,
                        onComplete = { selectedAccountFor = transaction },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (state.completed.isNotEmpty()) {
                item(key = "completed-title") {
                    Spacer(Modifier.height(20.dp))
                    SectionTitle("已报销")
                }
                items(state.completed, key = { "done-${it.original.id}" }) { item ->
                    CompletedRow(item = item, zone = zone)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    selectedAccountFor?.let { transaction ->
        AccountPickerSheet(
            selectedAccountId = transaction.accountId,
            onSelect = { accountId ->
                viewModel.complete(transaction.id, accountId)
                selectedAccountFor = null
            },
            onDismiss = { selectedAccountFor = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun ReimbursementRow(
    transaction: TransactionEntity,
    zone: ZoneId,
    onComplete: () -> Unit,
) {
    val category = Categories.byId(transaction.categoryId)
    val date = Instant.ofEpochMilli(transaction.occurredAt).atZone(zone)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.zh.chip.copy(alpha = 0.5f))
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(androidx.compose.ui.graphics.Color(category.color), CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.merchant.ifBlank { category.name },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${date.monthValue}月${date.dayOfMonth}日 · ${category.name} · 待报销",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = Formats.money(transaction.amountCents),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        TextButton(onClick = onComplete) {
            Text("已报销", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CompletedRow(
    item: CompletedReimbursement,
    zone: ZoneId,
) {
    val original = item.original
    val category = Categories.byId(original.categoryId)
    val date = Instant.ofEpochMilli(original.occurredAt).atZone(zone)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.zh.chip.copy(alpha = 0.36f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = original.merchant.ifBlank { category.name },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${date.monthValue}月${date.dayOfMonth}日 · 已报销" +
                    item.income?.let { " · ${Accounts.byId(it.accountId).name}" }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "+${Formats.money(original.amountCents)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.zh.income,
        )
    }
}
