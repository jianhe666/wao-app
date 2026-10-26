package app.zhizhang.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReimbursementRepositoryTest {

    @Test
    fun `completing a reimbursement creates linked income and marks original done`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = TransactionRepository(database, SettingsRepository(context))
            val now = System.currentTimeMillis()
            val originalId = repository.addManual(
                amountCents = 2_640,
                type = TxType.EXPENSE,
                categoryId = "transport",
                accountId = "wechat",
                merchant = "滴滴出行",
                note = "",
                occurredAt = now,
                reimbursementStatus = ReimbursementStatus.PENDING,
            )

            assertTrue(repository.completeReimbursement(originalId, "bank", now))
            val all = repository.all()
            val original = all.first { it.id == originalId }
            assertEquals(ReimbursementStatus.DONE.name, original.reimbursementStatus)

            val income = all.firstOrNull { it.reimbursementForId == originalId }
            assertNotNull(income)
            assertEquals(TxType.INCOME.name, income?.type)
            assertEquals("reimbursement", income?.categoryId)
            assertEquals(2_640L, income?.amountCents)
            assertEquals("bank", income?.accountId)

            assertFalse(repository.completeReimbursement(originalId, "bank", now))
        } finally {
            database.close()
        }
    }
}
