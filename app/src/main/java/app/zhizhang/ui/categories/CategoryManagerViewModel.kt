package app.zhizhang.ui.categories

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.zhizhang.data.Categories
import app.zhizhang.data.Category
import app.zhizhang.data.CustomCategoryStore
import app.zhizhang.data.TxType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CategoryManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val store = CustomCategoryStore(application)
    private val _categories = MutableStateFlow(Categories.customCategories())
    val categories = _categories.asStateFlow()

    fun add(name: String, emoji: String, color: Long, type: TxType): Boolean {
        val category = store.add(name, emoji, color, type) ?: return false
        Categories.setCustomCategories(store.load())
        _categories.value = Categories.customCategories()
        return true
    }

    fun delete(category: Category) {
        Categories.setCustomCategories(store.delete(category.id))
        _categories.value = Categories.customCategories()
    }
}
