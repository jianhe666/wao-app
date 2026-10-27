package app.zhizhang.ui.editor

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import app.zhizhang.ui.components.GlassAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.zhizhang.data.Accounts
import app.zhizhang.data.CustomCategoryStore
import app.zhizhang.data.Categories
import app.zhizhang.data.TxSource
import app.zhizhang.data.ReimbursementStatus
import app.zhizhang.data.TxType
import app.zhizhang.domain.BudgetPulse
import app.zhizhang.domain.BudgetTone
import app.zhizhang.ui.components.AccountPickerSheet
import app.zhizhang.ui.components.AddCategoryDialog
import app.zhizhang.ui.components.CategoryAddCell
import app.zhizhang.ui.components.CategoryCell
import app.zhizhang.ui.components.liquidGlass
import app.zhizhang.ui.components.ConfirmDialog
import app.zhizhang.ui.components.ZhDatePickerDialog
import app.zhizhang.ui.components.ZhTimePickerDialog
import app.zhizhang.ui.components.GlassPanel
import app.zhizhang.ui.components.SourceTag
import app.zhizhang.ui.theme.zh
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(onClose: () -> Unit) {
    val viewModel: EditorViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val customAccounts by viewModel.customAccounts.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showAddCardDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val categoryStore = remember { CustomCategoryStore(context) }

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        EditorTopBar(
            isEditing = state.isEditing,
            onClose = onClose,
            onDelete = if (state.isEditing) {
                { showDeleteDialog = true }
            } else {
                null
            },
        )

        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(2.dp))
                TypeToggle(
                    type = state.type,
                    onTypeChange = viewModel::setType,
                )
                Spacer(Modifier.height(16.dp))
                AmountDisplay(
                    input = state.amountInput,
                    type = state.type,
                )
                if (!state.error.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = state.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.zh.expense,
                    )
                }
                if (state.merchant.isNotBlank() || state.source != TxSource.MANUAL) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SourceTag(state.source)
                        if (state.merchant.isNotBlank()) {
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = state.merchant,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                state.budgetPulse?.let { pulse ->
                    Spacer(Modifier.height(8.dp))
                    BudgetCoachCard(pulse = pulse)
                }
                Spacer(Modifier.height(12.dp))
                val categorySlots: List<app.zhizhang.data.Category?> =
                    Categories.forType(state.type) + listOf(null)
                categorySlots.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { category ->
                            if (category == null) {
                                CategoryAddCell(
                                    onClick = { showAddCategory = true },
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                CategoryCell(
                                    category = category,
                                    selected = category.id == state.categoryId,
                                    onClick = { viewModel.setCategory(category.id) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        repeat(4 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (state.type == TxType.EXPENSE) {
                    ReimbursementRow(
                        status = state.reimbursementStatus,
                        onToggle = viewModel::setPendingReimbursement,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                MetaChips(
                    accountId = state.accountId,
                    occurredAt = state.occurredAt,
                    note = state.note,
                    zone = zone,
                    onAccountClick = { showAccountPicker = true },
                    onDateClick = { showDatePicker = true },
                    onTimeClick = { showTimePicker = true },
                    onNoteClick = { showNoteDialog = true },
                )
                Spacer(Modifier.height(10.dp))
            }

            Keypad(
                canSave = state.canSave,
                saving = state.saving,
                onDigit = viewModel::appendDigit,
                onDot = viewModel::appendDot,
                onBackspace = viewModel::backspace,
                onSave = viewModel::save,
            )
        }
    }

    if (showAccountPicker) {
        AccountPickerSheet(
            selectedAccountId = state.accountId,
            onSelect = viewModel::setAccount,
            onDismiss = { showAccountPicker = false },
            extraAccounts = customAccounts,
            onAddCard = {
                showAccountPicker = false
                showAddCardDialog = true
            },
        )
    }

    if (showTimePicker) {
        val time = Instant.ofEpochMilli(state.occurredAt).atZone(zone).toLocalTime()
        ZhTimePickerDialog(
            initialHour = time.hour,
            initialMinute = time.minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                viewModel.setTime(hour, minute)
                showTimePicker = false
            },
        )
    }

    if (showAddCardDialog) {
        AddBankCardDialog(
            onDismiss = { showAddCardDialog = false },
            onConfirm = { bank, tail ->
                viewModel.addBankCard(bank, tail)
                showAddCardDialog = false
            },
        )
    }

    if (showDatePicker) {
        ZhDatePickerDialog(
            initialDate = Instant.ofEpochMilli(state.occurredAt)
                .atZone(zone)
                .toLocalDate(),
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                viewModel.setDate(date)
                showDatePicker = false
            },
        )
    }

    if (showNoteDialog) {
        NoteDialog(
            initialNote = state.note,
            onDismiss = { showNoteDialog = false },
            onConfirm = { note ->
                viewModel.setNote(note)
                showNoteDialog = false
            },
        )
    }

    if (showAddCategory) {
        AddCategoryDialog(
            initialType = state.type,
            onDismiss = { showAddCategory = false },
            onSave = { name, emoji, color, type ->
                val created = categoryStore.add(name, emoji, color, type)
                if (created != null) {
                    Categories.setCustomCategories(categoryStore.load())
                    if (type != state.type) viewModel.setType(type)
                    viewModel.setCategory(created.id)
                    showAddCategory = false
                }
            },
        )
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "删除这笔账目？",
            body = "删除后无法恢复，但它不会影响已经学会的分类规则。",
            confirmText = "删除",
            destructive = true,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
            },
        )
    }
}

@Composable
private fun AddBankCardDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var bank by remember { mutableStateOf("") }
    var tail by remember { mutableStateOf("") }
    val valid = bank.isNotBlank() && tail.filter(Char::isDigit).length in 3..6
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("添加银行卡") },
        text = {
            Column {
                Text(
                    text = "填写银行名称和卡号尾号，保存后可在付款账户中选择。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = bank,
                    onValueChange = { bank = it.take(20) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("银行名称") },
                    placeholder = { Text("例如：招商银行") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tail,
                    onValueChange = { tail = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("卡号尾号") },
                    placeholder = { Text("例如：1234") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (valid) onConfirm(bank, tail)
                },
                enabled = valid,
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun EditorTopBar(
    isEditing: Boolean,
    onClose: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = if (isEditing) "编辑账目" else "记一笔",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除这笔账目",
                    tint = MaterialTheme.zh.expense,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun BudgetCoachCard(pulse: BudgetPulse) {
    val accent = when (pulse.tone) {
        BudgetTone.CALM -> MaterialTheme.zh.income
        BudgetTone.WATCH, BudgetTone.BIG -> MaterialTheme.zh.gold
        BudgetTone.ALERT -> MaterialTheme.zh.expense
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(5.dp)
                .background(accent, CircleShape),
        )
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pulse.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = pulse.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TypeToggle(
    type: TxType,
    onTypeChange: (TxType) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.zh.chip,
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            TxType.entries.forEach { entry ->
                val selected = type == entry
                val selectedColor = if (entry == TxType.INCOME) {
                    MaterialTheme.zh.income
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { onTypeChange(entry) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountDisplay(
    input: String,
    type: TxType,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorAlpha",
    )
    val display = input.ifEmpty { "0" }
    val fontSize = when {
        display.length > 9 -> 30.sp
        display.length > 7 -> 34.sp
        else -> 42.sp
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = "¥",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp, end = 4.dp),
        )
        Text(
            text = display,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = fontSize,
                lineHeight = (fontSize.value + 6).sp,
            ),
            color = if (input.isEmpty()) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .padding(start = 3.dp, bottom = 7.dp)
                .width(2.dp)
                .height(32.dp)
                .background(
                    (if (type == TxType.INCOME) MaterialTheme.zh.income else MaterialTheme.colorScheme.primary)
                        .copy(alpha = cursorAlpha),
                ),
        )
    }
}

@Composable
private fun ReimbursementRow(
    status: ReimbursementStatus,
    onToggle: (Boolean) -> Unit,
) {
    val done = status == ReimbursementStatus.DONE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.zh.chip.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "报销",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when (status) {
                    ReimbursementStatus.NONE -> "不需要报销"
                    ReimbursementStatus.PENDING -> "待报销，会显示在流水页"
                    ReimbursementStatus.DONE -> "已报销，已生成报销收入"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = status == ReimbursementStatus.PENDING,
            enabled = !done,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun MetaChips(
    accountId: String,
    occurredAt: Long,
    note: String,
    zone: ZoneId,
    onAccountClick: () -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onNoteClick: () -> Unit,
) {
    val account = Accounts.byId(accountId)
    val date = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val dateLabel = when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> date.format(DateTimeFormatter.ofPattern("M月d日"))
    }
    val timeLabel = Instant.ofEpochMilli(occurredAt).atZone(zone)
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetaChip(
            label = "账户 · ${account.name}",
            onClick = onAccountClick,
            modifier = Modifier.fillMaxWidth(),
        )
        MetaChip(
            label = "日期 · $dateLabel",
            onClick = onDateClick,
            modifier = Modifier.fillMaxWidth(),
        )
        MetaChip(
            label = "时间 · $timeLabel",
            onClick = onTimeClick,
            modifier = Modifier.fillMaxWidth(),
        )
        MetaChip(
            label = "备注 · ${note.ifBlank { "添加备注" }}",
            onClick = onNoteClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MetaChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(36.dp)
            .liquidGlass(
                shape = RoundedCornerShape(50),
                tint = MaterialTheme.zh.glassTop.copy(alpha = 0.12f),
                lensHeight = 6.dp,
                lensAmount = 14.dp,
                blurRadius = 3.dp,
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        border = null,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Keypad(
    canSave: Boolean,
    saving: Boolean,
    onDigit: (Char) -> Unit,
    onDot: () -> Unit,
    onBackspace: () -> Unit,
    onSave: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .height(224.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                ).forEach { row ->
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { digit ->
                            KeypadKey(
                                label = digit,
                                onClick = { onDigit(digit.first()) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KeypadKey(label = ".", onClick = onDot, modifier = Modifier.weight(1f))
                    KeypadKey(label = "0", onClick = { onDigit('0') }, modifier = Modifier.weight(1f))
                    KeypadKey(label = "⌫", onClick = onBackspace, modifier = Modifier.weight(1f))
                }
            }
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier
                    .width(92.dp)
                    .fillMaxHeight()
                    .liquidGlass(
                        shape = RoundedCornerShape(20.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        lensHeight = 10.dp,
                        lensAmount = 20.dp,
                        blurRadius = 4.dp,
                    ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(0.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                ),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = "保存",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun KeypadKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .liquidGlass(
                shape = RoundedCornerShape(14.dp),
                tint = MaterialTheme.zh.glassTop.copy(alpha = 0.12f),
                lensHeight = 7.dp,
                lensAmount = 16.dp,
                blurRadius = 3.dp,
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun NoteDialog(
    initialNote: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialNote) { mutableStateOf(initialNote) }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("备注") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(120) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("比如：和朋友聚餐、报销用") },
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
