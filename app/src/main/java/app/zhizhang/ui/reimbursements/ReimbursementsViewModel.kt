package app.zhizhang.ui.reimbursements

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zhizhang.ZhizhangApp
import app.zhizhang.data.ReimbursementStatus
import app.zhizhang.data.TxSource
import app.zhizhang.data.TxType
import app.zhizhang.data.TransactionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

data class CompletedReimbursement(
    val original: TransactionEntity,
    val income: TransactionEntity?,
)

data class ReimbursementsUiState(
    val pending: List<TransactionEntity> = emptyList(),
    val completed: List<CompletedReimbursement> = emptyList(),
) {
    val pendingCents: Long get() = pending.sumOf { it.amountCents }
}

class ReimbursementsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ZhizhangApp
    private val repository = app.repository
    private val zone = ZoneId.systemDefault()

    val uiState = repository.observeAll()
        .map { all ->
            val incomes = all.filter {
                it.txType == TxType.INCOME && it.reimbursementForId != null
            }
            ReimbursementsUiState(
                pending = all.filter {
                    it.txType == TxType.EXPENSE &&
                        it.reimbursement == ReimbursementStatus.PENDING
                }.sortedByDescending { it.occurredAt },
                completed = all.filter {
                    it.txType == TxType.EXPENSE &&
                        it.reimbursement == ReimbursementStatus.DONE
                }
                    .sortedByDescending { it.occurredAt }
                    .map { original ->
                        CompletedReimbursement(
                            original = original,
                            income = incomes.firstOrNull {
                                it.reimbursementForId == original.id
                            },
                        )
                    },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReimbursementsUiState(),
        )

    fun complete(originalId: Long, accountId: String) {
        viewModelScope.launch {
            val occurredAt = LocalDateTime.now(zone)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            repository.completeReimbursement(
                originalId = originalId,
                accountId = accountId,
                occurredAt = occurredAt,
                source = TxSource.MANUAL,
            )
        }
    }
}
