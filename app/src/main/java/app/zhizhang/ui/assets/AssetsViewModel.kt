package app.zhizhang.ui.assets

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zhizhang.ZhizhangApp
import app.zhizhang.data.AssetCategory
import app.zhizhang.data.AssetEntity
import app.zhizhang.data.AssetSource
import app.zhizhang.data.AssetCategories
import app.zhizhang.data.Formats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AssetsUiState(
    val assets: List<AssetEntity> = emptyList(),
) {
    val totalCents: Long get() = assets.sumOf { it.valueCents }
}

class AssetsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as ZhizhangApp).assetRepository

    val uiState = repository.assets
        .map { AssetsUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetsUiState())

    fun add(
        name: String,
        category: AssetCategory,
        valueInput: String,
        note: String,
        onDone: (Boolean) -> Unit,
    ) {
        val valueCents = Formats.amountInputToCents(valueInput)
        if (name.isBlank() || valueCents <= 0L) {
            onDone(false)
            return
        }
        viewModelScope.launch {
            val id = repository.add(
                name = name,
                categoryId = category.id,
                valueCents = valueCents,
                note = note,
                source = AssetSource.MANUAL,
            )
            onDone(id != null)
        }
    }

    fun delete(asset: AssetEntity) {
        viewModelScope.launch { repository.delete(asset.id) }
    }
}
