package app.zhizhang.domain

import app.zhizhang.data.TxType
import app.zhizhang.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryLearningTest {

    private fun tx(
        merchant: String,
        category: String = "other_expense",
        type: TxType = TxType.EXPENSE,
    ) = TransactionEntity(
        amountCents = 1_000,
        type = type.name,
        categoryId = category,
        accountId = "wechat",
        merchant = merchant,
        occurredAt = 1_000L,
    )

    @Test
    fun `learned rules and already categorized merchants are skipped`() {
        val candidates = CategoryLearning.candidates(
            transactions = listOf(
                tx("瑞幸咖啡", "other_expense"),
                tx("瑞幸咖啡", "food"),
                tx("美团外卖", "other_expense"),
                tx("张三", "social"),
                tx("红包", "social"),
            ),
            learnedRules = mapOf("滴滴" to "transport"),
        )
        assertEquals(listOf("美团外卖"), candidates.map { it.merchant })
    }

    @Test
    fun `pending suggestions are not suggested again`() {
        val candidates = CategoryLearning.candidates(
            transactions = listOf(tx("新商户")),
            learnedRules = emptyMap(),
            pendingKeys = setOf("新商户"),
        )
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `duplicate merchants collapse to one candidate`() {
        val candidates = CategoryLearning.candidates(
            transactions = listOf(tx("新商户"), tx("新商户")),
            learnedRules = emptyMap(),
        )
        assertEquals(1, candidates.size)
        assertEquals("新商户", candidates.first().merchant)
    }
}
