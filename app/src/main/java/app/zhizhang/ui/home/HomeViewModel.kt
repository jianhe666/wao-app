package app.zhizhang.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zhizhang.ZhizhangApp
import app.zhizhang.data.Formats
import app.zhizhang.data.ReimbursementStatus
import app.zhizhang.data.TxType
import app.zhizhang.data.TransactionEntity
import app.zhizhang.data.MergeDismissalStore
import app.zhizhang.domain.DuplicateMerge
import app.zhizhang.domain.MergeCandidate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class TransactionGroup(
    val date: LocalDate,
    val transactions: List<TransactionEntity>,
    val expenseCents: Long,
    val incomeCents: Long,
)

data class HomeUiState(
    val month: YearMonth = YearMonth.now(),
    val loading: Boolean = true,
    val expenseCents: Long = 0L,
    val incomeCents: Long = 0L,
    val budgetCents: Long = 0L,
    val previousExpenseCents: Long = 0L,
    val groups: List<TransactionGroup> = emptyList(),
    val transactionCount: Int = 0,
    val pendingReimbursementCents: Long = 0L,
    val pendingReimbursementCount: Int = 0,
    val reviewCount: Int = 0,
    val unprocessedNotificationCount: Int = 0,
    val mergeCandidates: List<MergeCandidate> = emptyList(),
) {
    val balanceCents: Long get() = incomeCents - expenseCents
    val budgetRemainingCents: Long get() = (budgetCents - expenseCents).coerceAtLeast(0L)
    val budgetProgress: Float
        get() = if (budgetCents <= 0L) 0f else (expenseCents.toFloat() / budgetCents).coerceIn(0f, 1f)
    val budgetUsedRatio: Float
        get() = if (budgetCents <= 0L) 0f else (expenseCents.toFloat() / budgetCents)
    val expenseDeltaPercent: Int?
        get() = if (previousExpenseCents <= 0L) {
            null
        } else {
            (((expenseCents - previousExpenseCents).toFloat() / previousExpenseCents) * 100).toInt()
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ZhizhangApp
    private val repository = app.repository
    private val settings = app.settings
    private val zone = ZoneId.systemDefault()

    private val mergeDismissalStore = MergeDismissalStore(app)
    private val selectedMonth = MutableStateFlow(YearMonth.now(zone))
    private val dismissedMergeKeys = MutableStateFlow(mergeDismissalStore.load())
    val selectedCategoryId = MutableStateFlow<String?>(null)

    val uiState = combine(selectedMonth, dismissedMergeKeys) { month, dismissed ->
        month to dismissed
    }.flatMapLatest { (month, dismissed) ->
        combine(
            repository.observeMonth(month, zone),
            repository.observeMonth(month.minusMonths(1), zone),
            settings.budgetCents,
            repository.observeAll(),
            repository.observeUnprocessedNotificationCount(),
        ) { current, previous, budget, all, eventCount ->
            buildState(month, current, previous, budget, all, eventCount, dismissed)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(month = selectedMonth.value),
    )

    fun selectMonth(month: YearMonth) {
        if (month.isAfter(YearMonth.now(zone))) return
        selectedCategoryId.value = null
        selectedMonth.value = month
    }

    fun shiftMonth(delta: Long) {
        val next = selectedMonth.value.plusMonths(delta)
        val current = YearMonth.now(zone)
        if (next.isAfter(current)) return
        selectedCategoryId.value = null
        selectedMonth.value = next
    }

    fun selectCategory(categoryId: String?) {
        selectedCategoryId.value = categoryId
    }

    fun setBudgetFromInput(input: String) {
        val cents = Formats.amountInputToCents(input)
        viewModelScope.launch {
            settings.setBudget(cents)
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun mergeDuplicate(primaryId: Long, duplicateId: Long) {
        viewModelScope.launch {
            repository.mergeDuplicate(primaryId, duplicateId)
        }
    }

    fun dismissMerge(candidate: MergeCandidate) {
        mergeDismissalStore.add(candidate.key)
        dismissedMergeKeys.value = dismissedMergeKeys.value + candidate.key
    }

    private fun previousExpenseFor(month: YearMonth, previous: List<TransactionEntity>): Long {
        val today = LocalDate.now(zone)
        val cutoffDay = if (month == YearMonth.from(today)) today.dayOfMonth else month.lengthOfMonth()
        return previous
            .filter { it.txType == TxType.EXPENSE }
            .filter {
                Instant.ofEpochMilli(it.occurredAt).atZone(zone).dayOfMonth <= cutoffDay
            }
            .sumOf { it.amountCents }
    }

    private fun buildState(
        month: YearMonth,
        current: List<TransactionEntity>,
        previous: List<TransactionEntity>,
        budgetCents: Long,
        all: List<TransactionEntity>,
        unprocessedNotificationCount: Int,
        dismissedMergeKeys: Set<String>,
    ): HomeUiState {
        val expenses = current.filter { it.txType == TxType.EXPENSE }
        val incomes = current.filter { it.txType == TxType.INCOME }
        val groups = current
            .groupBy { transaction ->
                Instant.ofEpochMilli(transaction.occurredAt).atZone(zone).toLocalDate()
            }
            .entries
            .sortedByDescending { it.key }
            .map { (date, list) ->
                TransactionGroup(
                    date = date,
                    transactions = list,
                    expenseCents = list.filter { it.txType == TxType.EXPENSE }.sumOf { it.amountCents },
                    incomeCents = list.filter { it.txType == TxType.INCOME }.sumOf { it.amountCents },
                )
            }

        return HomeUiState(
            month = month,
            loading = false,
            expenseCents = expenses.sumOf { it.amountCents },
            incomeCents = incomes.sumOf { it.amountCents },
            budgetCents = budgetCents,
            previousExpenseCents = previousExpenseFor(month, previous),
            groups = groups,
            transactionCount = current.size,
            pendingReimbursementCents = all
                .filter {
                    it.txType == TxType.EXPENSE &&
                        it.reimbursement == ReimbursementStatus.PENDING
                }
                .sumOf { it.amountCents },
            pendingReimbursementCount = all.count {
                it.txType == TxType.EXPENSE &&
                    it.reimbursement == ReimbursementStatus.PENDING
            },
            reviewCount = all.count { it.needsConfirmation } + unprocessedNotificationCount,
            unprocessedNotificationCount = unprocessedNotificationCount,
            mergeCandidates = DuplicateMerge.find(all)
                .filterNot { it.key in dismissedMergeKeys },
        )
    }
}
