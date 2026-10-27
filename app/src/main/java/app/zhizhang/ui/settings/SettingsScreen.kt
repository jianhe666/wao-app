package app.zhizhang.ui.settings

import android.Manifest
import android.app.AlarmManager
import app.zhizhang.notification.NotificationCaptureStatus
import app.zhizhang.notification.PaymentAccessibilityService
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import app.zhizhang.ui.components.GlassAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.unit.sp
import app.zhizhang.data.BaiduRemoteBackup
import kotlin.math.roundToInt
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.zhizhang.BuildConfig
import app.zhizhang.data.GithubRelease
import app.zhizhang.data.UpdateChecker
import app.zhizhang.R
import app.zhizhang.data.BaiduState
import app.zhizhang.data.Formats
import app.zhizhang.ui.components.BudgetDialog
import app.zhizhang.ui.components.ConfirmDialog
import app.zhizhang.ui.components.GlassDialog
import app.zhizhang.ui.components.MarkdownText
import app.zhizhang.ui.components.AppMark
import app.zhizhang.ui.components.BaiduMark
import app.zhizhang.ui.components.ZhCard
import app.zhizhang.ui.theme.zh
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(
    onShowMessage: (String) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenAiCategory: () -> Unit,
    onOpenAssets: () -> Unit,
    onOpenBackground: () -> Unit,
    highlightAi: Boolean = false,
    onHighlightAiConsumed: () -> Unit = {},
) {
    val viewModel: SettingsViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val baiduState by viewModel.baiduState.collectAsStateWithLifecycle()
    val baiduLogin by viewModel.baiduLogin.collectAsStateWithLifecycle()
    val baiduBackingUp by viewModel.baiduBackingUp.collectAsStateWithLifecycle()
    val baiduRestore by viewModel.baiduRestore.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val updateUseMirror by viewModel.updateUseMirror.collectAsStateWithLifecycle()
    val reminder by viewModel.reminderState.collectAsStateWithLifecycle()
    val autoCaptureAllApps by viewModel.autoCaptureAllApps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setDailyReminderEnabled(true)
        } else {
            onShowMessage("需要通知权限，才能发送每日记账提醒")
        }
    }
    val billChangePermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            onShowMessage("自动记账已开启，但通知权限未授权，账单变动提醒不会显示")
        }
    }
    fun enableAutoCapture() {
        viewModel.setAutoCapture(true)
        if (!isNotificationAccessGranted(context)) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }.onFailure {
                onShowMessage("无法打开通知读取设置，请手动授予权限")
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            billChangePermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun enableReminderWithPermission() {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setDailyReminderEnabled(true)
        }
    }
    fun toggleReminder(enabled: Boolean) {
        if (enabled) enableReminderWithPermission()
        else viewModel.setDailyReminderEnabled(false)
    }

    var showBudgetDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }
    var enableAiOnSave by remember { mutableStateOf(false) }
    var showBaiduLoginDialog by remember { mutableStateOf(false) }
    var enableBaiduAutoOnLogin by remember { mutableStateOf(false) }
    var showBaiduDirectoryDialog by remember { mutableStateOf(false) }
    var showBaiduRetentionDialog by remember { mutableStateOf(false) }
    var showBaiduRestoreDialog by remember { mutableStateOf(false) }
    var showReminderTimeDialog by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showBaiduLogoutDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var notificationAccessGranted by remember {
        mutableStateOf(isNotificationAccessGranted(context))
    }
    val notificationServiceConnected by NotificationCaptureStatus.connected.collectAsStateWithLifecycle()
    var exactAlarmAllowed by remember {
        mutableStateOf(canScheduleExactAlarms(context))
    }
    var accessibilityEnabled by remember {
        mutableStateOf(isPaymentAccessibilityEnabled(context))
    }
    val statusTop = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    val aiPulse = remember { Animatable(0f) }
    LaunchedEffect(highlightAi) {
        if (highlightAi) {
            kotlinx.coroutines.delay(220)
            repeat(2) {
                aiPulse.animateTo(1f, tween(220))
                aiPulse.animateTo(0f, tween(220))
            }
            onHighlightAiConsumed()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccessGranted = isNotificationAccessGranted(context)
                exactAlarmAllowed = canScheduleExactAlarms(context)
                accessibilityEnabled = isPaymentAccessibilityEnabled(context)
                viewModel.rescheduleDailyReminder()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingRestore = uri
            showRestoreDialog = true
        }
    }

    LaunchedEffect(baiduRestore.completedCount) {
        if (baiduRestore.completedCount != null) {
            showBaiduRestoreDialog = false
            viewModel.clearBaiduRestore()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is SettingsEvent.Message -> onShowMessage(event.text)
                is SettingsEvent.ShareFile -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = event.mime
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        clipData = ClipData.newRawUri("哇哦记账导出", event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(send, event.title),
                    )
                }
            }
        }
    }

    fun openNotificationAccess() {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }.onFailure {
            onShowMessage("无法打开系统设置，请手动授予通知读取权限")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 120.dp),
    ) {
        Spacer(Modifier.height(statusTop + 10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "我的",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(20.dp))

        SettingsSectionLabel("资产")
        Spacer(Modifier.height(8.dp))
        ZhCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            SettingsRow(
                icon = "wallet",
                iconTint = MaterialTheme.zh.gold,
                title = "资产管理",
                onClick = onOpenAssets,
            )
        }

        Spacer(Modifier.height(20.dp))
        SettingsSectionLabel("外观")
        Spacer(Modifier.height(8.dp))
        ZhCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            SettingsRow(
                icon = "spark",
                iconTint = MaterialTheme.colorScheme.primary,
                title = "个性化背景",
                subtitle = "预设、自己的照片和透明度",
                onClick = onOpenBackground,
            )

        }

        Spacer(Modifier.height(20.dp))
        SettingsSectionLabel("记账")
        Spacer(Modifier.height(8.dp))
        ZhCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            SettingsRow(
                icon = "wallet",
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "月度预算",
                subtitle = if (state.budgetCents > 0) {
                    Formats.money(state.budgetCents)
                } else {
                    "未设置"
                },
                onClick = { showBudgetDialog = true },
            )
            SettingsDivider()
            SettingsRow(
                icon = "spark",
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "分类管理",
                subtitle = "添加自定义支出/收入分类",
                onClick = onOpenCategories,
            )
            SettingsDivider()
            SettingsRow(
                icon = "bell",
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "支付通知记账",
                subtitle = when {
                    state.autoCaptureEnabled && notificationAccessGranted && notificationServiceConnected ->
                        "已连接，正在监听支付通知"
                    state.autoCaptureEnabled && notificationAccessGranted ->
                        "已授权，等待系统连接通知服务"
                    state.autoCaptureEnabled -> "需要通知读取权限"
                    else -> "关闭"
                },
                showChevron = false,
                trailing = {
                    Switch(
                        checked = state.autoCaptureEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                enableAutoCapture()
                            } else {
                                viewModel.setAutoCapture(false)
                            }
                        },
                    )
                },
                onClick = {
                    if (!state.autoCaptureEnabled) {
                        enableAutoCapture()
                    } else {
                        openNotificationAccess()
                    }
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = "bell",
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "识别其他应用",
                subtitle = if (autoCaptureAllApps) {
                    "外卖、电商、银行等支付通知（可能误识别）"
                } else {
                    "仅识别微信和支付宝"
                },
                showChevron = false,
                trailing = {
                    Switch(
                        checked = autoCaptureAllApps,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        onCheckedChange = { enabled ->
                            viewModel.setAutoCaptureAllApps(enabled)
                            if (enabled) {
                                onShowMessage("已开启其他应用识别，请注意通知可能误触发")
                            }
                        },
                    )
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = "spark",
                iconTint = MaterialTheme.colorScheme.primary,
                title = "无障碍增强识别",
                subtitle = if (accessibilityEnabled) {
                    "已开启 · 点此关闭"
                } else {
                    "可选 · 不含银行/云闪付"
                },
                onClick = { showAccessibilityDialog = true },
            )
            SettingsDivider()
            SettingsRow(
                icon = "clock",
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "每日记账提醒",
                subtitle = if (reminder.enabled) "每天 ${reminder.timeLabel}" else "关闭",
                showChevron = false,
                trailing = {
                    androidx.compose.material3.Switch(
                        checked = reminder.enabled,
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        onCheckedChange = ::toggleReminder,
                    )
                },
                onClick = {
                    if (reminder.enabled) showReminderTimeDialog = true
                    else enableReminderWithPermission()
                },
            )
            if (Build.VERSION.SDK_INT >= 31) {
                SettingsDivider()
                SettingsRow(
                    icon = "clock",
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = "精确提醒",
                    subtitle = if (exactAlarmAllowed) {
                        "已允许准点提醒"
                    } else {
                        "系统未允许，提醒可能晚约 1 分钟"
                    },
                    onClick = {
                        if (exactAlarmAllowed) {
                            onShowMessage("精确闹钟已允许")
                        } else {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        android.net.Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }.onFailure {
                                onShowMessage("无法打开精确闹钟设置")
                            }
                        }
                    },
                )
            }

        }

        Spacer(Modifier.height(22.dp))
        SettingsSectionLabel("AI")
        Spacer(Modifier.height(8.dp))
        ZhCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            SettingsRow(
                icon = "ai",
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "AI 助手",
                highlightAlpha = aiPulse.value,
                subtitle = when {
                    state.aiEnabled -> "已开启"
                    state.aiConfigured -> "已配置"
                    else -> "未连接"
                },
                showChevron = false,
                trailing = {
                    Switch(
                        checked = state.aiEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (state.aiConfigured) {
                                    viewModel.setAiEnabled(true)
                                } else {
                                    enableAiOnSave = true
                                    showAiSettingsDialog = true
                                }
                            } else {
                                viewModel.setAiEnabled(false)
                            }
                        },
                    )
                },
                onClick = {
                    if (state.aiEnabled) {
                        viewModel.setAiEnabled(false)
                    } else if (state.aiConfigured) {
                        viewModel.setAiEnabled(true)
                    } else {
                        enableAiOnSave = true
                        showAiSettingsDialog = true
                    }
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = "spark",
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "AI 智能分类",
                subtitle = "Beta · 选择范围，逐笔分类；确认后可复用",
                onClick = onOpenAiCategory,
            )
            SettingsDivider()
            SettingsRow(
                icon = "key",
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "连接配置",
                subtitle = if (state.aiConfigured) {
                    state.aiModel
                } else {
                    "填入 DeepSeek API Key"
                },
                onClick = {
                    enableAiOnSave = false
                    showAiSettingsDialog = true
                },
            )
        }

        Spacer(Modifier.height(22.dp))
        SettingsSectionLabel("数据")
        Spacer(Modifier.height(8.dp))
        ZhCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            SettingsRow(
                icon = "export",
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "导出",
                subtitle = "账单 CSV 或资产 CSV",
                onClick = { showExportDialog = true },
            )
            SettingsDivider()
            SettingsRow(
                icon = "backup",
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "备份数据",
                subtitle = "完整 JSON",
                onClick = { viewModel.exportBackup() },
            )
            SettingsDivider()
            SettingsRow(
                icon = "restore",
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "恢复备份",
                subtitle = "覆盖当前数据",
                onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain")) },
            )
            SettingsDivider()
            SettingsRow(
                icon = "trash",
                iconTint = MaterialTheme.zh.expense,
                title = "清空账本",
                titleColor = MaterialTheme.zh.expense,
                subtitle = "不可撤销",
                onClick = { showClearAllDialog = true },
            )
        }

        Spacer(Modifier.height(22.dp))
        SettingsSectionLabel("备份")
        Spacer(Modifier.height(8.dp))
        ZhCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            SettingsRow(
                icon = "baidu",
                iconTint = MaterialTheme.colorScheme.secondary,
                title = "百度网盘备份",
                subtitle = when {
                    baiduState.loggedIn && baiduState.autoBackup ->
                        "已开启 · 约每 24 小时自动备份一次"
                    baiduState.loggedIn ->
                        "已登录 · 可开启每日自动备份"
                    else ->
                        "登录后，定时把完整数据备份到你的网盘"
                },
                showChevron = false,
                trailing = {
                    Switch(
                        checked = baiduState.autoBackup,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        onCheckedChange = { enabled ->
                            if (enabled && !baiduState.loggedIn) {
                                enableBaiduAutoOnLogin = true
                                showBaiduLoginDialog = true
                            } else {
                                viewModel.setBaiduAutoBackup(enabled)
                            }
                        },
                    )
                },
                onClick = {
                    if (!baiduState.loggedIn) {
                        enableBaiduAutoOnLogin = true
                        showBaiduLoginDialog = true
                    } else {
                        viewModel.setBaiduAutoBackup(!baiduState.autoBackup)
                    }
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = "baidu",
                iconTint = MaterialTheme.colorScheme.secondary,
                title = if (baiduState.loggedIn) {
                    "已登录：${baiduState.userName.ifBlank { "百度网盘用户" }}"
                } else {
                    "登录百度网盘"
                },
                subtitle = if (baiduState.loggedIn) {
                    "设备码授权；退出后自动备份会关闭"
                } else if (baiduState.configured) {
                    "已填写开发者信息 · 点击获取授权码"
                } else {
                    "使用设备码授权，无需输入百度账号密码"
                },
                showChevron = !baiduState.loggedIn,
                trailing = if (baiduState.loggedIn) {
                    {
                        TextButton(onClick = { showBaiduLogoutDialog = true }) {
                            Text(
                                text = "退出",
                                color = MaterialTheme.zh.expense,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                } else {
                    null
                },
                onClick = if (!baiduState.loggedIn) {
                    {
                        enableBaiduAutoOnLogin = false
                        showBaiduLoginDialog = true
                    }
                } else {
                    null
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = "clock",
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "备份保留数量",
                subtitle = if (baiduState.retentionCount <= 0) {
                    "不限制"
                } else {
                    "最近 ${baiduState.retentionCount} 个"
                },
                onClick = { showBaiduRetentionDialog = true },
            )
            SettingsDivider()
            SettingsRow(
                icon = "folder",
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "备份目录",
                subtitle = baiduState.directory,
                onClick = { showBaiduDirectoryDialog = true },
            )
            SettingsDivider()
            SettingsRow(
                icon = "upload",
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "立即备份",
                subtitle = when {
                    baiduBackingUp -> "正在上传到百度网盘…"
                    baiduState.lastBackupAt > 0L -> {
                        val time = Instant.ofEpochMilli(baiduState.lastBackupAt)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
                        "$time · ${baiduState.lastBackupStatus.ifBlank { "已尝试备份" }}"
                    }
                    else -> "每次上传一个文件夹，包含 JSON、账单 CSV 和资产 CSV"
                },
                showChevron = false,
                trailing = {
                    if (baiduBackingUp) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(
                            onClick = {
                                if (!baiduState.loggedIn) {
                                    enableBaiduAutoOnLogin = false
                                    showBaiduLoginDialog = true
                                } else {
                                    viewModel.backupToBaiduNow()
                                }
                            },
                        ) { Text("备份") }
                    }
                },
                onClick = {
                    if (!baiduBackingUp) {
                        if (!baiduState.loggedIn) {
                            enableBaiduAutoOnLogin = false
                            showBaiduLoginDialog = true
                        } else {
                            viewModel.backupToBaiduNow()
                        }
                    }
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = "restore",
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "从网盘恢复",
                subtitle = "最新备份 · 会覆盖当前账本",
                onClick = {
                    if (!baiduState.loggedIn) {
                        enableBaiduAutoOnLogin = false
                        showBaiduLoginDialog = true
                    } else {
                        showBaiduRestoreDialog = true
                        viewModel.loadLatestBaiduBackup()
                    }
                },
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = "百度 API 需先在开放平台创建应用并申请上传权限，登录不会自动生成。没有开发者应用时，请使用「备份数据」。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
            lineHeight = 17.sp,
        )

        Spacer(Modifier.height(22.dp))
        SettingsSectionLabel("关于")
        Spacer(Modifier.height(8.dp))
        ZhCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            SettingsRow(
                icon = "lock",
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "隐私说明",
                subtitle = null,
                onClick = { showPrivacyDialog = true },
            )
            SettingsDivider()
            SettingsRow(
                icon = "restore",
                iconTint = MaterialTheme.colorScheme.primary,
                title = "检查更新",
                onClick = viewModel::checkForUpdates,
                trailing = {
                    if (updateState.checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = "restore",
                iconTint = MaterialTheme.colorScheme.secondary,
                title = "国内镜像下载",
                subtitle = "通过 ghfast.top 加速更新包",
                showChevron = false,
                trailing = {
                    Switch(
                        checked = updateUseMirror,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        onCheckedChange = viewModel::setUpdateUseMirror,
                    )
                },
                onClick = { viewModel.setUpdateUseMirror(!updateUseMirror) },
            )
            SettingsDivider()
            SettingsRow(
                icon = "export",
                iconTint = MaterialTheme.colorScheme.primary,
                title = "GitHub 仓库",
                subtitle = "github.com/jianhe666/wao-app",
                onClick = {
                    openUrl(
                        context = context,
                        url = "https://github.com/jianhe666/wao-app",
                        onError = onShowMessage,
                    )
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = "app",
                iconTint = MaterialTheme.zh.gold,
                title = "哇哦记账",
                subtitle = null,
                showChevron = false,
                trailing = {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            SettingsDivider()
        }
        Spacer(Modifier.height(30.dp))
    }

    if (showBudgetDialog) {
        BudgetDialog(
            currentBudgetCents = state.budgetCents,
            onDismiss = { showBudgetDialog = false },
            onConfirm = { value ->
                viewModel.saveBudget(value)
                showBudgetDialog = false
            },
        )
    }
    if (showClearAllDialog) {
        ClearLedgerDialog(
            onDismiss = { showClearAllDialog = false },
            onConfirm = {
                viewModel.clearAllData()
                showClearAllDialog = false
            },
        )
    }
    if (showRestoreDialog && pendingRestore != null) {
        ConfirmDialog(
            title = "恢复这份备份？",
            body = "当前账本会被备份中的内容完全覆盖。建议先导出当前数据。",
            confirmText = "恢复",
            destructive = true,
            onDismiss = {
                showRestoreDialog = false
                pendingRestore = null
            },
            onConfirm = {
                pendingRestore?.let(viewModel::restoreBackup)
                showRestoreDialog = false
                pendingRestore = null
            },
        )
    }
    if (showAiSettingsDialog) {
        AiSettingsDialog(
            initialApiKey = state.aiApiKey,
            initialBaseUrl = state.aiBaseUrl,
            initialModel = state.aiModel,
            enableAfterSave = enableAiOnSave,
            onDismiss = { showAiSettingsDialog = false },
            onSave = { apiKey, baseUrl, model, enable ->
                viewModel.saveAiConfig(apiKey, baseUrl, model, enable)
                showAiSettingsDialog = false
            },
            onClear = {
                viewModel.clearAiConfig()
                showAiSettingsDialog = false
            },
        )
    }
    if (showBaiduLoginDialog) {
        BaiduLoginDialog(
            initialAppKey = baiduState.appKey,
            initialSecretKey = baiduState.secretKey,
            login = baiduLogin,
            enableAutoBackup = enableBaiduAutoOnLogin,
            onStart = { appKey, secretKey ->
                viewModel.startBaiduLogin(
                    appKey = appKey,
                    secretKey = secretKey,
                    enableAutoBackup = enableBaiduAutoOnLogin,
                )
            },
            onDismiss = {
                val shouldCancel = baiduLogin.phase == BaiduLoginPhase.REQUESTING ||
                    baiduLogin.phase == BaiduLoginPhase.WAITING
                if (shouldCancel) viewModel.cancelBaiduLogin()
                else viewModel.dismissBaiduLogin()
                showBaiduLoginDialog = false
            },
        )
    }
    if (showBaiduDirectoryDialog) {
        BaiduDirectoryDialog(
            initialPath = baiduState.directory,
            onDismiss = { showBaiduDirectoryDialog = false },
            onSave = { path ->
                viewModel.saveBaiduDirectory(path)
                showBaiduDirectoryDialog = false
            },
        )
    }
    if (showReminderTimeDialog) {
        ReminderTimeDialog(
            initialHour = reminder.hour,
            initialMinute = reminder.minute,
            onDismiss = { showReminderTimeDialog = false },
            onConfirm = { hour, minute ->
                viewModel.setDailyReminderTime(hour, minute)
                showReminderTimeDialog = false
            },
        )
    }
    if (showAccessibilityDialog) {
        GlassAlertDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(if (accessibilityEnabled) "关闭无障碍增强" else "开启无障碍增强？")
            },
            text = {
                Text(
                    text = if (accessibilityEnabled) {
                        "请在系统的「无障碍」设置里关闭「哇哦记账 · 支付识别增强」。\n\n关闭后哇哦记账会立即停止读取支付结果页面。"
                    } else {
                        "这是可选功能，默认关闭。开启后，哇哦记账只读取微信、支付宝、购物和外卖应用的当前页面支付结果文字，用于本地自动记账；不截屏、不上传，也不会读取银行/云闪付。\n\n" +
                            "需要在系统「无障碍」设置里手动允许哇哦记账。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAccessibilityDialog = false
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }.onFailure {
                            onShowMessage("无法打开无障碍设置")
                        }
                    },
                ) { Text(if (accessibilityEnabled) "去系统关闭" else "去系统开启") }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDialog = false }) { Text("取消") }
            },
        )
    }
    if (showBaiduRetentionDialog) {
        BaiduRetentionDialog(
            initialCount = baiduState.retentionCount,
            onDismiss = { showBaiduRetentionDialog = false },
            onConfirm = { count ->
                viewModel.setBaiduRetention(count)
                showBaiduRetentionDialog = false
            },
        )
    }
    if (showBaiduRestoreDialog) {
        BaiduRestoreDialog(
            state = baiduRestore,
            onDismiss = {
                showBaiduRestoreDialog = false
                viewModel.clearBaiduRestore()
            },
            onConfirm = viewModel::restoreLatestBaiduBackup,
        )
    }
    if (showBaiduLogoutDialog) {
        ConfirmDialog(
            title = "退出百度网盘？",
            body = "退出后会关闭自动备份，并清除这台手机上的百度授权信息。已经上传到网盘的备份不会删除。",
            confirmText = "退出",
            destructive = true,
            onDismiss = { showBaiduLogoutDialog = false },
            onConfirm = {
                viewModel.logoutBaidu()
                showBaiduLogoutDialog = false
            },
        )
    }
    if (showExportDialog) {
        GlassAlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("导出数据") },
            text = { Text("选择要导出的 CSV。账单和资产会分别生成表格，互不混在一起。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportDialog = false
                        viewModel.exportCsv()
                    },
                ) { Text("账单 CSV") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExportDialog = false
                        viewModel.exportAssetCsv()
                    },
                ) { Text("资产 CSV") }
            },
        )
    }

    if (showPrivacyDialog) {
        GlassAlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("你的账，只属于你") },
            text = {
                Text(
                    text = "哇哦记账默认完全离线，账目只保存在这台手机。\n\n" +
                        "支付通知自动记账默认关闭；开启后，只有支付宝和微信支付的通知会在本机被解析，" +
                        "不会读取聊天内容。\n\n" +
                        "AI 默认关闭。开启并配置 API Key 后，只有在你使用对话或图片识别时，" +
                        "相关文字、图片才会发送到你配置的 API；API Key 仅保存在本机，不写入备份。\n\n" +
                        "百度网盘自动备份默认关闭。登录后由你主动开启，备份文件会上传到你的百度网盘 " +
                        "/apps/ 应用目录；百度网盘的 AppKey、SecretKey 与 token 仅保存在本机，不写入哇哦记账备份。\n\n" +
                        "导出、备份和分享都由你主动发起。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("知道了") }
            },
        )
    }

    updateState.release?.let { release ->
        GlassDialog(onDismissRequest = viewModel::dismissUpdate) {
            Text(
                text = "发现新版本 ${release.tagName}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (release.notes.isBlank()) {
                    Text("有新版本可以更新。")
                } else {
                    Text(MarkdownText.toPlainText(release.notes))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (release.apkUrl == null) {
                        "这个 Release 还没有附带 APK，将打开 GitHub 页面。"
                    } else {
                        "将打开 GitHub 下载 APK。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = viewModel::dismissUpdate) { Text("稍后") }
                TextButton(
                    onClick = {
                        openUrl(
                            context = context,
                            url = UpdateChecker.downloadUrl(
                                release.apkUrl ?: release.releaseUrl,
                                updateUseMirror,
                            ),
                            onError = onShowMessage,
                        )
                        viewModel.dismissUpdate()
                    },
                ) {
                    Text(if (release.apkUrl == null) "打开 GitHub" else "下载 APK")
                }
            }
        }
    }

    updateState.error?.let { error ->
        GlassDialog(onDismissRequest = viewModel::dismissUpdate) {
            Text(
                text = "检查更新失败",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(error)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = viewModel::dismissUpdate) { Text("知道了") }
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: String,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    showChevron: Boolean = true,
    highlightAlpha: Float = 0f,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f * highlightAlpha),
                RoundedCornerShape(14.dp),
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon == "app") {
            AppMark(size = 30.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(iconTint.copy(alpha = 0.10f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                SettingsIcon(icon = icon, tint = iconTint)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = titleColor,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (showChevron) {
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsIcon(icon: String, tint: Color) {
    if (icon == "baidu") {
        BaiduMark(size = 19.dp)
        return
    }
    val vector = when (icon) {
        "export" -> Icons.Default.Share
        "backup" -> Icons.Default.Lock
        "restore" -> Icons.Default.Refresh
        "trash" -> Icons.Default.Delete
        "lock" -> Icons.Default.Lock
        "heart" -> Icons.Default.Favorite
        else -> null
    }
    if (vector != null) {
        Icon(
            imageVector = vector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(17.dp),
        )
    } else {
        val painter = when (icon) {
            "wallet" -> painterResource(R.drawable.ic_wallet)
            "bell" -> painterResource(R.drawable.ic_bell)
            "spark" -> painterResource(R.drawable.ic_spark)
            "ai", "key" -> painterResource(R.drawable.ic_spark)
            "folder" -> painterResource(R.drawable.ic_folder)
            "clock" -> painterResource(R.drawable.ic_clock)
            "upload" -> painterResource(R.drawable.ic_upload)
            else -> painterResource(R.drawable.ic_spark)
        }
        Icon(
            painter = painter,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp, end = 14.dp),
        thickness = 1.dp,
        color = MaterialTheme.zh.hairline,
    )
}

private fun isPaymentAccessibilityEnabled(context: android.content.Context): Boolean {
    val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any {
            it.resolveInfo?.serviceInfo?.name == PaymentAccessibilityService::class.java.name
        }
}

private fun canScheduleExactAlarms(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

private fun isNotificationAccessGranted(context: android.content.Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)


@Composable
private fun AiSettingsDialog(
    initialApiKey: String,
    initialBaseUrl: String,
    initialModel: String,
    enableAfterSave: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Boolean) -> Unit,
    onClear: () -> Unit,
) {
    var apiKey by remember(initialApiKey) { mutableStateOf(initialApiKey) }
    var baseUrl by remember(initialBaseUrl) { mutableStateOf(initialBaseUrl) }
    var model by remember(initialModel) { mutableStateOf(initialModel) }
    var localError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val canSave = apiKey.isNotBlank() &&
        baseUrl.startsWith("https://") &&
        model.isNotBlank()

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                Text("AI 服务设置")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "默认适配 DeepSeek，也兼容 OpenAI 风格的接口。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { input ->
                        apiKey = input.trim()
                        if (model.isBlank() || model == "deepseek-chat") {
                            model = "deepseek-flash"
                        }
                        localError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    placeholder = { Text("sk-…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "密钥只保存在本机应用私有目录，不会写入备份。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it.trim()
                        localError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API 地址") },
                    placeholder = { Text("https://api.deepseek.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = {
                        model = it.trim()
                        localError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型") },
                    placeholder = { Text("deepseek-flash") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            baseUrl = "https://api.deepseek.com"
                            model = "deepseek-flash"
                            localError = null
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        Text("恢复 DeepSeek 默认配置")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://platform.deepseek.com/api_keys"),
                                    ),
                                )
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        Text("获取 DeepSeek Key")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.zh.chip.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = if (enableAfterSave) {
                            "保存后会开启 AI。账单文本和对话内容将发送到上面的 API 地址。"
                        } else {
                            "启用 AI 后，账单文本和对话内容会发送到上面的 API 地址。请确认你信任该服务商。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                if (localError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = localError.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.zh.expense,
                    )
                }
                if (initialApiKey.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = onClear,
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        Text(
                            text = "清除密钥并关闭 AI",
                            color = MaterialTheme.zh.expense,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!baseUrl.startsWith("https://")) {
                        localError = "API 地址必须以 https:// 开头"
                    } else if (apiKey.isBlank()) {
                        localError = "请填写 API Key"
                    } else {
                        onSave(apiKey, baseUrl, model, enableAfterSave)
                    }
                },
                enabled = canSave,
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}


@Composable
private fun BaiduLoginDialog(
    initialAppKey: String,
    initialSecretKey: String,
    login: BaiduLoginUiState,
    enableAutoBackup: Boolean,
    onStart: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var appKey by remember(initialAppKey) { mutableStateOf(initialAppKey) }
    var secretKey by remember(initialSecretKey) { mutableStateOf(initialSecretKey) }
    var browserError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val canStart = appKey.isNotBlank() && secretKey.isNotBlank() &&
        login.phase != BaiduLoginPhase.REQUESTING

    val dismissAction = {
        onDismiss()
    }

    GlassAlertDialog(
        onDismissRequest = dismissAction,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("连接百度网盘") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                when (login.phase) {
                    BaiduLoginPhase.IDLE,
                    BaiduLoginPhase.ERROR,
                    BaiduLoginPhase.REQUESTING,
                    -> {
                        Text(
                            text = "百度网盘使用设备码授权：哇哦记账会显示一个用户码，你到百度授权页输入即可。应用不会接触你的百度密码。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "首次使用需要填写百度网盘开放平台的开发者信息。在开放平台创建应用并申请上传权限后，可获得 AppKey 和 SecretKey。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.zh.gold.copy(alpha = 0.13f))
                                .padding(10.dp),
                        ) {
                            Text(
                                text = "百度账号登录不会自动创建 API。AppKey / SecretKey 只能在开放平台注册应用后获得，客户端无法绕过。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = appKey,
                            onValueChange = { appKey = it.trim() },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("AppKey") },
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = secretKey,
                            onValueChange = { secretKey = it.trim() },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("SecretKey") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://pan.baidu.com/union/console/applist"),
                                        ),
                                    )
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 0.dp),
                        ) {
                            Text("打开百度网盘开放平台控制台")
                        }
                        if (enableAutoBackup) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "登录成功后会同时开启每日自动备份。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (login.phase == BaiduLoginPhase.ERROR) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = login.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.zh.expense,
                            )
                        }
                        if (login.phase == BaiduLoginPhase.REQUESTING) {
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "正在获取授权码…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    BaiduLoginPhase.WAITING -> {
                        Text(
                            text = "请在浏览器中打开百度授权页，登录百度账号并输入下面的用户码。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.zh.chip,
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = login.userCode,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "用户码",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    clipboard.setText(AnnotatedString(login.userCode))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("复制用户码")
                            }
                            Button(
                                onClick = {
                                    val url = Uri.parse(login.verificationUrl)
                                        .buildUpon()
                                        .appendQueryParameter("display", "mobile")
                                        .appendQueryParameter("code", login.userCode)
                                        .build()
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, url))
                                    }.onFailure {
                                        browserError = "没有找到可用浏览器，请手动打开 ${login.verificationUrl}"
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("打开授权页")
                            }
                        }
                        if (browserError != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = browserError.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.zh.expense,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "等待授权完成，请保持此页面打开…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    BaiduLoginPhase.SUCCESS -> {
                        Text(
                            text = "登录成功",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.zh.income,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "欢迎，${login.userName.ifBlank { "百度网盘用户" }}。" +
                                if (enableAutoBackup) " 自动备份已开启。" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (login.phase) {
                BaiduLoginPhase.SUCCESS -> {
                    TextButton(onClick = onDismiss) { Text("完成") }
                }

                BaiduLoginPhase.WAITING -> {
                    TextButton(onClick = onDismiss) { Text("取消") }
                }

                BaiduLoginPhase.REQUESTING -> {
                    TextButton(onClick = {}, enabled = false) { Text("请稍候") }
                }

                else -> {
                    TextButton(
                        onClick = { onStart(appKey, secretKey) },
                        enabled = canStart,
                    ) { Text("获取授权码") }
                }
            }
        },
        dismissButton = {
            if (login.phase != BaiduLoginPhase.SUCCESS) {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

@Composable
private fun ClearLedgerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var confirmation by remember { mutableStateOf("") }
    val expected = "确认清空"
    val canConfirm = confirmation.trim() == expected
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("清空所有账目？") },
        text = {
            Column {
                Text(
                    text = "这会删除全部账目、预算和分类记忆，且无法撤销。建议先备份数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "请输入「确认清空」后才能继续：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.zh.expense,
                )
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it.take(16) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(expected) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = canConfirm,
            ) {
                Text(
                    text = "全部清空",
                    color = if (canConfirm) MaterialTheme.zh.expense else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ReminderTimeDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var hourText by remember(initialHour) { mutableStateOf("%02d".format(initialHour)) }
    var minuteText by remember(initialMinute) { mutableStateOf("%02d".format(initialMinute)) }
    val hour = hourText.toIntOrNull()
    val minute = minuteText.toIntOrNull()
    val valid = hour != null && minute != null && hour in 0..23 && minute in 0..59
    val quickTimes = listOf(8 to 30, 12 to 30, 20 to 0, 21 to 30)
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("每日提醒时间") },
        text = {
            Column {
                Text(
                    text = "每天提醒你记一笔，别让账拖到周末。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { hourText = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.width(78.dp),
                        singleLine = true,
                        label = { Text("时") },
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                    )
                    Text(
                        text = " : ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { minuteText = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.width(78.dp),
                        singleLine = true,
                        label = { Text("分") },
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    quickTimes.forEach { (h, m) ->
                        Text(
                            text = "%02d:%02d".format(h, m),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    RoundedCornerShape(50),
                                )
                                .clickable {
                                    hourText = "%02d".format(h)
                                    minuteText = "%02d".format(m)
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onConfirm(hour!!, minute!!) },
                enabled = valid,
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun BaiduRestoreDialog(
    state: BaiduRestoreUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val backup = state.backup
    GlassAlertDialog(
        onDismissRequest = { if (!state.restoring) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("从百度网盘恢复") },
        text = {
            Column {
                when {
                    state.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = "正在查找最新备份…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    backup != null -> {
                        Text(
                            text = backup.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${formatBaiduTime(backup.modifiedAt)} · ${formatBaiduSize(backup.size)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "恢复会覆盖当前账本、预算和分类规则，且无法撤销。建议先导出当前数据。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.zh.expense,
                        )
                    }
                }
                if (state.error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.zh.expense,
                    )
                }
                if (state.restoring) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在下载并恢复…", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = backup != null && !state.loading && !state.restoring,
            ) {
                Text(if (state.restoring) "恢复中…" else "覆盖并恢复")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BaiduRetentionDialog(
    initialCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var raw by remember(initialCount) {
        mutableFloatStateOf(if (initialCount <= 0) 51f else initialCount.coerceIn(1, 50).toFloat())
    }
    val count = raw.roundToInt()
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("备份保留数量") },
        text = {
            Column {
                Text(
                    text = "自动清理较早的备份，只保留最近的数量。旧备份会从网盘永久删除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.zh.chip.copy(alpha = 0.50f))
                        .border(1.dp, MaterialTheme.zh.glassBorder.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = if (count >= 51) "不限制" else "最近 $count 个",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = raw,
                        onValueChange = { raw = it },
                        valueRange = 1f..51f,
                        steps = 49,
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            thumbColor = Color.Transparent,
                        ),
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .shadow(8.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.78f))
                                    .border(1.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                )
                            }
                        },
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text("1 个", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("不限", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(if (count >= 51) 0 else count) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun formatBaiduTime(millis: Long): String {
    if (millis <= 0L) return "时间未知"
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
}

private fun formatBaiduSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes < 1024L -> "${bytes} B"
    bytes < 1024L * 1024L -> "${bytes / 1024} KB"
    else -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
}

@Composable
private fun BaiduDirectoryDialog(
    initialPath: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var path by remember(initialPath) { mutableStateOf(initialPath) }
    val valid = path.startsWith("/apps/") && !path.contains("..")
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("备份目录") },
        text = {
            Column {
                Text(
                    text = "必须位于百度网盘开放平台分配给应用的 /apps/ 目录内。例如 /apps/你的应用名/哇哦记账备份。" +
                        "目录不存在时会自动创建。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("网盘目录") },
                    singleLine = true,
                    isError = path.isNotBlank() && !valid,
                )
                if (path.isNotBlank() && !valid) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "目录必须以 /apps/ 开头，且不能包含 ..",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.zh.expense,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(path) },
                enabled = valid,
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}


private fun openUrl(context: android.content.Context, url: String, onError: (String) -> Unit) {
    if (url.isBlank()) {
        onError("没有可打开的更新地址")
        return
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        onError("无法打开浏览器，请手动访问 GitHub Releases")
    }
}
