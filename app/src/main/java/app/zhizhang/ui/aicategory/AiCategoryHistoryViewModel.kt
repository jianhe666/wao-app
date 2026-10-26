package app.zhizhang.ui.aicategory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zhizhang.ZhizhangApp
import app.zhizhang.data.AiCategoryHistoryStore
import app.zhizhang.data.CategoryConfirmationStore
import app.zhizhang.data.TransactionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiCategoryHistoryItem(
    val transaction: TransactionEntity,
    val categoryId: String,
)

class AiCategoryHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ZhizhangApp
    private val repository = app.repository
    private val historyStore = AiCategoryHistoryStore(app)
    private val confirmationStore = CategoryConfirmationStore(app)
    private val _items = MutableStateFlow<List<AiCategoryHistoryItem>>(emptyList())
    val items = _items.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val history = historyStore.load()
            val transactions = repository.all().filter { it.id in history.keys }
            _items.value = transactions
                .sortedByDescending { it.occurredAt }
                .map { AiCategoryHistoryItem(it, it.categoryId) }
        }
    }

    fun updateCategory(transaction: TransactionEntity, categoryId: String) {
        viewModelScope.launch {
            repository.updateTransactionCategory(transaction.id, categoryId)
            historyStore.record(transaction.id, categoryId)
            confirmationStore.put(CategoryConfirmationStore.signature(transaction), categoryId)
            reload()
        }
    }
}
