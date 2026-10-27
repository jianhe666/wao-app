package app.zhizhang.ui.assets

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import app.zhizhang.ui.components.GlassAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.app.DatePickerDialog
import java.time.LocalDate
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.zhizhang.data.AssetCategories
import app.zhizhang.data.AssetCategory
import app.zhizhang.data.AssetEntity
import app.zhizhang.data.Formats
import app.zhizhang.ui.components.ZhCard
import app.zhizhang.ui.components.liquidGlass
import app.zhizhang.ui.theme.zh
import java.time.Instant
import java.time.ZoneId

@Composable
fun AssetsScreen(onBack: () -> Unit) {
    val viewModel: AssetsViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    var showEditor by remember { mutableStateOf(false) }
    var editingAsset by remember { mutableStateOf<AssetEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<AssetEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "资产管理",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "已有资产和购买记录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = {
                    editingAsset = null
                    showEditor = true
                },
                modifier = Modifier.liquidGlass(
                    shape = RoundedCornerShape(50),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    lensHeight = 6.dp,
                    lensAmount = 14.dp,
                    blurRadius = 2.dp,
                ),
            ) { Text("+ 添加") }
        }

        ZhCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 17.dp, vertical = 15.dp),
        ) {
            Text(
                text = "总资产",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = Formats.money(state.totalCents),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${state.assets.size} 项资产",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(14.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.assets.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "还没有资产",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "把已经有的现金、存款、数码或房产加进来。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(14.dp))
                        TextButton(
                            onClick = {
                                editingAsset = null
                                showEditor = true
                            },
                        ) { Text("添加第一项资产") }
                    }
                }
            } else {
                items(state.assets, key = { it.id }) { asset ->
                    AssetRow(
                        asset = asset,
                        zone = zone,
                        onEdit = {
                            editingAsset = asset
                            showEditor = true
                        },
                        onDelete = { pendingDelete = asset },
                    )
                }
            }
        }
    }

    if (showEditor) {
        AssetEditorDialog(
            initial = editingAsset,
            onDismiss = {
                showEditor = false
                editingAsset = null
            },
            onSave = { name, category, value, note, acquiredAt ->
                val target = editingAsset
                if (target == null) {
                    viewModel.add(name, category, value, note, acquiredAt) { ok ->
                        if (ok) {
                            showEditor = false
                            editingAsset = null
                        }
                    }
                } else {
                    viewModel.update(target, name, category, value, note, acquiredAt) { ok ->
                        if (ok) {
                            showEditor = false
                            editingAsset = null
                        }
                    }
                }
            },
        )
    }

    pendingDelete?.let { asset ->
        GlassAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这项资产？") },
            text = { Text("「${asset.name}」会从资产列表移除，不会删除账目。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(asset)
                        pendingDelete = null
                    },
                ) { Text("删除", color = MaterialTheme.zh.expense) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun AssetRow(
    asset: AssetEntity,
    zone: ZoneId,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val category = asset.category
    val date = Instant.ofEpochMilli(asset.acquiredAt).atZone(zone)
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
            .clickable(onClick = onEdit)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(Color(category.color).copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = category.emoji, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = asset.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(category.name)
                    if (category.tracksPurchaseDate) {
                        append(" · ${date.year}/${date.monthValue}/${date.dayOfMonth}")
                    }
                    if (asset.note.isNotBlank()) append(" · ${asset.note}")
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
                text = Formats.money(asset.valueCents),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "编辑${asset.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除${asset.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetEditorDialog(
    initial: AssetEntity?,
    onDismiss: () -> Unit,
    onSave: (String, AssetCategory, String, String, Long?) -> Unit,
) {
    val context = LocalContext.current
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var value by remember(initial?.id) {
        mutableStateOf(initial?.let { Formats.centsToInput(it.valueCents) }.orEmpty())
    }
    var note by remember(initial?.id) { mutableStateOf(initial?.note.orEmpty()) }
    var category by remember(initial?.id) {
        mutableStateOf(initial?.category ?: AssetCategories.all.first())
    }
    var acquiredDate by remember(initial?.id) {
        mutableStateOf(
            initial?.acquiredAt?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            } ?: LocalDate.now(),
        )
    }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加资产" else "编辑资产") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("资产名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("当前价值（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (category.tracksPurchaseDate) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    acquiredDate = LocalDate.of(year, month + 1, day)
                                },
                                acquiredDate.year,
                                acquiredDate.monthValue - 1,
                                acquiredDate.dayOfMonth,
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("购入日期：${acquiredDate.year}/${acquiredDate.monthValue}/${acquiredDate.dayOfMonth}")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "类型",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(5.dp))
                AssetCategories.all.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { item ->
                            val selected = category.id == item.id
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else MaterialTheme.zh.chip.copy(alpha = 0.5f),
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        else Color.Transparent,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable { category = item }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${item.emoji} ${item.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(160) },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val acquiredAt = if (category.tracksPurchaseDate) {
                        acquiredDate.atStartOfDay(ZoneId.systemDefault())
                            .toInstant().toEpochMilli()
                    } else {
                        null
                    }
                    onSave(name, category, value, note, acquiredAt)
                },
                enabled = name.isNotBlank() && value.isNotBlank(),
            ) { Text(if (initial == null) "保存" else "保存修改") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
