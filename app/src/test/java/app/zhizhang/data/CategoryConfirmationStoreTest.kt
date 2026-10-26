package app.zhizhang.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.zhizhang.domain.CategoryClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryConfirmationStoreTest {

    @Test
    fun `confirmed merchant and note mapping is persisted`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("category_confirmations", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val store = CategoryConfirmationStore(context)
        val transaction = TransactionEntity(
            amountCents = 2_000,
            type = TxType.EXPENSE.name,
            categoryId = "other_expense",
            accountId = "wechat",
            merchant = "某商户",
            note = "午饭",
            occurredAt = 1_000L,
        )
        val signature = CategoryConfirmationStore.signature(transaction)
        store.put(signature, "food")
        val reloaded = CategoryConfirmationStore(context).load()
        assertEquals("food", reloaded[signature])
        assertEquals(
            CategoryConfirmationStore.signature(transaction.copy(note = " 午饭 ")),
            signature,
        )
        context.getSharedPreferences("category_confirmations", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
}
