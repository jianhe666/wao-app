package app.zhizhang.ui.wealth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zhizhang.ZhizhangApp
import app.zhizhang.data.Account
import app.zhizhang.data.AssetEntity
import app.zhizhang.data.Accounts
import app.zhizhang.data.Subscription
import app.zhizhang.data.SubscriptionCycle
import app.zhizhang.data.TxType
import app.zhizhang.data.WealthStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WealthAccount(
    val account: Account,
    val currentCents: Long,
)

data class WealthUiState(
    val accounts: List<WealthAccount> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val assets: List<AssetEntity> = emptyList(),
) {
    val totalCents: Long get() = accounts.sumOf { it.currentCents } + assets.sumOf { it.valueCents }
    val assetsTotalCents: Long get() = assets.sumOf { it.valueCents }
}

class WealthViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ZhizhangApp
    private val store = WealthStore(app)
    private val revision = MutableStateFlow(0)

    init {
        Accounts.setProfileOverrides(store.profiles())
    }

    val uiState = combine(
        app.repository.observeAll(),
        app.settings.customAccounts,
        app.assetRepository.assets,
        revision,
    ) { transactions, customAccounts, assets, _ ->
        val nets = transactions.groupBy { it.accountId }.mapValues { (_, rows) ->
            rows.sumOf {
                if (it.txType == TxType.INCOME) it.amountCents else -it.amountCents
            }
        }
        val accounts = (Accounts.all + customAccounts)
            .distinctBy { it.id }
            .map { raw ->
                val account = Accounts.withProfile(raw)
                WealthAccount(
                    account = account,
                    currentCents = store.openingBalance(raw.id) + (nets[raw.id] ?: 0L),
                )
            }
        WealthUiState(
            accounts = accounts,
            subscriptions = store.subscriptions(),
            assets = assets,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        WealthUiState(),
    )

    fun editAccount(account: Account, name: String, currentBalanceCents: Long) {
        val net = uiState.value.accounts.firstOrNull { it.account.id == account.id }?.currentCents
            ?: 0L
        val opening = currentBalanceCents - (net - store.openingBalance(account.id))
        store.saveProfile(account.id, name, account.emoji)
        store.setOpeningBalance(account.id, opening)
        Accounts.setProfileOverrides(store.profiles())
        revision.value++
    }

    fun saveSubscription(subscription: Subscription) {
        store.saveSubscription(subscription)
        revision.value++
    }

    fun deleteSubscription(id: Long) {
        store.deleteSubscription(id)
        revision.value++
    }

    fun nextPayment(cycle: SubscriptionCycle, fromMillis: Long): Long =
        store.nextDate(cycle, fromMillis)
}
