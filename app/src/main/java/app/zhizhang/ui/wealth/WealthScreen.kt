package app.zhizhang.ui.wealth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.zhizhang.data.Account
import app.zhizhang.data.Accounts
import app.zhizhang.data.Formats
import app.zhizhang.data.Subscription
import app.zhizhang.data.SubscriptionCycle
import app.zhizhang.ui.components.GlassAlertDialog
import app.zhizhang.ui.components.ZhDatePickerDialog
import app.zhizhang.ui.components.liquidGlass
import app.zhizhang.ui.theme.zh
import java.time.Instant
import java.time.ZoneId

@Composable
fun WealthScreen(onBack: () -> Unit) {
    val viewModel: WealthViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingAccount by remember { mutableStateOf<Account?>(null) }
    var editingSubscription by remember { mutableStateOf<Subscription?>(null) }
    var showSubscriptionEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Subscription?>(null) }
    val zone = remember { ZoneId.systemDefault() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "财富",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    shape = RoundedCornerShape(22.dp),
                    tint = MaterialTheme.zh.glassTop.copy(alpha = 0.16f),
                    lensHeight = 10.dp,
                    lensAmount = 22.dp,
                    blurRadius = 5.dp,
                )
                .padding(17.dp),
        ) {
            Text("总财富", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = Formats.money(state.totalCents),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text("${state.accounts.size} 个账户 · ${state.subscriptions.size} 个订阅", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(20.dp))
        Text("账户余额", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        state.accounts.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(16.dp),
                        tint = MaterialTheme.zh.glassTop.copy(alpha = 0.10f),
                        lensHeight = 8.dp,
                        lensAmount = 18.dp,
                        blurRadius = 3.dp,
                    )
                    .clickable { editingAccount = item.account }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.account.emoji, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(10.dp))
                Text(item.account.name, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                Text(Formats.money(item.currentCents), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("订阅", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { editingSubscription = null; showSubscriptionEditor = true },
                modifier = Modifier.liquidGlass(
                    shape = RoundedCornerShape(50),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    lensHeight = 6.dp,
                    lensAmount = 14.dp,
                    blurRadius = 2.dp,
                ),
            ) { Text("+ 添加") }
        }
        if (state.subscriptions.isEmpty()) {
            Text("还没有订阅记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.subscriptions.forEach { subscription ->
            val date = Instant.ofEpochMilli(subscription.nextPaymentAt).atZone(zone)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(16.dp),
                        tint = MaterialTheme.zh.glassTop.copy(alpha = 0.10f),
                        lensHeight = 8.dp,
                        lensAmount = 18.dp,
                        blurRadius = 3.dp,
                    )
                    .clickable {
                        editingSubscription = subscription
                        showSubscriptionEditor = true
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(subscription.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                    Text(
                        "${subscription.cycle.label} · ${Accounts.byId(subscription.accountId).name} · ${date.monthValue}月${date.dayOfMonth}日下次",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(Formats.money(subscription.amountCents), color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = { pendingDelete = subscription }) {
                    Icon(Icons.Default.Delete, "删除订阅", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(34.dp))
    }

    editingAccount?.let { account ->
        AccountEditDialog(
            account = account,
            currentBalance = state.accounts.firstOrNull { it.account.id == account.id }?.currentCents ?: 0L,
            onDismiss = { editingAccount = null },
            onSave = { name, balance ->
                viewModel.editAccount(account, name, balance)
                editingAccount = null
            },
        )
    }

    if (showSubscriptionEditor) {
        SubscriptionEditorDialog(
            initial = editingSubscription,
            accounts = Accounts.all,
            nextDate = viewModel::nextPayment,
            onDismiss = { showSubscriptionEditor = false },
            onSave = { subscription ->
                viewModel.saveSubscription(subscription)
                showSubscriptionEditor = false
            },
        )
    }

    pendingDelete?.let { subscription ->
        GlassAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这个订阅？") },
            text = { Text("「${subscription.name}」会从订阅列表移除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSubscription(subscription.id)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.zh.expense) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun AccountEditDialog(
    account: Account,
    currentBalance: Long,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
) {
    var name by remember(account.id) { mutableStateOf(account.name) }
    var balance by remember(account.id) { mutableStateOf(Formats.centsToInput(currentBalance)) }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑账户") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("账户名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    label = { Text("当前余额（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, Formats.amountInputToCents(balance)) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SubscriptionEditorDialog(
    initial: Subscription?,
    accounts: List<Account>,
    nextDate: (SubscriptionCycle, Long) -> Long,
    onDismiss: () -> Unit,
    onSave: (Subscription) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var amount by remember(initial?.id) {
        mutableStateOf(initial?.let { Formats.centsToInput(it.amountCents) }.orEmpty())
    }
    var cycle by remember(initial?.id) { mutableStateOf(initial?.cycle ?: SubscriptionCycle.MONTHLY) }
    var accountId by remember(initial?.id) { mutableStateOf(initial?.accountId ?: "wechat") }
    var nextPaymentAt by remember(initial?.id) {
        val base = initial?.nextPaymentAt ?: System.currentTimeMillis()
        mutableStateOf(nextDate(cycle, base))
    }
    var showDatePicker by remember { mutableStateOf(false) }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加订阅" else "编辑订阅") },
        text = {
            Column(modifier = Modifier.height(390.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("订阅名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("订阅金额（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text("订阅方式", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SubscriptionCycle.entries.filter { it != SubscriptionCycle.CUSTOM }.forEach { option ->
                        TextButton(
                            onClick = {
                                cycle = option
                                nextPaymentAt = nextDate(option, System.currentTimeMillis())
                            },
                            modifier = Modifier.liquidGlass(
                                shape = RoundedCornerShape(50),
                                tint = if (cycle == option) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else MaterialTheme.zh.glassTop.copy(alpha = 0.10f),
                                lensHeight = 5.dp,
                                lensAmount = 12.dp,
                                blurRadius = 2.dp,
                            ),
                        ) { Text(option.label) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("支付账户", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(5.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    accounts.forEach { account ->
                        TextButton(
                            onClick = { accountId = account.id },
                            modifier = Modifier.liquidGlass(
                                shape = RoundedCornerShape(50),
                                tint = if (accountId == account.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else MaterialTheme.zh.glassTop.copy(alpha = 0.10f),
                                lensHeight = 5.dp,
                                lensAmount = 12.dp,
                                blurRadius = 2.dp,
                            ),
                        ) { Text(account.emoji + account.name) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.liquidGlass(
                        shape = RoundedCornerShape(14.dp),
                        tint = MaterialTheme.zh.glassTop.copy(alpha = 0.10f),
                        lensHeight = 7.dp,
                        lensAmount = 16.dp,
                        blurRadius = 3.dp,
                    ),
                ) {
                    val date = Instant.ofEpochMilli(nextPaymentAt).atZone(zone)
                    Text("下次支付：${date.monthValue}月${date.dayOfMonth}日")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && Formats.amountInputToCents(amount) > 0L,
                onClick = {
                    onSave(
                        Subscription(
                            id = initial?.id ?: 0L,
                            name = name,
                            cycle = cycle,
                            accountId = accountId,
                            amountCents = Formats.amountInputToCents(amount),
                            nextPaymentAt = nextPaymentAt,
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (showDatePicker) {
        ZhDatePickerDialog(
            initialDate = Instant.ofEpochMilli(nextPaymentAt).atZone(zone).toLocalDate(),
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                nextPaymentAt = date.atStartOfDay(zone).toInstant().toEpochMilli()
                showDatePicker = false
            },
        )
    }
}
