package app.zhizhang.ui.appearance

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zhizhang.ZhizhangApp
import app.zhizhang.data.BackgroundImageStore
import app.zhizhang.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BackgroundUiState(
    val presetId: String = "default",
    val opacity: Float = 1f,
    val customPath: String = "",
    val importing: Boolean = false,
    val error: String? = null,
)

class BackgroundViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = (application as ZhizhangApp).settings
    private val imageStore = BackgroundImageStore(application)
    private val importState = MutableStateFlow<Pair<Boolean, String?>>(false to null)

    val uiState = combine(
        settings.backgroundId,
        settings.backgroundOpacity,
        settings.customBackgroundPath,
        importState,
    ) { id, opacity, path, import ->
        BackgroundUiState(
            presetId = id,
            opacity = opacity,
            customPath = path,
            importing = import.first,
            error = import.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BackgroundUiState(),
    )

    fun selectPreset(id: String) {
        viewModelScope.launch { settings.setBackground(id) }
    }

    fun setOpacity(value: Float) {
        viewModelScope.launch { settings.setBackgroundOpacity(value) }
    }

    fun importCustom(uri: Uri) {
        viewModelScope.launch {
            importState.value = true to null
            val path = imageStore.import(uri)
            if (path == null) {
                importState.value = false to "图片读取失败，换一张试试"
            } else {
                settings.setBackground("custom", path)
                importState.value = false to null
            }
        }
    }

    fun clearError() {
        importState.value = false to null
    }
}
