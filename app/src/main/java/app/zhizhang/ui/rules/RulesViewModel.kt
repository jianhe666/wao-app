package app.zhizhang.ui.rules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zhizhang.ZhizhangApp
import app.zhizhang.data.AiException
import app.zhizhang.data.CategorySuggestion
import app.zhizhang.data.CategorySuggestionStore
import app.zhizhang.data.MerchantRuleEntity
import app.zhizhang.data.MerchantSample
import app.zhizhang.domain.CategoryLearning
import app.zhizhang.domain.LearningCandidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RulesViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ZhizhangApp
    private val repository = app.repository
    private val settings = app.settings
    private val aiService = app.aiService
    private val suggestionStore = CategorySuggestionStore(app)

    val rules = repository.observeRules().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList<MerchantRuleEntity>(),
    )

    private val _suggestions = MutableStateFlow(suggestionStore.load())
    val suggestions = _suggestions.asStateFlow()

    private val _classifying = MutableStateFlow(false)
    val classifying = _classifying.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun classifyWithAi() {
        if (_classifying.value) return
        viewModelScope.launch {
            _classifying.value = true
            _message.value = null
            try {
                if (!settings.aiEnabled.first() || settings.aiApiKey.first().isBlank()) {
                    _message.value = "先在「我的 → AI」开启 AI 并配置 API Key"
                    return@launch
                }
                val existing = repository.rulesSnapshot()
                val pendingKeys = _suggestions.value.map { it.merchantKey }.toSet()
                val candidates = CategoryLearning.candidates(
                    transactions = repository.all(),
                    learnedRules = existing,
                    pendingKeys = pendingKeys,
                )
                val samples = candidates.map { MerchantSample(it.merchant, it.type) }
                if (samples.isEmpty()) {
                    _message.value = "没有需要分类的新商户，已学习的商户会自动跳过"
                    return@launch
                }
                val result = aiService.classifyMerchants(samples)
                val auto = result.filter { it.confidence >= 0.8f }
                val pending = result
                    .filter { it.confidence < 0.8f }
                    .map {
                        CategorySuggestion(
                            merchantKey = it.merchantKey,
                            merchant = it.merchant,
                            categoryId = it.categoryId,
                            confidence = it.confidence,
                            reason = it.reason,
                            type = it.type,
                        )
                    }
                auto.forEach { repository.updateRule(it.merchantKey, it.categoryId) }
                val learnedAfterAi = repository.rulesSnapshot().keys
                suggestionStore.removeAll(learnedAfterAi)
                val pendingAfterRules = pending.filter {
                    it.merchantKey !in learnedAfterAi
                }
                _suggestions.value = suggestionStore.merge(pendingAfterRules)
                _message.value = if (auto.isEmpty() && pendingAfterRules.isEmpty()) {
                    "AI 没有给出新的分类建议"
                } else {
                    "候选 ${candidates.size} 个，已自动分类 ${auto.size} 个，${pendingAfterRules.size} 个待确认"
                }
            } catch (error: AiException) {
                _message.value = error.message ?: "AI 分类失败"
            } catch (error: Exception) {
                _message.value = "AI 分类失败，请稍后重试"
            } finally {
                _classifying.value = false
            }
        }
    }

    fun confirmSuggestion(suggestion: CategorySuggestion) {
        viewModelScope.launch {
            val alreadyLearned = suggestion.merchantKey in repository.rulesSnapshot()
            if (!alreadyLearned) {
                repository.updateRule(suggestion.merchantKey, suggestion.categoryId)
            }
            _suggestions.value = suggestionStore.remove(suggestion.merchantKey)
        }
    }

    fun confirmAllSuggestions() {
        viewModelScope.launch {
            val learned = repository.rulesSnapshot()
            _suggestions.value.forEach {
                if (it.merchantKey !in learned) {
                    repository.updateRule(it.merchantKey, it.categoryId)
                }
            }
            _suggestions.value = suggestionStore.clear()
        }
    }

    fun dismissSuggestion(suggestion: CategorySuggestion) {
        _suggestions.value = suggestionStore.remove(suggestion.merchantKey)
    }

    fun updateCategory(merchantKey: String, categoryId: String) {
        viewModelScope.launch { repository.updateRule(merchantKey, categoryId) }
    }

    fun delete(merchantKey: String) {
        viewModelScope.launch { repository.deleteRule(merchantKey) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearRules() }
    }
}
