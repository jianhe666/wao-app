package app.zhizhang.ui.importflow

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.zhizhang.data.Categories
import app.zhizhang.data.Formats
import app.zhizhang.domain.ParsedBill
import app.zhizhang.ui.components.GlassPanel
import app.zhizhang.ui.components.liquidGlass
import app.zhizhang.ui.components.MerchantBadge
import app.zhizhang.ui.theme.zh
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ImportScreen(
    onBack: (() -> Unit)? = null,
    onImported: (Int) -> Unit,
) {
    val viewModel: ImportViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val csvPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.parseCsv(uri)
    }

    LaunchedEffect(state.importedCount) {
        state.importedCount?.let { count ->
            viewModel.consumeImported()
            onImported(count)
        }
    }

    val statusTop = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = statusTop + 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = "智能导入",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "文本 / CSV / XLSX",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "input") {
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = PaddingValues(14.dp),
                ) {
                    OutlinedTextField(
                        value = state.text,
                        onValueChange = viewModel::onTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(164.dp),
                        placeholder = {
                            Text(
                                text = "把支付宝、微信或银行短信粘进来…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            )
                        },
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(14.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ImportSourceCard(
                            title = "粘贴",
                            subtitle = "从剪贴板",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.onTextChange(clipboard.getText()?.text.orEmpty())
                            },
                        )
                        ImportSourceCard(
                            title = "示例",
                            subtitle = "看看格式",
                            modifier = Modifier.weight(1f),
                            onClick = viewModel::fillSample,
                        )
                        ImportSourceCard(
                            title = "账单 CSV",
                            subtitle = "选择文件",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                csvPicker.launch(
                                    arrayOf(
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "text/plain",
                                        "application/vnd.ms-excel",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/octet-stream",
                                    ),
                                )
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = viewModel::parseText,
                            enabled = state.canParse,
                            modifier = Modifier.liquidGlass(
                                shape = RoundedCornerShape(12.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                lensHeight = 7.dp,
                                lensAmount = 15.dp,
                                blurRadius = 3.dp,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                            ),
                        ) {
                            if (state.parsing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 1.6.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text("识别")
                            }
                        }
                    }
                }
            }

            state.error?.let { error ->
                item(key = "error") { HintText(error, MaterialTheme.zh.expense) }
            }
            state.hint?.let { hint ->
                item(key = "hint") { HintText(hint, MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            if (state.bills.isNotEmpty()) {
                item(key = "result-title") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = buildString {
                                append("识别到 ${state.bills.size} 笔")
                                state.source?.let { append(" · $it") }
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.weight(1f))
                        if (state.skipped > 0) {
                            Text(
                                text = "跳过 ${state.skipped} 行",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(state.bills, key = { it.fingerprint }) { bill ->
                    BillPreview(bill)
                }
                item(key = "import") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = viewModel::cancelImport,
                            enabled = !state.importing,
                            modifier = Modifier.height(46.dp),
                        ) {
                            Text(
                                text = "取消导入",
                                color = if (state.importing) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = viewModel::importBills,
                            enabled = state.canImport,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            if (state.importing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(17.dp),
                                    strokeWidth = 1.6.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text("导入 ${state.bills.size} 笔")
                            }
                        }
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun HintText(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun BillPreview(bill: ParsedBill) {
    val category = Categories.byId(bill.categoryId)
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(bill.occurredAt).atZone(zone)
    val dateLabel = date.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.zh.chip.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MerchantBadge(
            text = bill.merchant.ifBlank { category.name },
            color = Color(category.color),
            size = 30.dp,
        )
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bill.merchant.ifBlank { category.name },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$dateLabel · ${category.name}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = Formats.signedMoney(bill.amountCents, bill.type),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}


@Composable
private fun ImportSourceCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .liquidGlass(
                shape = RoundedCornerShape(14.dp),
                tint = MaterialTheme.zh.glassTop.copy(alpha = 0.10f),
                lensHeight = 7.dp,
                lensAmount = 16.dp,
                blurRadius = 3.dp,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
