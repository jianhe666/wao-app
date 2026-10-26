package app.zhizhang.ui.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zhizhang.ZhizhangApp
import app.zhizhang.data.NotificationEventEntity
import app.zhizhang.data.TransactionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReviewViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ZhizhangApp
    private val repository = app.repository

    val items: StateFlow<List<TransactionEntity>> = repository.observeNeedsReview()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val events: StateFlow<List<NotificationEventEntity>> = app.database.notificationEventDao()
        .observeRecent(100)
        .map { list -> list.filter { !it.processed } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun confirm(id: Long) {
        viewModelScope.launch { repository.markReviewed(id) }
    }

    fun confirmAll() {
        viewModelScope.launch { repository.markAllReviewed() }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun dismissEvent(id: Long) {
        viewModelScope.launch { app.database.notificationEventDao().markIgnored(id) }
    }
}
