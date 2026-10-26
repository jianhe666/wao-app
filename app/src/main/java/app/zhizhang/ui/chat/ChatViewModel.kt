package app.zhizhang.ui.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.viewModelScope
import app.zhizhang.ZhizhangApp
import app.zhizhang.data.Account
import app.zhizhang.data.Accounts
import app.zhizhang.data.AiChatTurn
import app.zhizhang.data.AiException
import app.zhizhang.data.ReimbursementIntent
import app.zhizhang.data.Categories
import app.zhizhang.data.Formats
import app.zhizhang.data.ImportSelection
import app.zhizhang.data.ReimbursementStatus
import app.zhizhang.data.TxType
import app.zhizhang.data.TransactionRepository
import app.zhizhang.data.TransactionEntity
import app.zhizhang.domain.ChatGreetings
import app.zhizhang.domain.ChatStyle
import app.zhizhang.domain.CustomChatStyle
import app.zhizhang.domain.ChatStyles
import app.zhizhang.domain.ParsedBill
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class ChatRole {
    USER,
    ASSISTANT,
}

data class ChatBill(
    val bill: ParsedBill,
    val categoryId: String,
)

data class ChatReimbursement(
    val original: TransactionEntity,
    val accountId: String,
    val receivedAt: Long,
    val confirmed: Boolean = false,
    val dismissed: Boolean = false,
)

data class ChatMessage(
    val id: Long,
    val role: ChatRole,
    val content: String,
    val bills: List<ChatBill> = emptyList(),
    val reimbursements: List<ChatReimbursement> = emptyList(),
    val confirmed: Boolean = false,
    val confirmedCount: Int = 0,
    val dismissed: Boolean = false,
    val error: Boolean = false,
    val welcome: Boolean = false,
    val image: ImageBitmap? = null,
)

data class HomeSummary(
    val month: YearMonth = YearMonth.now(),
    val expenseCents: Long = 0L,
    val incomeCents: Long = 0L,
    val budgetCents: Long = 0L,
    val count: Int = 0,
) {
    val remainingCents: Long get() = (budgetCents - expenseCents).coerceAtLeast(0L)
    val progress: Float
        get() = if (budgetCents <= 0L) 0f else (expenseCents.toFloat() / budgetCents).coerceIn(0f, 1f)
    val isOverBudget: Boolean get() = budgetCents > 0L && expenseCents > budgetCents
}

data class ChatUiState(
    val checking: Boolean = true,
    val available: Boolean = false,
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            id = 0L,
            role = ChatRole.ASSISTANT,
            content = "直接告诉我花了什么，或者发一张账单截图。",
            welcome = true,
        ),
    ),
    val input: String = "",
    val sending: Boolean = false,
    val pendingImage: ImageBitmap? = null,
    val pendingImageDataUrl: String? = null,
    val quickPrompts: List<String> = emptyList(),
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ZhizhangApp
    private val repository = app.repository
    private val settings = app.settings
    private val aiService = app.aiService
    private val zone = ZoneId.systemDefault()
    private var nextId = 1L
    private val greeting = ChatGreetings.forMoment(LocalDateTime.now(zone))

    private val _state = MutableStateFlow(
        ChatUiState(
            messages = listOf(
                ChatMessage(
                    id = 0L,
                    role = ChatRole.ASSISTANT,
                    content = greeting.message,
                    welcome = true,
                ),
            ),
            quickPrompts = greeting.suggestions,
        ),
    )
    val state = _state.asStateFlow()

    val customChatStyles: kotlinx.coroutines.flow.StateFlow<List<CustomChatStyle>> =
        settings.customChatStyles.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val customAccounts: kotlinx.coroutines.flow.StateFlow<List<Account>> =
        settings.customAccounts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val chatStyle: kotlinx.coroutines.flow.StateFlow<ChatStyle> = combine(
        settings.chatStyleId,
        settings.customChatStyles,
    ) { id, custom ->
        ChatStyles.resolve(id, custom)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatStyles.default,
    )

    val summary: kotlinx.coroutines.flow.StateFlow<HomeSummary> = combine(
        repository.observeMonth(YearMonth.now(zone), zone),
        settings.budgetCents,
    ) { transactions, budget ->
        HomeSummary(
            month = YearMonth.now(zone),
            expenseCents = transactions
                .filter { it.txType == TxType.EXPENSE }
                .sumOf { it.amountCents },
            incomeCents = transactions
                .filter { it.txType == TxType.INCOME }
                .sumOf { it.amountCents },
            budgetCents = budget,
            count = transactions.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeSummary(),
    )

    init {
        viewModelScope.launch {
            combine(settings.aiEnabled, settings.aiApiKey) { enabled, key ->
                enabled && key.isNotBlank()
            }.collect { available ->
                _state.update { it.copy(available = available, checking = false) }
            }
        }
    }

    fun updateInput(value: String) {
        _state.update { it.copy(input = value.take(500)) }
    }

    fun attachImage(uri: Uri, autoSend: Boolean = false) {
        viewModelScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                ChatImage.prepare(getApplication(), uri)
            }
            if (prepared == null) {
                addError("这张图片打不开，换一张试试。")
            } else {
                _state.update {
                    it.copy(
                        pendingImage = prepared.preview,
                        pendingImageDataUrl = prepared.dataUrl,
                        input = if (it.input.isBlank()) "识别这张账单" else it.input,
                    )
                }
                if (autoSend) {
                    // 分享图片启动时可能还在初始化 available 状态，直接读配置避免竞态。
                    val aiReady = settings.aiEnabled.first() && settings.aiApiKey.first().isNotBlank()
                    if (aiReady) {
                        _state.update { it.copy(available = true, checking = false) }
                        send()
                    } else {
                        addError("图片已收到。先打开 AI 助手，就能识别这张账单。")
                    }
                }
            }
        }
    }

    fun clearPendingImage() {
        _state.update { it.copy(pendingImage = null, pendingImageDataUrl = null) }
    }

    fun reportError(text: String) {
        addError(text)
    }

    fun setChatStyle(id: String) {
        viewModelScope.launch { settings.setChatStyle(id) }
    }

    fun saveCustomChatStyle(id: String?, name: String, instruction: String) {
        viewModelScope.launch {
            settings.saveCustomChatStyle(id, name, instruction)
        }
    }

    fun deleteCustomChatStyle(id: String) {
        viewModelScope.launch { settings.deleteCustomChatStyle(id) }
    }

    fun send(preset: String? = null) {
        val snapshot = _state.value
        if (snapshot.sending) return
        if (!snapshot.available) {
            addError("AI 还没有开启。到「我的」里连接后就能对话记账。")
            return
        }
        val text = (preset ?: snapshot.input).trim()
        val imageDataUrl = snapshot.pendingImageDataUrl
        if (text.isBlank() && imageDataUrl.isNullOrBlank()) return
        val prompt = text.ifBlank { "识别这张账单截图" }
        val sentAt = LocalDateTime.now(zone).truncatedTo(ChronoUnit.MINUTES)

        val userMessage = ChatMessage(
            id = nextId++,
            role = ChatRole.USER,
            content = prompt,
            image = snapshot.pendingImage,
        )
        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                input = "",
                sending = true,
                pendingImage = null,
                pendingImageDataUrl = null,
            )
        }

        viewModelScope.launch {
            try {
                val history = _state.value.messages
                    .filter { !it.welcome && !it.error }
                    .dropLast(1)
                    .map {
                        AiChatTurn(
                            role = if (it.role == ChatRole.USER) "user" else "assistant",
                            content = it.content,
                        )
                    } + AiChatTurn("user", prompt, imageDataUrl)
                val context = buildLedgerContext()
                val result = aiService.chat(
                    history = history,
                    ledgerContext = context,
                    lastUserText = prompt,
                    styleInstruction = "${chatStyle.value.name}风格。${chatStyle.value.instruction}",
                    temperature = chatStyle.value.temperature,
                    now = sentAt,
                    zone = zone,
                    accounts = Accounts.all + settings.customAccounts.first(),
                )
                val reimbursements = resolveReimbursements(
                    intents = result.reimbursements,
                    sentAt = sentAt,
                )
                val assistant = ChatMessage(
                    id = nextId++,
                    role = ChatRole.ASSISTANT,
                    content = result.reply,
                    bills = result.bills.map { ChatBill(it, it.categoryId) },
                    reimbursements = reimbursements,
                )
                _state.update { it.copy(messages = it.messages + assistant, sending = false) }
                if (result.reimbursements.size > reimbursements.size) {
                    addError("没有找到唯一对应的待报销支出。补充一下日期、金额或商户，我再试一次。")
                }
            } catch (error: AiException) {
                val detail = error.message.orEmpty().trim()
                val text = when {
                    detail.contains("长度") || detail.contains("max_tokens") ->
                        "AI 回复被长度上限截断，已尝试扩大输出预算。可以把账本内容缩短一点，或再发一次。"
                    detail.contains("没有生成内容") ->
                        "AI 服务这次没有返回内容，已自动重试过。直接再发一次就好。"
                    detail.contains("API Key") ||
                        detail.contains("API 地址") ||
                        detail.contains("余额") ->
                        "AI 配置需要检查一下：$detail"
                    error.retryable ->
                        "网络或 AI 服务不稳定，重试后仍未完成。$detail"
                    else ->
                        "AI 这次没有给出结果。${detail.ifBlank { "可以再发一次试试。" }}"
                }
                addError(text)
            } catch (error: Exception) {
                addError("网络或 AI 服务没有响应，再发一次试试。")
            }
        }
    }

    fun setReimbursementAccount(messageId: Long, index: Int, accountId: String) {
        if (!Accounts.isValid(accountId)) return
        _state.update { current ->
            current.copy(
                messages = current.messages.map { message ->
                    if (message.id != messageId) {
                        message
                    } else {
                        message.copy(
                            reimbursements = message.reimbursements.mapIndexed { itemIndex, item ->
                                if (itemIndex == index && !item.confirmed && !item.dismissed) {
                                    item.copy(accountId = accountId)
                                } else {
                                    item
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    fun cancelReimbursement(messageId: Long, index: Int) {
        _state.update { current ->
            current.copy(
                messages = current.messages.map { message ->
                    if (message.id != messageId) {
                        message
                    } else {
                        message.copy(
                            reimbursements = message.reimbursements.mapIndexed { itemIndex, item ->
                                if (itemIndex == index && !item.confirmed) {
                                    item.copy(dismissed = true)
                                } else {
                                    item
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    fun confirmReimbursement(messageId: Long, index: Int) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val proposal = message.reimbursements.getOrNull(index) ?: return
        if (proposal.confirmed || proposal.dismissed) return
        viewModelScope.launch {
            val success = repository.completeReimbursement(
                originalId = proposal.original.id,
                accountId = proposal.accountId,
                occurredAt = proposal.receivedAt,
            )
            if (success) {
                _state.update { current ->
                    current.copy(
                        messages = current.messages.map { item ->
                            if (item.id != messageId) {
                                item
                            } else {
                                item.copy(
                                    reimbursements = item.reimbursements.mapIndexed { i, value ->
                                        if (i == index) value.copy(confirmed = true) else value
                                    },
                                )
                            }
                        },
                    )
                }
            } else {
                addError("这笔支出已经报销过，或原支出已经被删除。")
            }
        }
    }

    fun cancel(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        if (message.confirmed || message.dismissed || message.bills.isEmpty()) return
        _state.update { current ->
            current.copy(
                messages = current.messages.map {
                    if (it.id == messageId) it.copy(dismissed = true) else it
                },
            )
        }
    }

    fun confirm(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        if (message.confirmed || message.bills.isEmpty()) return
        viewModelScope.launch {
            val count = repository.addAiBills(
                message.bills.map { ImportSelection(it.bill, it.categoryId) },
            )
            _state.update { current ->
                current.copy(
                    messages = current.messages.map {
                        if (it.id == messageId) {
                            it.copy(confirmed = true, confirmedCount = count)
                        } else {
                            it
                        }
                    },
                )
            }
        }
    }

    fun openConfigMessage() {
        addError("请先在「我的 → AI → 连接配置」中填写 API Key 并开启 AI。")
    }

    private suspend fun resolveReimbursements(
        intents: List<ReimbursementIntent>,
        sentAt: LocalDateTime,
    ): List<ChatReimbursement> {
        if (intents.isEmpty()) return emptyList()
        val expenses = repository.all()
            .filter { it.txType == TxType.EXPENSE }
            .filter { it.reimbursement != ReimbursementStatus.DONE }
        val used = mutableSetOf<Long>()
        val received = sentAt.atZone(zone).toInstant().toEpochMilli()
        return intents.mapNotNull { intent ->
            val candidates = expenses.filter { original ->
                original.id !in used &&
                    original.amountCents == intent.amountCents &&
                    (intent.date == null ||
                        Instant.ofEpochMilli(original.occurredAt).atZone(zone).toLocalDate() == intent.date) &&
                    (intent.merchant.isBlank() || run {
                        val name = original.merchant.ifBlank {
                            Categories.byId(original.categoryId).name
                        }
                        name.contains(intent.merchant, ignoreCase = true) ||
                            intent.merchant.contains(name, ignoreCase = true)
                    })
            }
            val original = candidates.singleOrNull() ?: return@mapNotNull null
            used += original.id
            ChatReimbursement(
                original = original,
                accountId = intent.accountId?.takeIf(Accounts::isValid) ?: original.accountId,
                receivedAt = intent.receivedAt ?: received,
            )
        }
    }

    private fun addError(text: String) {
        val message = ChatMessage(
            id = nextId++,
            role = ChatRole.ASSISTANT,
            content = text,
            error = true,
        )
        _state.update { it.copy(messages = it.messages + message, sending = false) }
    }

    private suspend fun buildLedgerContext(): String {
        val month = YearMonth.now(zone)
        val range = TransactionRepository.monthRange(month, zone)
        val allTransactions = repository.all()
        val current = allTransactions.filter {
            it.occurredAt >= range.first && it.occurredAt < range.second
        }
        val expense = current.filter { it.txType == TxType.EXPENSE }
        val income = current.filter { it.txType == TxType.INCOME }
        val budget = settings.budgetCents.first()

        val byCategory = expense.groupBy { it.categoryId }
            .map { (id, list) -> Categories.byId(id).name to list.sumOf { it.amountCents } }
            .sortedByDescending { it.second }
            .take(5)
            .joinToString("；") { "${it.first} ${Formats.money(it.second)}" }
        val recent = current.sortedByDescending { it.occurredAt }.take(15).joinToString("；") {
            val day = Instant.ofEpochMilli(it.occurredAt).atZone(zone)
            val category = Categories.byId(it.categoryId).name
            val account = Accounts.byId(it.accountId).name
            val name = it.merchant.ifBlank { category }
            val kind = if (it.txType == TxType.INCOME) "收入" else "支出"
            val reimbursement = when (it.reimbursement) {
                ReimbursementStatus.PENDING -> "/待报销"
                ReimbursementStatus.DONE -> "/已报销"
                ReimbursementStatus.NONE -> ""
            }
            "${day.monthValue}月${day.dayOfMonth}日 $name ${Formats.money(it.amountCents)} $category/$account/$kind$reimbursement"
        }
        val pendingReimbursements = allTransactions
            .filter {
                it.txType == TxType.EXPENSE &&
                    it.reimbursement == ReimbursementStatus.PENDING
            }
            .sortedByDescending { it.occurredAt }
            .take(20)
            .joinToString("；") {
                val day = Instant.ofEpochMilli(it.occurredAt).atZone(zone)
                val name = it.merchant.ifBlank { Categories.byId(it.categoryId).name }
                "${day.monthValue}月${day.dayOfMonth}日 $name ${Formats.money(it.amountCents)}"
            }
        return buildString {
            append("当前月份：${month.year}年${month.monthValue}月。")
            if (current.isEmpty()) append("本月还没有账目。")
            append("支出 ${Formats.money(expense.sumOf { it.amountCents })}，")
            append("收入 ${Formats.money(income.sumOf { it.amountCents })}。")
            if (budget <= 0) {
                append("用户还没有设置月度预算。")
            } else {
                val spent = expense.sumOf { it.amountCents }
                append("月度预算 ${Formats.money(budget)}，")
                if (spent > budget) {
                    append("已超支 ${Formats.money(spent - budget)}。")
                } else {
                    append("剩余 ${Formats.money(budget - spent)}。")
                }
            }
            if (byCategory.isNotBlank()) append("支出分类：$byCategory。")
            if (recent.isNotBlank()) append("最近账目：$recent。")
            if (pendingReimbursements.isNotBlank()) {
                append("待报销支出：$pendingReimbursements。")
            }
        }.take(2_400)
    }
}
