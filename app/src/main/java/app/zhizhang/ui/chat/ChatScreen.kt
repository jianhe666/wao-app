package app.zhizhang.ui.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import app.zhizhang.ui.components.GlassAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.zhizhang.R
import app.zhizhang.data.Account
import app.zhizhang.data.Accounts
import app.zhizhang.data.AssetCategories
import app.zhizhang.data.Categories
import app.zhizhang.data.Formats
import app.zhizhang.data.TxType
import app.zhizhang.domain.ChatStyle
import app.zhizhang.domain.CustomChatStyle
import app.zhizhang.domain.ChatStyles
import app.zhizhang.ui.components.AccountPickerSheet
import app.zhizhang.ui.components.AppMark
import app.zhizhang.ui.components.GlassCircleButton
import app.zhizhang.ui.components.PlatformSelectableText
import app.zhizhang.ui.components.LiquidActionButton
import app.zhizhang.ui.components.liquidGlass
import app.zhizhang.ui.components.LocalHazeState
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import app.zhizhang.ui.components.liquidHazeStyle
import dev.chrisbanes.haze.hazeEffect
import app.zhizhang.ui.components.ThinProgressBar
import app.zhizhang.ui.theme.zh
import java.time.Instant
import java.time.LocalDate
import java.io.File
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeChatScreen(
    prefillText: String? = null,
    onPrefillConsumed: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onAddBill: () -> Unit = {},
    onOpenLedger: () -> Unit = {},
    sharedImage: Uri? = null,
    onSharedImageConsumed: () -> Unit = {},
) {
    val viewModel: ChatViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val style by viewModel.chatStyle.collectAsStateWithLifecycle()
    val customStyles by viewModel.customChatStyles.collectAsStateWithLifecycle()
    val customAccounts by viewModel.customAccounts.collectAsStateWithLifecycle()
    val messageBackdrop = rememberLayerBackdrop { drawContent() }
    val overscrollEffect = rememberOverscrollEffect()
    var showStyleSheet by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val inputFocusRequester = remember { FocusRequester() }
    var cursorEndSignal by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val navigationBarHeight = with(density) {
        WindowInsets.navigationBars.getBottom(density).toDp()
    }
    // 只使用一个底部值，且取“键盘高度”和“底栏高度”的较大值。
    // 之前 imePadding 和 animateDpAsState 同时参与动画：键盘收起时
    // imePadding 先变成 0，而底栏间距还在 140ms 动画里，所以输入框
    // 会先掉到底部，再被补间动画抬起来。
    val imeGap = with(density) { WindowInsets.ime.getBottom(density).toDp() } + 8.dp
    val dockGap = navigationBarHeight + 84.dp
    val composerBottomPadding = maxOf(imeGap, dockGap)

    LaunchedEffect(Unit) {
        focusManager.clearFocus()
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.attachImage(uri)
    }
    val camera = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            cameraUri?.let(viewModel::attachImage)
        }
    }

    LaunchedEffect(sharedImage) {
        if (sharedImage != null) {
            viewModel.attachImage(sharedImage, autoSend = true)
            onSharedImageConsumed()
        }
    }

    LaunchedEffect(prefillText) {
        if (!prefillText.isNullOrBlank()) {
            viewModel.updateInput(prefillText)
            onPrefillConsumed()
        }
    }

    LaunchedEffect(state.messages.size, state.sending) {
        val count = state.messages.size + if (state.sending) 1 else 0
        if (count > 0) {
            // 确认卡片属于消息 item 的下半部分，只滚到 item 顶部会把它藏在输入框下面。
            listState.animateScrollToItem(count - 1, scrollOffset = 10_000)
        }
    }

    if (state.checking) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        )
        return
    }
    if (!state.available) {
        AiOffScreen(
            onOpenSettings = onOpenSettings,
            onOpenLedger = onOpenLedger,
            onAddBill = onAddBill,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        ChatHeader(
            style = style,
            canClear = state.messages.any { !it.welcome },
            onStyleClick = { showStyleSheet = true },
            onAddBill = onAddBill,
            onClear = { showClearDialog = true },
        )
        BudgetLine(summary)
        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (state.checking) {
                Box(Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(messageBackdrop)
                        .overscroll(overscrollEffect),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 12.dp,
                        bottom = composerBottomPadding + 80.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        Column(modifier = Modifier.animateItem()) {
                            ChatMessageItem(
                                message = message,
                                summary = summary,
                                onConfirm = { viewModel.confirm(message.id) },
                                onCancel = { viewModel.cancel(message.id) },
                                accounts = Accounts.all + customAccounts,
                                onReimbursementAccount = { index, accountId ->
                                    viewModel.setReimbursementAccount(message.id, index, accountId)
                                },
                                onReimbursementConfirm = { index ->
                                    viewModel.confirmReimbursement(message.id, index)
                                },
                                onReimbursementCancel = { index ->
                                    viewModel.cancelReimbursement(message.id, index)
                                },
                            )
                            if (message.welcome) {
                                Spacer(Modifier.height(10.dp))
                                Column(modifier = Modifier.padding(start = 33.dp)) {
                                    SuggestionRow(
                                        suggestions = state.quickPrompts,
                                        onPick = { suggestion ->
                                            viewModel.updateInput(suggestion)
                                            cursorEndSignal += 1
                                            runCatching { inputFocusRequester.requestFocus() }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (state.sending) {
                        item(key = "typing") {
                            Box(Modifier.animateItem()) { TypingRow() }
                        }
                    }
                }
            }

            // The composer floats over the tail of the conversation, with content-aware glass.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = composerBottomPadding),
            ) {
                state.pendingImage?.let { image ->
                    PendingImageBar(
                        image = image,
                        onRemove = viewModel::clearPendingImage,
                    )
                }
                ChatComposer(
                    value = state.input,
                    focusRequester = inputFocusRequester,
                    cursorEndSignal = cursorEndSignal,
                    sending = state.sending,
                    hasImage = state.pendingImage != null,
                    backdrop = messageBackdrop,
                    onValueChange = viewModel::updateInput,
                    onPickImage = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onCamera = {
                        runCatching {
                            val uri = createCameraUri(context)
                            cameraUri = uri
                            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            camera.launch(intent)
                        }.onFailure {
                            viewModel.reportError("没有找到可用的相机，试试从相册选择。")
                        }
                    },
                    onSend = { viewModel.send() },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    state.assetPrompt?.let { prompt ->
        GlassAlertDialog(
            onDismissRequest = viewModel::dismissAssetPrompt,
            title = { Text("这笔购买要加入资产吗？") },
            text = {
                Column {
                    Text(
                        text = "AI 判断这是一笔可管理的资产。确认后会加入资产列表，不影响已记的支出。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    prompt.candidates.forEach { candidate ->
                        val category = AssetCategories.byId(candidate.categoryId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = category.emoji)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = candidate.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${category.name} · ${Formats.money(candidate.amountCents)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::addPromptedAssets) { Text("加入资产") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAssetPrompt) { Text("暂不") }
            },
        )
    }

    if (showClearDialog) {
        GlassAlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空聊天记录？") },
            text = { Text("清空后只保留一条问候语。账目不会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    },
                ) { Text("清空", color = MaterialTheme.zh.expense) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }

    if (showStyleSheet) {
        ChatStyleSheet(
            currentStyleId = style.id,
            customStyles = customStyles,
            onSelectPreset = { id ->
                viewModel.setChatStyle(id)
                showStyleSheet = false
            },
            onSaveCustom = { id, name, instruction ->
                viewModel.saveCustomChatStyle(id, name, instruction)
            },
            onDeleteCustom = viewModel::deleteCustomChatStyle,
            onDismiss = { showStyleSheet = false },
        )
    }
}

@Composable
private fun AiOffScreen(
    onOpenSettings: () -> Unit,
    onOpenLedger: () -> Unit,
    onAddBill: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(bottom = 104.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppMark(size = 72.dp)
                Spacer(Modifier.height(22.dp))
                Text(
                    text = "AI 助手还没打开",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "打开后，文字和截图都能变成账目。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "调用 AI 会消耗少量 token 费用。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("去打开 AI 助手")
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onOpenLedger,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("查看流水")
                    }
                    OutlinedButton(
                        onClick = onAddBill,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("手动记账")
                    }
                }
            }
        }
    }
}

private fun createCameraUri(context: Context): Uri {
    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(directory, "bill-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

@Composable
private fun ChatHeader(
    style: ChatStyle,
    canClear: Boolean,
    onStyleClick: () -> Unit,
    onAddBill: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppMark(size = 38.dp)
        Spacer(Modifier.width(11.dp))
        Text(
            text = "哇哦记账",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .liquidGlass(
                    shape = RoundedCornerShape(50),
                    tint = MaterialTheme.zh.glassTop.copy(alpha = 0.12f),
                    lensHeight = 6.dp,
                    lensAmount = 14.dp,
                    blurRadius = 2.dp,
                )
                .clickable(onClick = onStyleClick)
                .semantics {
                    role = Role.Button
                    contentDescription = "切换聊天风格"
                }
                .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = style.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        GlassCircleButton(
            modifier = Modifier.size(34.dp),
            onClick = onAddBill,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "手动添加账单",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        if (canClear) {
            Spacer(Modifier.width(5.dp))
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .size(32.dp)
                    .liquidGlass(
                        shape = RoundedCornerShape(50),
                        tint = MaterialTheme.zh.glassTop.copy(alpha = 0.10f),
                        lensHeight = 5.dp,
                        lensAmount = 10.dp,
                        blurRadius = 2.dp,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "清空聊天记录",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun BudgetLine(summary: HomeSummary) {
    val hasBudget = summary.budgetCents > 0L
    val accent = when {
        summary.isOverBudget -> MaterialTheme.zh.expense
        summary.progress >= 0.9f -> MaterialTheme.zh.gold
        else -> MaterialTheme.colorScheme.primary
    }
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (hasBudget) "本月" else "本月预算未设置",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasBudget) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = Formats.money(summary.expenseCents),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = " / ${Formats.money(summary.budgetCents)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (summary.isOverBudget) "已超支" else "${(summary.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
            } else {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "在「我的」设置",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
        if (hasBudget) {
            Spacer(Modifier.height(7.dp))
            ThinProgressBar(
                progress = summary.progress,
                modifier = Modifier.fillMaxWidth(),
                color = accent,
                height = 3.dp,
            )
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    summary: HomeSummary,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    accounts: List<Account>,
    onReimbursementAccount: (Int, String) -> Unit,
    onReimbursementConfirm: (Int) -> Unit,
    onReimbursementCancel: (Int) -> Unit,
) {
    val haze = LocalHazeState.current
    val hazeStyle = liquidHazeStyle(tintAlpha = 0.30f)

    if (message.role == ChatRole.USER) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            message.image?.let { image ->
                Image(
                    bitmap = image,
                    contentDescription = "账单截图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .widthIn(max = 210.dp)
                        .heightIn(max = 240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.45f),
                            RoundedCornerShape(16.dp),
                        ),
                )
                Spacer(Modifier.height(6.dp))
            }
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp))
                    .then(if (haze != null) Modifier.hazeEffect(haze, hazeStyle) else Modifier)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.30f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            ),
                        ),
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                PlatformSelectableText(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            AppMark(size = 24.dp)
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.widthIn(max = 320.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp, 18.dp, 18.dp, 18.dp))
                        .then(if (haze != null) Modifier.hazeEffect(haze, hazeStyle) else Modifier)
                        .background(MaterialTheme.zh.glassTop.copy(alpha = 0.30f))
                        .border(
                            1.dp,
                            MaterialTheme.zh.hairline.copy(alpha = 0.22f),
                            RoundedCornerShape(5.dp, 18.dp, 18.dp, 18.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    PlatformSelectableText(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.error) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                if (message.bills.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    ChatBillCard(message, summary, onConfirm, onCancel)
                }
                if (message.reimbursements.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    message.reimbursements.forEachIndexed { index, reimbursement ->
                        if (index > 0) Spacer(Modifier.height(8.dp))
                        ChatReimbursementCard(
                            reimbursement = reimbursement,
                            accounts = accounts,
                            onAccount = { accountId -> onReimbursementAccount(index, accountId) },
                            onConfirm = { onReimbursementConfirm(index) },
                            onCancel = { onReimbursementCancel(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatReimbursementCard(
    reimbursement: ChatReimbursement,
    accounts: List<Account>,
    onAccount: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    var showAccountPicker by remember { mutableStateOf(false) }
    val allAccounts = (Accounts.all + accounts).distinctBy { it.id }
    val original = reimbursement.original
    val merchant = original.merchant.ifBlank { Categories.byId(original.categoryId).name }
    val date = Instant.ofEpochMilli(original.occurredAt).atZone(zone)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.zh.chip.copy(alpha = 0.55f))
            .border(
                1.dp,
                MaterialTheme.zh.hairline.copy(alpha = 0.26f),
                RoundedCornerShape(18.dp),
            )
            .padding(12.dp),
    ) {
        Text(
            text = when {
                reimbursement.confirmed -> "已报销"
                reimbursement.dismissed -> "已取消报销"
                else -> "找到这笔支出"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(MaterialTheme.zh.expense, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = merchant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${date.monthValue}月${date.dayOfMonth}日 · ${Formats.money(original.amountCents)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = Formats.money(original.amountCents),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "报销入账 +${Formats.money(original.amountCents)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.zh.income,
        )
        if (!reimbursement.confirmed && !reimbursement.dismissed) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showAccountPicker = true }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "到账账户",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = Accounts.byId(reimbursement.accountId).name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "选择报销到账账户",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = "取消",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onConfirm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "确认报销",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        } else if (reimbursement.confirmed) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = "已记入流水，原支出标记为已报销",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.zh.income,
            )
        } else {
            Spacer(Modifier.height(7.dp))
            Text(
                text = "已取消，不会生成报销收入",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showAccountPicker) {
        AccountPickerSheet(
            selectedAccountId = reimbursement.accountId,
            onSelect = {
                onAccount(it)
                showAccountPicker = false
            },
            onDismiss = { showAccountPicker = false },
            extraAccounts = allAccounts.filter { it.id.startsWith("card:") },
        )
    }
}

@Composable
private fun ChatBillCard(
    message: ChatMessage,
    summary: HomeSummary,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var expanded by remember(message.id) { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(160),
        label = "billExpandArrow",
    )
    val visibleBills = if (expanded) message.bills else message.bills.take(5)
    val billExpense = message.bills
        .filter { it.bill.type == TxType.EXPENSE }
        .sumOf { it.bill.amountCents }
    val projected = summary.expenseCents + billExpense
    val budgetHint = if (summary.budgetCents > 0L && !message.confirmed && !message.dismissed) {
        when {
            projected > summary.budgetCents ->
                "入账后会超出预算 ${Formats.money(projected - summary.budgetCents)}。"
            projected >= summary.budgetCents * 0.9 ->
                "入账后只剩 ${Formats.money(summary.budgetCents - projected)} 预算。"
            billExpense >= 100_000L ->
                "大额支出，可以先放 24 小时再决定。"
            else -> null
        }
    } else {
        null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .liquidGlass(
                shape = RoundedCornerShape(18.dp),
                tint = MaterialTheme.zh.glassTop.copy(alpha = 0.16f),
                lensHeight = 9.dp,
                lensAmount = 20.dp,
                blurRadius = 4.dp,
            )
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.34f),
                RoundedCornerShape(18.dp),
            )
            .padding(12.dp),
    ) {
        Text(
            text = when {
                message.confirmed -> "已入账"
                message.dismissed -> "已取消 · ${message.bills.size} 笔"
                else -> "待入账 · ${message.bills.size} 笔"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        visibleBills.forEachIndexed { index, chatBill ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            val category = Categories.byId(chatBill.categoryId)
            val account = Accounts.byId(chatBill.bill.accountId)
            val date = Instant.ofEpochMilli(chatBill.bill.occurredAt).atZone(zone)
            val day = when (date.toLocalDate()) {
                today -> "今天"
                today.minusDays(1) -> "昨天"
                else -> "${date.monthValue}月${date.dayOfMonth}日"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(Color(category.color), CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chatBill.bill.merchant.ifBlank { category.name },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$day · ${category.name} · ${account.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = Formats.signedMoney(chatBill.bill.amountCents, chatBill.bill.type),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (chatBill.bill.type == TxType.INCOME) {
                        MaterialTheme.zh.income
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
        if (message.bills.size > 5) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (expanded) {
                        "收起账单"
                    } else {
                        "还有 ${message.bills.size - 5} 笔 · 展开"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(3.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起账单" else "展开全部账单",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(15.dp)
                        .graphicsLayer {
                            rotationZ = arrowRotation
                        },
                )
            }
        }
        if (budgetHint != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = budgetHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.zh.gold,
            )
        }
        Spacer(Modifier.height(10.dp))
        when {
            message.confirmed -> {
                Text(
                    text = if (message.confirmedCount > 0) "已记入流水" else "之前已经记过",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.zh.income,
                )
            }
            message.dismissed -> {
                Text(
                    text = "已取消，不会记入流水",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .liquidGlass(
                                shape = RoundedCornerShape(12.dp),
                                tint = MaterialTheme.zh.glassTop.copy(alpha = 0.10f),
                                lensHeight = 6.dp,
                                lensAmount = 12.dp,
                                blurRadius = 2.dp,
                            ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                        ),
                    ) {
                        Text(
                            text = "取消",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .liquidGlass(
                                shape = RoundedCornerShape(12.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                lensHeight = 7.dp,
                                lensAmount = 14.dp,
                                blurRadius = 3.dp,
                            )
                            .clickable(onClick = onConfirm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "入账",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestions: List<String>,
    onPick: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { suggestion ->
            Text(
                text = suggestion,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(
                        1.dp,
                        MaterialTheme.zh.hairline.copy(alpha = 0.35f),
                        RoundedCornerShape(50),
                    )
                    .clickable { onPick(suggestion) }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun TypingRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppMark(size = 24.dp)
        Spacer(Modifier.width(9.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(13.dp),
            strokeWidth = 1.5.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "正在想…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PendingImageBar(
    image: androidx.compose.ui.graphics.ImageBitmap,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            bitmap = image,
            contentDescription = "待发送的账单截图",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = "账单截图",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "移除图片",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ChatComposer(
    value: String,
    focusRequester: FocusRequester,
    cursorEndSignal: Int,
    sending: Boolean,
    hasImage: Boolean,
    backdrop: Backdrop?,
    onValueChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onCamera: () -> Unit,
    onSend: () -> Unit,
) {
    val canSend = !sending && (value.isNotBlank() || hasImage)
    var showAddMenu by remember { mutableStateOf(false) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            fieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }
    LaunchedEffect(cursorEndSignal) {
        if (cursorEndSignal > 0) {
            fieldValue = fieldValue.copy(selection = TextRange(fieldValue.text.length))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .liquidGlass(
                shape = RoundedCornerShape(26.dp),
                tint = MaterialTheme.zh.glassTop.copy(alpha = 0.16f),
                lensHeight = 10.dp,
                lensAmount = 24.dp,
                blurRadius = 5.dp,
                backdropOverride = backdrop,
            )
            .padding(start = 14.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicTextField(
            value = fieldValue,
            onValueChange = { next ->
                fieldValue = next
                onValueChange(next.text)
            },
            enabled = !sending,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = 4,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .padding(end = 6.dp, top = 12.dp, bottom = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (fieldValue.text.isBlank()) {
                        Text(
                            text = "和我说说今天花了多少吧",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        )
                    }
                    inner()
                }
            },
        )
        LiquidActionButton(
            modifier = Modifier.size(38.dp),
            onClick = onSend,
            enabled = canSend,
            tint = MaterialTheme.colorScheme.primary,
        ) {
            if (sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 1.5.dp,
                    color = Color.White,
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Box {
            GlassCircleButton(
                modifier = Modifier.size(38.dp),
                onClick = { showAddMenu = true },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加账单图片",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
            DropdownMenu(
                expanded = showAddMenu,
                onDismissRequest = { showAddMenu = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                DropdownMenuItem(
                    text = { Text("从相册选择") },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_image),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        showAddMenu = false
                        onPickImage()
                    },
                )
                DropdownMenuItem(
                    text = { Text("拍照") },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_camera),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        showAddMenu = false
                        onCamera()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatStyleSheet(
    currentStyleId: String,
    customStyles: List<CustomChatStyle>,
    onSelectPreset: (String) -> Unit,
    onSaveCustom: (String?, String, String) -> Unit,
    onDeleteCustom: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editorOpen by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var nameValue by remember { mutableStateOf("") }
    var instructionValue by remember { mutableStateOf("") }

    fun openNew() {
        editingId = null
        nameValue = ""
        instructionValue = ""
        editorOpen = true
    }

    fun openEdit(style: CustomChatStyle) {
        editingId = style.id
        nameValue = style.name
        instructionValue = style.instruction
        editorOpen = true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 34.dp, height = 3.dp)
                    .background(MaterialTheme.zh.hairline, CircleShape),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "说话方式",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(14.dp))
            ChatStyles.presets.filter { it.id != "custom" }.forEach { preset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (preset.id == currentStyleId) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable { onSelectPreset(preset.id) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.weight(1f))
                    if (preset.id == currentStyleId) {
                        Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (customStyles.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .height(1.dp)
                        .background(MaterialTheme.zh.hairline.copy(alpha = 0.3f)),
                )
                Text(
                    text = "我的风格",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                customStyles.forEach { custom ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (custom.id == currentStyleId) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                } else {
                                    Color.Transparent
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectPreset(custom.id) }
                                .padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = custom.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = custom.instruction,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (custom.id == currentStyleId) {
                            Text("✓", color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                        }
                        TextButton(
                            onClick = { openEdit(custom) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("编辑", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { openNew() }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "＋ 新建自定义风格",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (editorOpen) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = nameValue,
                    onValueChange = { nameValue = it.take(24) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("风格名称") },
                    placeholder = { Text("例如：像朋友一样提醒我") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = instructionValue,
                    onValueChange = { instructionValue = it.take(240) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("说话方式") },
                    placeholder = { Text("比如：少说教，直接说金额和一句建议。") },
                    textStyle = MaterialTheme.typography.bodySmall,
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (editingId != null) {
                        TextButton(
                            onClick = {
                                editingId?.let(onDeleteCustom)
                                editorOpen = false
                            },
                        ) { Text("删除", color = MaterialTheme.zh.expense) }
                    }
                    TextButton(onClick = { editorOpen = false }) { Text("取消") }
                    TextButton(
                        onClick = {
                            onSaveCustom(editingId, nameValue, instructionValue)
                            editorOpen = false
                        },
                        enabled = nameValue.isNotBlank() && instructionValue.isNotBlank(),
                    ) { Text("保存") }
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("关闭")
            }
        }
    }
}
