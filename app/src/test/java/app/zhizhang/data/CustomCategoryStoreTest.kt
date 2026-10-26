package app.zhizhang.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomCategoryStoreTest {

    @Test
    fun `custom category is persisted and resolved after reload`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("custom_categories", Context.MODE_PRIVATE).edit().clear().commit()
        val store = CustomCategoryStore(context)
        val created = store.add("咖啡", "☕", 0xFF2A9D8F, TxType.EXPENSE)
        assertNotNull(created)
        val reloaded = CustomCategoryStore(context).load()
        assertEquals(1, reloaded.size)
        assertEquals("咖啡", reloaded.first().name)
        Categories.setCustomCategories(reloaded)
        assertEquals("咖啡", Categories.byId(created?.id).name)
        assertEquals(TxType.EXPENSE, Categories.byId(created?.id).type)

        val next = store.delete(created!!.id)
        assertTrue(next.isEmpty())
        Categories.setCustomCategories(emptyList())
        context.getSharedPreferences("custom_categories", Context.MODE_PRIVATE).edit().clear().commit()
    }
}
