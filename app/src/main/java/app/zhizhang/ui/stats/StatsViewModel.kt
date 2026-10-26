package app.zhizhang.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zhizhang.ZhizhangApp
import app.zhizhang.data.TxType
import app.zhizhang.domain.MonthStats
import app.zhizhang.domain.StatsCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class StatsUiState(
    val month: YearMonth = YearMonth.now(),
    val loading: Boolean = true,
    val stats: MonthStats? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ZhizhangApp
    private val repository = app.repository
    private val settings = app.settings
    private val zone = ZoneId.systemDefault()

    private val selectedMonth = MutableStateFlow(YearMonth.now(zone))

    val uiState = selectedMonth.flatMapLatest { month ->
        combine(
            repository.observeMonth(month, zone),
            repository.observeMonth(month.minusMonths(1), zone),
            settings.budgetCents,
        ) { current, previous, budget ->
            val comparisonDay = if (month == YearMonth.now(zone)) {
                LocalDate.now(zone).dayOfMonth
            } else {
                month.lengthOfMonth()
            }
            val previousExpense = previous
                .filter { it.txType == TxType.EXPENSE }
                .filter {
                    Instant.ofEpochMilli(it.occurredAt).atZone(zone).dayOfMonth <= comparisonDay
                }
                .sumOf { it.amountCents }
            StatsUiState(
                month = month,
                loading = false,
                stats = StatsCalculator.build(
                    month = month,
                    transactions = current,
                    previousExpenseCents = previousExpense,
                    budgetCents = budget,
                    zone = zone,
                ),
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(month = selectedMonth.value),
    )

    fun selectMonth(month: YearMonth) {
        if (month.isAfter(YearMonth.now(zone))) return
        selectedMonth.value = month
    }

    fun shiftMonth(delta: Long) {
        val next = selectedMonth.value.plusMonths(delta)
        if (next.isAfter(YearMonth.now(zone))) return
        selectedMonth.value = next
    }
}
