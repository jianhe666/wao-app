package app.zhizhang.ui.wealth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.zhizhang.data.Formats
import app.zhizhang.data.SavingsDeposit
import app.zhizhang.data.SavingsGoal
import app.zhizhang.data.SavingsStore
import app.zhizhang.ui.components.GlassAlertDialog
import app.zhizhang.ui.components.ZhCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SavingsContent() {
    val context = LocalContext.current
    val store = remember { SavingsStore(context) }
    var goals by remember { mutableStateOf(store.goals()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<SavingsGoal?>(null) }
    var showGoalEditor by remember { mutableStateOf(false) }
    var showDepositEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SavingsDeposit?>(null) }
    var deleteGoal by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = goals.firstOrNull { it.id == selectedId }

    fun persist(next: List<SavingsGoal>) {
        if (store.save(next)) goals = next else error = "保存失败，请重试"
    }

    Text("攒钱记录用于跟踪目标进度，金额不重复计入总财富。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    TextButton(onClick = { editing = null; showGoalEditor = true }) { Text("＋ 新增攒钱目标") }
    if (goals.isEmpty()) Text("还没有目标，先为想完成的事攒一笔吧。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    goals.forEach { goal ->
        ZhCard(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            onClick = { selectedId = goal.id },
        ) {
            Text(goal.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("已攒 ${Formats.money(goal.savedCents)} / ${Formats.money(goal.targetCents)}")
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (goal.savedCents.toFloat() / goal.targetCents).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (goal.savedCents >= goal.targetCents) "目标已达成 · ${goal.deposits.size} 笔存入 ›"
                else "还差 ${Formats.money(goal.targetCents - goal.savedCents)} · ${goal.deposits.size} 笔存入 ›",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (selected != null && !showGoalEditor && !showDepositEditor && !deleteGoal && pendingDelete == null) {
        GlassAlertDialog(
            onDismissRequest = { selectedId = null },
            title = { Text(selected.name) },
            text = {
                Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                    Text("已攒 ${Formats.money(selected.savedCents)} / ${Formats.money(selected.targetCents)}")
                    Row {
                        TextButton(onClick = { editing = selected; showGoalEditor = true }) { Text("修改目标") }
                        TextButton(onClick = { deleteGoal = true }) { Text("删除目标") }
                    }
                    Text("存入明细", style = MaterialTheme.typography.titleSmall)
                    if (selected.deposits.isEmpty()) Text("还没有存入记录")
                    selected.deposits.sortedByDescending { it.timestamp }.forEach { deposit ->
                        val date = Instant.ofEpochMilli(deposit.timestamp).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text("+ ${Formats.money(deposit.cents)}")
                                Text(date, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { pendingDelete = deposit }) { Text("删除") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDepositEditor = true }) { Text("存一笔") } },
            dismissButton = { TextButton(onClick = { selectedId = null }) { Text("关闭") } },
        )
    }

    if (showGoalEditor) {
        SavingsForm(
            title = if (editing == null) "新增攒钱目标" else "修改目标",
            initialName = editing?.name.orEmpty(),
            initialAmount = editing?.let { Formats.centsToInput(it.targetCents) }.orEmpty(),
            amountLabel = "目标金额（元）",
            onDismiss = { showGoalEditor = false },
        ) { name, amount ->
            val target = editing
            val next = if (target == null) {
                goals + SavingsGoal(SavingsStore.newId(), name, amount)
            } else goals.map { if (it.id == target.id) it.copy(name = name, targetCents = amount) else it }
            if (store.save(next)) {
                goals = next
                showGoalEditor = false
            } else error = "保存失败，请重试"
        }
    }

    if (showDepositEditor && selected != null) {
        SavingsForm(
            title = "存一笔", initialAmount = "", amountLabel = "本次存入（元）",
            onDismiss = { showDepositEditor = false },
        ) { _, amount ->
            val total = runCatching { Math.addExact(selected.savedCents, amount) }.getOrNull()
            if (total == null) error = "累计金额过大" else {
                val updated = selected.copy(deposits = selected.deposits +
                    SavingsDeposit(SavingsStore.newId(), amount, System.currentTimeMillis()))
                val next = goals.map { if (it.id == selected.id) updated else it }
                if (store.save(next)) { goals = next; showDepositEditor = false }
                else error = "保存失败，请重试"
            }
        }
    }

    if ((deleteGoal || pendingDelete != null) && selected != null) {
        GlassAlertDialog(
            onDismissRequest = { deleteGoal = false; pendingDelete = null },
            title = { Text(if (deleteGoal) "删除目标和全部明细？" else "删除这笔存入？") },
            text = { Text("删除后进度会随记录更新。") },
            confirmButton = {
                TextButton(onClick = {
                    if (deleteGoal) {
                        val next = goals.filterNot { it.id == selected.id }
                        if (store.save(next)) { goals = next; selectedId = null }
                        else error = "保存失败，请重试"
                    } else {
                        val updated = selected.copy(deposits = selected.deposits.filterNot { it.id == pendingDelete?.id })
                        persist(goals.map { if (it.id == selected.id) updated else it })
                    }
                    deleteGoal = false
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteGoal = false; pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SavingsForm(
    title: String,
    initialName: String? = null,
    initialAmount: String,
    amountLabel: String,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
) {
    var name by remember { mutableStateOf(initialName.orEmpty()) }
    var amount by remember { mutableStateOf(initialAmount) }
    val cents = SavingsStore.amountCents(amount)
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (initialName != null) {
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text("目标名称") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    amount, { amount = it }, label = { Text(amountLabel) },
                    singleLine = true, isError = amount.isNotBlank() && cents == null,
                    supportingText = { Text("金额须大于 0，最多两位小数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = cents != null && (initialName == null || name.isNotBlank()),
                onClick = { cents?.let { onSave(name.trim(), it) } },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
