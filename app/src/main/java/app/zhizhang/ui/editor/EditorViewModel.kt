package app.zhizhang.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.zhizhang.ZhizhangApp
import app.zhizhang.data.Account
import app.zhizhang.data.Categories
import app.zhizhang.data.Formats
import app.zhizhang.data.ReimbursementStatus
import app.zhizhang.data.TxSource
import app.zhizhang.data.TxType
import app.zhizhang.data.TransactionEntity
import app.zhizhang.domain.BudgetCoach
import app.zhizhang.domain.BudgetPulse
import app.zhizhang.domain.CategoryClassifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class EditorUiState(
    val isEditing: Boolean = false,
    val loading: Boolean = false,
    val type: TxType = TxType.EXPENSE,
    val amountInput: String = "",
    val categoryId: String = "food",
    val accountId: String = "wechat",
    val occurredAt: Long = System.currentTimeMillis(),
    val note: String = "",
    val merchant: String = "",
    val reimbursementStatus: ReimbursementStatus = ReimbursementStatus.NONE,
    val reimbursementForId: Long? = null,
    val source: TxSource = TxSource.MANUAL,
    val originalId: Long = 0L,
    val fingerprint: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null,
    val budgetCents: Long = 0L,
    val monthExpenseCents: Long = 0L,
) {
    val amountCents: Long get() = Formats.amountInputToCents(amountInput)
    val canSave: Boolean get() = amountCents > 0 && !saving
    val budgetPulse: BudgetPulse?
        get() {
            if (type != TxType.EXPENSE) return null
            val selectedMonth = Instant.ofEpochMilli(occurredAt)
                .atZone(ZoneId.systemDefault())
                .let { YearMonth.from(it) }
            if (selectedMonth != YearMonth.now(ZoneId.systemDefault())) return null
            return BudgetCoach.evaluate(amountCents, monthExpenseCents, budgetCents)
        }
}

class EditorViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val app = application as ZhizhangApp
    private val repository = app.repository
    private val settings = app.settings
    private val zone = ZoneId.systemDefault()
    private val transactionId: Long = savedStateHandle["txId"] ?: 0L

    private val _state = MutableStateFlow(
        EditorUiState(
            isEditing = transactionId > 0L,
            loading = transactionId > 0L,
        ),
    )
    val state = _state.asStateFlow()

    val customAccounts: kotlinx.coroutines.flow.StateFlow<List<Account>> =
        settings.customAccounts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
        if (transactionId > 0L) {
            viewModelScope.launch {
                val transaction = repository.getById(transactionId)
                if (transaction == null) {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = "这笔账目已经不存在了",
                    )
                } else {
                    val currentMonth = YearMonth.now(zone)
                    val budget = settings.budgetCents.first()
                    val monthExpense = repository.expenseForMonth(currentMonth, zone)
                    val transactionMonth = Instant.ofEpochMilli(transaction.occurredAt)
                        .atZone(zone)
                        .let { YearMonth.from(it) }
                    val baseline = if (
                        transaction.txType == TxType.EXPENSE &&
                        transactionMonth == currentMonth
                    ) {
                        (monthExpense - transaction.amountCents).coerceAtLeast(0L)
                    } else {
                        monthExpense
                    }
                    _state.value = EditorUiState(
                        isEditing = true,
                        loading = false,
                        type = transaction.txType,
                        amountInput = Formats.centsToInput(transaction.amountCents),
                        categoryId = transaction.categoryId,
                        accountId = transaction.accountId,
                        occurredAt = transaction.occurredAt,
                        note = transaction.note,
                        merchant = transaction.merchant,
                        reimbursementStatus = transaction.reimbursement,
                        reimbursementForId = transaction.reimbursementForId,
                        source = transaction.txSource,
                        originalId = transaction.id,
                        fingerprint = transaction.fingerprint,
                        createdAt = transaction.createdAt,
                        budgetCents = budget,
                        monthExpenseCents = baseline,
                    )
                }
            }
        } else {
            viewModelScope.launch {
                val account = settings.lastAccountId.first()
                val budget = settings.budgetCents.first()
                val monthExpense = repository.expenseForMonth(YearMonth.now(zone), zone)
                _state.value = _state.value.copy(
                    accountId = account,
                    budgetCents = budget,
                    monthExpenseCents = monthExpense,
                )
            }
        }
    }

    fun setType(type: TxType) {
        if (_state.value.type == type) return
        _state.value = _state.value.copy(
            type = type,
            categoryId = Categories.defaultFor(type).id,
            error = null,
        )
    }

    fun appendDigit(digit: Char) {
        if (!digit.isDigit()) return
        val current = _state.value.amountInput
        if (current.length >= 12) return
        val dot = current.indexOf('.')
        if (dot >= 0 && current.length - dot > 2) return
        val next = when {
            current == "0" -> digit.toString()
            current.isEmpty() && digit == '0' -> "0"
            else -> current + digit
        }
        _state.value = _state.value.copy(amountInput = next, error = null)
    }

    fun appendDot() {
        val current = _state.value.amountInput
        if (current.contains('.')) return
        if (current.length >= 12) return
        val next = if (current.isEmpty()) "0." else "$current."
        _state.value = _state.value.copy(amountInput = next, error = null)
    }

    fun backspace() {
        val current = _state.value.amountInput
        if (current.isEmpty()) return
        _state.value = _state.value.copy(amountInput = current.dropLast(1), error = null)
    }

    fun clearAmount() {
        _state.value = _state.value.copy(amountInput = "", error = null)
    }

    fun setCategory(categoryId: String) {
        val category = Categories.byId(categoryId)
        if (category.type != _state.value.type) return
        _state.value = _state.value.copy(categoryId = categoryId, error = null)
    }

    fun setAccount(accountId: String) {
        _state.value = _state.value.copy(accountId = accountId)
    }

    fun addBankCard(bank: String, tail: String) {
        viewModelScope.launch {
            settings.addCustomAccount(bank, tail)?.let { account ->
                _state.value = _state.value.copy(accountId = account.id)
            }
        }
    }

    fun setDate(date: LocalDate) {
        val previousTime = Instant.ofEpochMilli(_state.value.occurredAt).atZone(zone).toLocalTime()
        val occurred = date.atTime(previousTime).atZone(zone).toInstant().toEpochMilli()
        _state.value = _state.value.copy(occurredAt = occurred)
    }

    fun setTime(hour: Int, minute: Int) {
        val previous = Instant.ofEpochMilli(_state.value.occurredAt).atZone(zone)
        val occurred = previous
            .withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
            .toInstant()
            .toEpochMilli()
        _state.value = _state.value.copy(occurredAt = occurred)
    }

    fun setNote(note: String) {
        _state.value = _state.value.copy(note = note.take(120))
    }

    fun setPendingReimbursement(pending: Boolean) {
        val current = _state.value
        if (current.type != TxType.EXPENSE) return
        if (current.reimbursementStatus == ReimbursementStatus.DONE) return
        _state.value = current.copy(
            reimbursementStatus = if (pending) {
                ReimbursementStatus.PENDING
            } else {
                ReimbursementStatus.NONE
            },
        )
    }

    fun save() {
        val snapshot = _state.value
        if (snapshot.saving || snapshot.saved) return
        val cents = snapshot.amountCents
        if (cents <= 0L) {
            _state.value = snapshot.copy(error = "先输入一个金额吧")
            return
        }
        if (snapshot.isEditing && snapshot.originalId <= 0L) {
            _state.value = snapshot.copy(error = "这笔账目无法编辑，请返回后重试")
            return
        }
        _state.value = snapshot.copy(saving = true, error = null)

        viewModelScope.launch {
            runCatching {
                if (snapshot.isEditing) {
                    repository.updateFromEditor(
                        TransactionEntity(
                            id = snapshot.originalId,
                            amountCents = cents,
                            type = snapshot.type.name,
                            categoryId = snapshot.categoryId,
                            accountId = snapshot.accountId,
                            merchant = snapshot.merchant,
                            note = snapshot.note.trim(),
                            occurredAt = snapshot.occurredAt,
                            source = snapshot.source.name,
                            fingerprint = snapshot.fingerprint,
                            reimbursementStatus = if (snapshot.type == TxType.EXPENSE) {
                                snapshot.reimbursementStatus.name
                            } else {
                                ReimbursementStatus.NONE.name
                            },
                            reimbursementForId = snapshot.reimbursementForId,
                            createdAt = snapshot.createdAt,
                        ),
                    )
                } else {
                    repository.addManual(
                        amountCents = cents,
                        type = snapshot.type,
                        categoryId = snapshot.categoryId,
                        accountId = snapshot.accountId,
                        merchant = "",
                        note = snapshot.note,
                        occurredAt = snapshot.occurredAt,
                        source = TxSource.MANUAL,
                        reimbursementStatus = snapshot.reimbursementStatus,
                    )
                }

                if (snapshot.merchant.isNotBlank()) {
                    val suggested = CategoryClassifier(repository.rulesSnapshot())
                        .classify("${snapshot.merchant} ${snapshot.note}", snapshot.type)
                    if (suggested != snapshot.categoryId) {
                        repository.learnRule(snapshot.merchant, snapshot.categoryId)
                    }
                }
            }.onSuccess {
                _state.value = _state.value.copy(saving = false, saved = true)
            }.onFailure {
                _state.value = _state.value.copy(
                    saving = false,
                    error = "保存失败，请再试一次",
                )
            }
        }
    }

    fun delete() {
        val id = _state.value.originalId
        if (id <= 0L) return
        viewModelScope.launch {
            repository.delete(id)
            _state.value = _state.value.copy(deleted = true)
        }
    }
}
