package app.zhizhang.ui.aicategory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zhizhang.ZhizhangApp
import app.zhizhang.data.AiCategorySuggestion
import app.zhizhang.data.AiException
import app.zhizhang.data.AiCategoryHistoryStore
import app.zhizhang.data.AiTransactionCategorySuggestion
import app.zhizhang.data.CategoryConfirmationStore
import app.zhizhang.data.Categories
import app.zhizhang.data.TransactionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

enum class AiCategoryRange(val label: String) {
    DAY("最近一天"),
    WEEK("最近一周"),
    MONTH("最近一个月"),
    ALL("全部"),
}

data class AiCategoryUiState(
    val range: AiCategoryRange = AiCategoryRange.WEEK,
    val loading: Boolean = false,
    val analyzing: Boolean = false,
    val candidates: List<TransactionEntity> = emptyList(),
    val pending: List<AiTransactionCategorySuggestion> = emptyList(),
    val appliedCount: Int = 0,
    val skippedRecorded: Int = 0,
    val message: String? = null,
    val error: String? = null,
)

class AiCategoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ZhizhangApp
    private val repository = app.repository
    private val settings = app.settings
    private val aiService = app.aiService
    private val confirmationStore = CategoryConfirmationStore(app)
    private val historyStore = AiCategoryHistoryStore(app)
    private val _state = MutableStateFlow(AiCategoryUiState())
    val state = _state.asStateFlow()

    init {
        refresh(AiCategoryRange.WEEK)
    }

    fun selectRange(range: AiCategoryRange) {
        refresh(range)
    }

    fun refresh(range: AiCategoryRange = _state.value.range) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                range = range,
                loading = true,
                candidates = emptyList(),
                pending = emptyList(),
                appliedCount = 0,
                message = null,
                error = null,
            )
            runCatching {
                val now = System.currentTimeMillis()
                val cutoff = when (range) {
                    AiCategoryRange.DAY -> now - 24 * 60 * 60 * 1000L
                    AiCategoryRange.WEEK -> now - 7 * 24 * 60 * 60 * 1000L
                    AiCategoryRange.MONTH -> now - 30 * 24 * 60 * 60 * 1000L
                    AiCategoryRange.ALL -> 0L
                }
                val recorded = historyStore.load().keys
                repository.all()
                    .filter { it.occurredAt >= cutoff }
                    .filterNot { it.id in recorded }
                    .sortedByDescending { it.occurredAt }
            }.onSuccess { transactions ->
                val skipped = historyStore.load().keys.size
                _state.value = _state.value.copy(
                    loading = false,
                    candidates = transactions,
                    skippedRecorded = skipped,
                    message = "共 ${transactions.size} 笔可分类账单" +
                        if (skipped > 0) "，已跳过 $skipped 笔已分类" else "",
                )
            }.onFailure {
                _state.value = _state.value.copy(loading = false, error = "读取账单失败")
            }
        }
    }

    fun analyze() {
        if (_state.value.analyzing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(analyzing = true, error = null, message = null)
            try {
                val recordedIds = historyStore.load().keys
                val candidates = _state.value.candidates.filterNot { it.id in recordedIds }
                if (candidates.isEmpty()) {
                    _state.value = _state.value.copy(
                        analyzing = false,
                        error = "这个时间范围内没有可分类账单",
                    )
                    return@launch
                }

                // 先用用户确认过的“商户 + 备注”映射，避免重复请求 AI。
                val confirmations = confirmationStore.load()
                val remaining = mutableListOf<TransactionEntity>()
                var locallyApplied = 0
                candidates.forEach { transaction ->
                    val signature = CategoryConfirmationStore.signature(transaction)
                    val categoryId = confirmations[signature]
                    val category = categoryId?.let { id -> Categories.byId(id) }
                    if (categoryId != null && category != null &&
                        category.type == transaction.txType
                    ) {
                        if (categoryId != transaction.categoryId) {
                            repository.updateTransactionCategory(transaction.id, categoryId)
                            locallyApplied++
                        }
                        historyStore.record(transaction.id, categoryId)
                    } else {
                        remaining += transaction
                    }
                }
                if (remaining.isEmpty()) {
                    _state.value = _state.value.copy(
                        analyzing = false,
                        pending = emptyList(),
                        appliedCount = locallyApplied,
                        message = "本地已按确认记录分类 $locallyApplied 笔，无需请求 AI",
                    )
                    return@launch
                }
                if (!settings.aiEnabled.first() || settings.aiApiKey.first().isBlank()) {
                    _state.value = _state.value.copy(
                        analyzing = false,
                        appliedCount = locallyApplied,
                        error = "先在「我的 → AI」开启 AI 并配置 API Key",
                    )
                    return@launch
                }

                val result = mutableListOf<AiTransactionCategorySuggestion>()
                remaining.chunked(40).forEachIndexed { index, chunk ->
                    _state.value = _state.value.copy(
                        message = "正在分析第 ${index + 1}/${(remaining.size + 39) / 40} 批",
                    )
                    result += aiService.classifyTransactions(chunk)
                }
                // 结果和原分类相同就不打扰用户，但记录为已分类，避免重复请求。
                result.filter { it.categoryId == it.transaction.categoryId }.forEach {
                    historyStore.record(it.transaction.id, it.categoryId)
                }
                val pending = result
                    .filter { it.categoryId != it.transaction.categoryId }
                    .distinctBy { it.transaction.id }
                _state.value = _state.value.copy(
                    analyzing = false,
                    pending = pending,
                    appliedCount = locallyApplied,
                    message = buildString {
                        append("本地已自动分类 $locallyApplied 笔")
                        append("，待确认 ${pending.size} 笔")
                    },
                    error = null,
                )
            } catch (error: AiException) {
                _state.value = _state.value.copy(
                    analyzing = false,
                    error = error.message ?: "AI 分类失败",
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    analyzing = false,
                    error = "AI 分类失败，请稍后重试",
                )
            }
        }
    }

    fun changeSuggestionCategory(
        suggestion: AiTransactionCategorySuggestion,
        categoryId: String,
    ) {
        _state.value = _state.value.copy(
            pending = _state.value.pending.map {
                if (it.transaction.id == suggestion.transaction.id) {
                    it.copy(categoryId = categoryId)
                } else {
                    it
                }
            },
        )
    }

    fun confirm(suggestion: AiTransactionCategorySuggestion) {
        viewModelScope.launch {
            confirmationStore.put(
                CategoryConfirmationStore.signature(suggestion.transaction),
                suggestion.categoryId,
            )
            historyStore.record(suggestion.transaction.id, suggestion.categoryId)
            repository.updateTransactionCategory(suggestion.transaction.id, suggestion.categoryId)
            _state.value = _state.value.copy(
                pending = _state.value.pending.filterNot {
                    it.transaction.id == suggestion.transaction.id
                },
            )
        }
    }

    fun cancel(suggestion: AiTransactionCategorySuggestion) {
        _state.value = _state.value.copy(
            pending = _state.value.pending.filterNot {
                it.transaction.id == suggestion.transaction.id
            },
        )
    }

    fun cancelAll() {
        _state.value = _state.value.copy(pending = emptyList())
    }

    fun confirmAll() {
        viewModelScope.launch {
            _state.value.pending.forEach {
                confirmationStore.put(
                    CategoryConfirmationStore.signature(it.transaction),
                    it.categoryId,
                )
                historyStore.record(it.transaction.id, it.categoryId)
                repository.updateTransactionCategory(it.transaction.id, it.categoryId)
            }
            _state.value = _state.value.copy(pending = emptyList())
        }
    }

    fun ignore(suggestion: AiTransactionCategorySuggestion) {
        _state.value = _state.value.copy(
            pending = _state.value.pending.filterNot {
                it.transaction.id == suggestion.transaction.id
            },
        )
    }
}
