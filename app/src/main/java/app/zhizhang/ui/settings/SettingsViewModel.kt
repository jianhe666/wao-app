package app.zhizhang.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zhizhang.ZhizhangApp
import app.zhizhang.backup.BaiduBackupScheduler
import app.zhizhang.data.BackupManager
import app.zhizhang.data.BaiduAuthException
import app.zhizhang.data.BaiduException
import app.zhizhang.data.BaiduNetworkException
import app.zhizhang.data.BaiduRemoteBackup
import app.zhizhang.data.BaiduState
import app.zhizhang.data.Formats
import app.zhizhang.data.SettingsRepository
import app.zhizhang.reminder.DailyReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReminderUiState(
    val enabled: Boolean = false,
    val hour: Int = 20,
    val minute: Int = 30,
) {
    val timeLabel: String get() = "%02d:%02d".format(hour, minute)
}

data class SettingsUiState(
    val loading: Boolean = true,
    val transactionCount: Int = 0,
    val autoCount: Int = 0,
    val budgetCents: Long = 0L,
    val ruleCount: Int = 0,
    val autoCaptureEnabled: Boolean = false,
    val aiEnabled: Boolean = false,
    val aiApiKey: String = "",
    val aiBaseUrl: String = SettingsRepository.DEFAULT_AI_BASE_URL,
    val aiModel: String = SettingsRepository.DEFAULT_AI_MODEL,
) {
    val aiConfigured: Boolean get() = aiApiKey.isNotBlank()
    val aiMaskedKey: String
        get() = when {
            aiApiKey.isBlank() -> "未配置"
            aiApiKey.length <= 10 -> "••••••"
            else -> "${aiApiKey.take(3)}••••${aiApiKey.takeLast(4)}"
        }
}

enum class BaiduLoginPhase {
    IDLE,
    REQUESTING,
    WAITING,
    SUCCESS,
    ERROR,
}

data class BaiduLoginUiState(
    val phase: BaiduLoginPhase = BaiduLoginPhase.IDLE,
    val userCode: String = "",
    val verificationUrl: String = "",
    val userName: String = "",
    val error: String = "",
)

data class BaiduRestoreUiState(
    val loading: Boolean = false,
    val backup: BaiduRemoteBackup? = null,
    val error: String? = null,
    val restoring: Boolean = false,
    val completedCount: Int? = null,
)

sealed interface SettingsEvent {
    data class ShareFile(val uri: Uri, val mime: String, val title: String) : SettingsEvent
    data class Message(val text: String) : SettingsEvent
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ZhizhangApp
    private val repository = app.repository
    private val settings = app.settings
    private val backupManager: BackupManager = app.backupManager
    private val baiduRepository = app.baiduRepository
    private val baiduPanService = app.baiduPanService

    private val events = Channel<SettingsEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val baiduState = baiduRepository.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BaiduState(),
    )

    private val _baiduLogin = MutableStateFlow(BaiduLoginUiState())
    val baiduLogin = _baiduLogin.asStateFlow()
    private var baiduLoginJob: Job? = null

    private val _baiduBackingUp = MutableStateFlow(false)
    val baiduBackingUp = _baiduBackingUp.asStateFlow()

    private val _baiduRestore = MutableStateFlow(BaiduRestoreUiState())
    val baiduRestore = _baiduRestore.asStateFlow()

    private val baseState = combine(
        repository.observeCount(),
        repository.observeAutoCount(),
        settings.budgetCents,
        repository.observeRuleCount(),
        settings.autoCaptureEnabled,
    ) { count, autoCount, budget, ruleCount, autoEnabled ->
        SettingsUiState(
            loading = false,
            transactionCount = count,
            autoCount = autoCount,
            budgetCents = budget,
            ruleCount = ruleCount,
            autoCaptureEnabled = autoEnabled,
        )
    }

    private val aiState = combine(
        settings.aiEnabled,
        settings.aiApiKey,
        settings.aiBaseUrl,
        settings.aiModel,
    ) { enabled, apiKey, baseUrl, model ->
        AiState(enabled = enabled, apiKey = apiKey, baseUrl = baseUrl, model = model)
    }

    val autoCaptureAllApps = settings.autoCaptureAllApps.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    val reminderState = combine(
        settings.dailyReminderEnabled,
        settings.dailyReminderHour,
        settings.dailyReminderMinute,
    ) { enabled, hour, minute ->
        ReminderUiState(enabled = enabled, hour = hour, minute = minute)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReminderUiState(),
    )

    val uiState = combine(baseState, aiState) { base, ai ->
        base.copy(
            aiEnabled = ai.enabled,
            aiApiKey = ai.apiKey,
            aiBaseUrl = ai.baseUrl,
            aiModel = ai.model,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    private data class AiState(
        val enabled: Boolean,
        val apiKey: String,
        val baseUrl: String,
        val model: String,
    )

    fun startBaiduLogin(
        appKey: String,
        secretKey: String,
        enableAutoBackup: Boolean,
    ) {
        if (appKey.isBlank() || secretKey.isBlank()) {
            viewModelScope.launch {
                events.send(SettingsEvent.Message("请先填写 AppKey 和 SecretKey"))
            }
            return
        }
        baiduLoginJob?.cancel()
        _baiduLogin.value = BaiduLoginUiState(phase = BaiduLoginPhase.REQUESTING)
        baiduLoginJob = viewModelScope.launch {
            try {
                baiduRepository.saveCredentials(appKey, secretKey)
                val auth = baiduPanService.requestDeviceCode(appKey)
                _baiduLogin.value = BaiduLoginUiState(
                    phase = BaiduLoginPhase.WAITING,
                    userCode = auth.userCode,
                    verificationUrl = auth.verificationUrl,
                )
                val token = baiduPanService.pollForToken(appKey, secretKey, auth)
                val userName = runCatching {
                    baiduPanService.fetchUserName(token.accessToken)
                }.getOrDefault("百度网盘用户")
                baiduRepository.saveAuth(
                    accessToken = token.accessToken,
                    refreshToken = token.refreshToken,
                    expiresAt = token.expiresAt,
                    userName = userName,
                )
                _baiduLogin.value = BaiduLoginUiState(
                    phase = BaiduLoginPhase.SUCCESS,
                    userName = userName,
                )
                events.send(SettingsEvent.Message("百度网盘登录成功"))
                val shouldAuto = enableAutoBackup || baiduRepository.current().autoBackup
                if (shouldAuto) {
                    baiduRepository.setAutoBackup(true)
                    BaiduBackupScheduler.schedule(getApplication())
                }
            } catch (error: CancellationException) {
                _baiduLogin.value = BaiduLoginUiState(phase = BaiduLoginPhase.IDLE)
                throw error
            } catch (error: BaiduAuthException) {
                _baiduLogin.value = BaiduLoginUiState(
                    phase = BaiduLoginPhase.ERROR,
                    error = error.message ?: "百度授权失败",
                )
            } catch (error: Exception) {
                _baiduLogin.value = BaiduLoginUiState(
                    phase = BaiduLoginPhase.ERROR,
                    error = error.message ?: "百度授权失败，请稍后再试",
                )
            }
        }
    }

    fun cancelBaiduLogin() {
        baiduLoginJob?.cancel()
        baiduLoginJob = null
        _baiduLogin.value = BaiduLoginUiState(phase = BaiduLoginPhase.IDLE)
    }

    fun dismissBaiduLogin() {
        _baiduLogin.value = BaiduLoginUiState(phase = BaiduLoginPhase.IDLE)
    }

    fun logoutBaidu() {
        viewModelScope.launch {
            BaiduBackupScheduler.cancel(getApplication())
            baiduRepository.logout()
            events.send(SettingsEvent.Message("已退出百度网盘"))
        }
    }

    fun setBaiduAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            val state = baiduRepository.current()
            if (enabled && !state.loggedIn) {
                events.send(SettingsEvent.Message("请先登录百度网盘"))
                return@launch
            }
            baiduRepository.setAutoBackup(enabled)
            val context = getApplication<Application>()
            if (enabled) BaiduBackupScheduler.schedule(context) else BaiduBackupScheduler.cancel(context)
            events.send(
                SettingsEvent.Message(
                    if (enabled) "已开启每日自动备份（约每 24 小时一次）" else "已关闭百度网盘自动备份",
                ),
            )
        }
    }

    fun saveBaiduDirectory(path: String) {
        val clean = path.trim().ifBlank { baiduState.value.directory }
        if (!clean.startsWith("/apps/") || clean.contains("..")) {
            viewModelScope.launch {
                events.send(SettingsEvent.Message("备份目录必须以 /apps/ 开头"))
            }
            return
        }
        viewModelScope.launch {
            baiduRepository.setDirectory(clean.trimEnd('/'))
            events.send(SettingsEvent.Message("备份目录已更新"))
        }
    }

    fun backupToBaiduNow() {
        if (_baiduBackingUp.value) return
        val snapshot = baiduState.value
        if (!snapshot.loggedIn) {
            viewModelScope.launch { events.send(SettingsEvent.Message("请先登录百度网盘")) }
            return
        }
        _baiduBackingUp.value = true
        viewModelScope.launch {
            try {
                val json = backupManager.buildBackupJson()
                val result = baiduPanService.uploadBackup(json, snapshot.directory)
                baiduRepository.markBackupSuccess(result.remotePath)
                events.send(SettingsEvent.Message("已备份到 ${result.remotePath}"))
            } catch (error: BaiduAuthException) {
                baiduRepository.markBackupFailure(error.message ?: "授权失效")
                events.send(SettingsEvent.Message("备份失败：${error.message ?: "请重新登录百度网盘"}"))
            } catch (error: BaiduNetworkException) {
                baiduRepository.markBackupFailure(error.message ?: "网络错误")
                events.send(SettingsEvent.Message("备份失败：${error.message ?: "请检查网络"}"))
            } catch (error: BaiduException) {
                baiduRepository.markBackupFailure(error.message ?: "备份失败")
                events.send(SettingsEvent.Message("备份失败：${error.message ?: "请稍后再试"}"))
            } catch (error: Exception) {
                baiduRepository.markBackupFailure("备份失败")
                events.send(SettingsEvent.Message("备份失败，请稍后再试"))
            } finally {
                _baiduBackingUp.value = false
            }
        }
    }

    fun saveBudget(input: String) {
        val cents = Formats.amountInputToCents(input)
        viewModelScope.launch {
            settings.setBudget(cents)
            events.send(SettingsEvent.Message(if (cents > 0) "预算已更新" else "已取消月度预算"))
        }
    }

    fun setAiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && settings.aiApiKey.first().isBlank()) {
                events.send(SettingsEvent.Message("请先填写 API Key"))
                return@launch
            }
            settings.setAiEnabled(enabled)
            events.send(
                SettingsEvent.Message(
                    if (enabled) "AI 已开启" else "AI 已关闭",
                ),
            )
        }
    }

    fun saveAiConfig(
        apiKey: String,
        baseUrl: String,
        model: String,
        enableAfterSave: Boolean,
    ) {
        val cleanKey = apiKey.trim()
        val cleanUrl = baseUrl.trim().ifBlank { SettingsRepository.DEFAULT_AI_BASE_URL }
        val cleanModel = model.trim().ifBlank { SettingsRepository.DEFAULT_AI_MODEL }
        val validation = when {
            cleanKey.isBlank() -> "API Key 不能为空"
            !cleanUrl.startsWith("https://") -> "API 地址必须以 https:// 开头"
            cleanModel.isBlank() -> "模型名称不能为空"
            else -> null
        }
        if (validation != null) {
            viewModelScope.launch { events.send(SettingsEvent.Message(validation)) }
            return
        }
        viewModelScope.launch {
            settings.setAiConfig(cleanKey, cleanUrl, cleanModel)
            if (enableAfterSave) settings.setAiEnabled(true)
            events.send(
                SettingsEvent.Message(
                    if (enableAfterSave) "API 已保存，AI 已开启" else "AI 设置已保存",
                ),
            )
        }
    }

    fun clearAiConfig() {
        viewModelScope.launch {
            settings.clearAiConfig()
            events.send(SettingsEvent.Message("已清除 API Key 并关闭 AI"))
        }
    }

    fun setAutoCapture(enabled: Boolean) {
        viewModelScope.launch {
            settings.setAutoCapture(enabled)
            events.send(
                SettingsEvent.Message(
                    if (enabled) "自动记账已开启，请在系统中允许通知读取" else "自动记账已关闭",
                ),
            )
        }
    }

    fun clearRules() {
        viewModelScope.launch {
            repository.clearRules()
            events.send(SettingsEvent.Message("已清空自动分类的学习记录"))
        }
    }

    fun loadDemoData() {
        viewModelScope.launch {
            val count = repository.addDemoData()
            events.send(
                SettingsEvent.Message(
                    if (count > 0) "已加入 $count 笔演示账目" else "账本里已经有账目了",
                ),
            )
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAll()
            settings.setBudget(0L)
            events.send(SettingsEvent.Message("账本已经清空"))
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            runCatching { backupManager.exportCsv() }
                .onSuccess { uri ->
                    events.send(SettingsEvent.ShareFile(uri, "text/csv", "导出有数账单"))
                }
                .onFailure {
                    events.send(SettingsEvent.Message("导出失败，请稍后再试"))
                }
        }
    }

    fun exportBackup() {
        viewModelScope.launch {
            runCatching { backupManager.exportJson() }
                .onSuccess { uri ->
                    events.send(SettingsEvent.ShareFile(uri, "application/json", "备份有数数据"))
                }
                .onFailure {
                    events.send(SettingsEvent.Message("备份失败，请稍后再试"))
                }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { backupManager.restoreJson(uri) }
                .onSuccess { count ->
                    events.send(SettingsEvent.Message("已恢复 $count 笔账目"))
                }
                .onFailure { error ->
                    events.send(
                        SettingsEvent.Message(error.message ?: "恢复失败，请检查备份文件"),
                    )
                }
        }
    }

    fun setAutoCaptureAllApps(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoCaptureAllApps(enabled) }
    }

    fun setDailyReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setDailyReminderEnabled(enabled)
            if (enabled) {
                DailyReminderScheduler.schedule(
                    getApplication(),
                    settings.dailyReminderHour.first(),
                    settings.dailyReminderMinute.first(),
                )
                events.send(SettingsEvent.Message("每日记账提醒已开启"))
            } else {
                DailyReminderScheduler.cancel(getApplication())
                events.send(SettingsEvent.Message("每日记账提醒已关闭"))
            }
        }
    }

    fun rescheduleDailyReminder() {
        viewModelScope.launch {
            if (settings.dailyReminderEnabled.first()) {
                DailyReminderScheduler.schedule(
                    getApplication(),
                    settings.dailyReminderHour.first(),
                    settings.dailyReminderMinute.first(),
                )
            }
        }
    }

    fun setDailyReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settings.setDailyReminderTime(hour, minute)
            if (settings.dailyReminderEnabled.first()) {
                DailyReminderScheduler.schedule(getApplication(), hour, minute)
            }
            events.send(
                SettingsEvent.Message("提醒时间已设为 %02d:%02d".format(hour, minute)),
            )
        }
    }

    fun setBaiduRetention(count: Int) {
        viewModelScope.launch {
            baiduRepository.setRetentionCount(count)
            events.send(
                SettingsEvent.Message(
                    if (count <= 0) "已设置为不限制备份数量" else "将保留最近 $count 个备份",
                ),
            )
        }
    }

    fun loadLatestBaiduBackup() {
        viewModelScope.launch {
            _baiduRestore.value = BaiduRestoreUiState(loading = true)
            try {
                val directory = baiduRepository.current().directory
                val latest = baiduPanService.listBackups(directory).firstOrNull()
                _baiduRestore.value = BaiduRestoreUiState(
                    backup = latest,
                    error = if (latest == null) "网盘目录里还没有备份" else null,
                )
            } catch (error: BaiduAuthException) {
                _baiduRestore.value = BaiduRestoreUiState(error = "百度网盘授权已失效，请重新登录")
            } catch (error: BaiduNetworkException) {
                _baiduRestore.value = BaiduRestoreUiState(error = error.message ?: "网络连接失败")
            } catch (error: BaiduException) {
                _baiduRestore.value = BaiduRestoreUiState(error = error.message ?: "读取备份失败")
            } catch (error: Exception) {
                _baiduRestore.value = BaiduRestoreUiState(error = "读取备份失败，请稍后再试")
            }
        }
    }

    fun restoreLatestBaiduBackup() {
        val backup = _baiduRestore.value.backup ?: return
        viewModelScope.launch {
            _baiduRestore.value = _baiduRestore.value.copy(restoring = true, error = null)
            try {
                val json = baiduPanService.downloadBackup(backup)
                val count = backupManager.restoreJsonText(json)
                _baiduRestore.value = _baiduRestore.value.copy(
                    restoring = false,
                    completedCount = count,
                )
                events.send(SettingsEvent.Message("已从百度网盘恢复 $count 笔账目"))
            } catch (error: BaiduAuthException) {
                _baiduRestore.value = _baiduRestore.value.copy(
                    restoring = false,
                    error = "百度网盘授权已失效，请重新登录",
                )
            } catch (error: BaiduException) {
                _baiduRestore.value = _baiduRestore.value.copy(
                    restoring = false,
                    error = error.message ?: "恢复失败",
                )
            } catch (error: Exception) {
                _baiduRestore.value = _baiduRestore.value.copy(
                    restoring = false,
                    error = error.message ?: "恢复失败，请检查网盘备份",
                )
            }
        }
    }

    fun clearBaiduRestore() {
        _baiduRestore.value = BaiduRestoreUiState()
    }
}
