package app.zhizhang.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MergeDuplicateRepositoryTest {

    @Test
    fun `merge keeps automatic record and removes chat duplicate`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = TransactionRepository(database, SettingsRepository(context))
            val automaticId = repository.addManual(
                amountCents = 2_640,
                type = TxType.EXPENSE,
                categoryId = "other_expense",
                accountId = "wechat",
                merchant = "滴滴出行",
                note = "",
                occurredAt = 1_000_000L,
                source = TxSource.NOTIFICATION,
            )
            val chatId = repository.addManual(
                amountCents = 2_640,
                type = TxType.EXPENSE,
                categoryId = "transport",
                accountId = "wechat",
                merchant = "滴滴出行",
                note = "打车",
                occurredAt = 1_120_000L,
                source = TxSource.AI,
            )
            assertTrue(repository.mergeDuplicate(automaticId, chatId))
            val all = repository.all()
            assertEquals(1, all.size)
            assertEquals(automaticId, all.first().id)
            assertEquals("transport", all.first().categoryId)
        } finally {
            database.close()
        }
    }
}
