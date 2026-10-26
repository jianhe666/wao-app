package app.zhizhang.domain

import app.zhizhang.data.TxSource
import app.zhizhang.data.TxType
import app.zhizhang.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateMergeTest {

    private fun tx(
        id: Long,
        source: TxSource,
        amount: Long = 2_640,
        occurredAt: Long = 1_000_000L,
    ) = TransactionEntity(
        id = id,
        amountCents = amount,
        type = TxType.EXPENSE.name,
        categoryId = "transport",
        accountId = "wechat",
        merchant = "滴滴出行",
        occurredAt = occurredAt,
        source = source.name,
    )

    @Test
    fun `automatic and chat records with same amount and close time are suggested`() {
        val pairs = DuplicateMerge.find(
            listOf(
                tx(1, TxSource.NOTIFICATION, occurredAt = 1_000_000L),
                tx(2, TxSource.AI, occurredAt = 1_000_000L + 3 * 60_000L),
                tx(3, TxSource.NOTIFICATION, occurredAt = 2_000_000L),
            ),
        )
        assertEquals(1, pairs.size)
        assertEquals(1L, pairs.first().automatic.id)
        assertEquals(2L, pairs.first().userRecord.id)
    }

    @Test
    fun `different amount or time is not suggested`() {
        val pairs = DuplicateMerge.find(
            listOf(
                tx(1, TxSource.NOTIFICATION),
                tx(2, TxSource.AI, amount = 2_641),
                tx(3, TxSource.AI, occurredAt = 1_000_000L + 11 * 60_000L),
            ),
        )
        assertTrue(pairs.isEmpty())
    }
}
