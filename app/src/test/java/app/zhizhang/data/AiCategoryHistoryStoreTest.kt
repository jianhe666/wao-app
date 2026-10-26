package app.zhizhang.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiCategoryHistoryStoreTest {

    @Test
    fun `classified transaction ids are remembered`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("ai_category_history", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val store = AiCategoryHistoryStore(context)
        store.record(12L, "food")
        assertEquals("food", AiCategoryHistoryStore(context).load()[12L])
        store.remove(12L)
        assertEquals(null, AiCategoryHistoryStore(context).load()[12L])
        context.getSharedPreferences("ai_category_history", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
}
