package app.zhizhang.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategorySuggestionStoreTest {

    @Test
    fun `suggestions persist and can be merged and removed`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("category_suggestions", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val store = CategorySuggestionStore(context)
        val first = CategorySuggestion(
            merchantKey = "瑞幸咖啡",
            merchant = "瑞幸咖啡",
            categoryId = "food",
            confidence = 0.62f,
            reason = "咖啡店",
            type = TxType.EXPENSE,
        )
        store.merge(listOf(first))
        val loaded = CategorySuggestionStore(context).load()
        assertEquals(1, loaded.size)
        assertEquals("food", loaded.first().categoryId)

        val second = first.copy(merchantKey = "滴滴", merchant = "滴滴", categoryId = "transport")
        val merged = store.merge(listOf(second))
        assertEquals(2, merged.size)
        assertTrue(store.remove(first.merchantKey).none { it.merchantKey == first.merchantKey })
        assertEquals(emptyList<CategorySuggestion>(), store.clear())
        context.getSharedPreferences("category_suggestions", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
}
