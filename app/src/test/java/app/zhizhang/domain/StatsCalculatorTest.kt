package app.zhizhang.domain

import app.zhizhang.data.TxSource
import app.zhizhang.data.TxType
import app.zhizhang.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class StatsCalculatorTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 23)
    private val month = YearMonth.of(2026, 10)

    private fun tx(
        amount: Long,
        category: String,
        merchant: String,
        day: Int,
        type: TxType = TxType.EXPENSE,
        account: String = "wechat",
    ) = TransactionEntity(
        amountCents = amount,
        type = type.name,
        categoryId = category,
        accountId = account,
        merchant = merchant,
        occurredAt = LocalDateTime.of(2026, 10, day, 12, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli(),
        source = TxSource.IMPORT.name,
    )

    @Test
    fun `computes totals slices and daily average`() {
        val stats = StatsCalculator.build(
            month = month,
            transactions = listOf(
                tx(3_000, "food", "美团外卖", 2),
                tx(2_000, "food", "瑞幸咖啡", 3),
                tx(5_000, "shopping", "盒马鲜生", 4),
                tx(1_000, "transport", "地铁", 5),
                tx(1_850_000, "salary", "公司", 6, TxType.INCOME),
            ),
            previousExpenseCents = 10_000,
            budgetCents = 20_000,
            zone = zone,
            today = today,
        )
        assertEquals(11_000L, stats.expenseCents)
        assertEquals(1_850_000L, stats.incomeCents)
        assertEquals(1_839_000L, stats.balanceCents)
        assertEquals(3, stats.slices.size)
        assertEquals("food", stats.slices.first().category.id)
        assertEquals(5_000L, stats.slices.first().amountCents)
        assertEquals(11_000L / 23, stats.dailyAverageCents)
        assertEquals(31, stats.dailyExpenses.size)
        assertEquals(3_000L, stats.dailyExpenses[1])
        assertNotNull(stats.largestExpense)
        assertEquals(5_000L, stats.largestExpense?.amountCents)
        assertEquals("盒马鲜生", stats.topMerchants.first().merchant)
        assertEquals(1, stats.topMerchants.first().count)
        assertTrue(stats.insights.isNotEmpty())
    }


    @Test
    fun `frequent shop outranks larger one and transfers are excluded`() {
        val stats = StatsCalculator.build(
            month = month,
            transactions = listOf(
                tx(1_000, "food", "瑞幸咖啡", 2),
                tx(1_000, "food", "瑞幸咖啡", 3),
                tx(5_000, "shopping", "盒马鲜生", 4),
                tx(9_000, "social", "张三", 5),
                tx(8_000, "other_expense", "李四", 6),
            ),
            previousExpenseCents = 0,
            budgetCents = 0,
            zone = zone,
            today = today,
        )
        assertEquals("瑞幸咖啡", stats.topMerchants.first().merchant)
        assertEquals(2, stats.topMerchants.first().count)
        assertTrue(stats.topMerchants.none { it.merchant == "张三" })
        assertTrue(stats.topMerchants.none { it.merchant == "李四" })
    }


    @Test
    fun `online merchants are excluded from frequent stores`() {
        val stats = StatsCalculator.build(
            month = month,
            transactions = listOf(
                tx(1_000, "shopping", "拼多多", 2),
                tx(2_000, "transport", "哈啰单车", 3),
                tx(3_000, "food", "美团外卖", 4),
                tx(3_000, "food", "美团", 6),
                tx(4_000, "shopping", "盒马鲜生", 5),
            ),
            previousExpenseCents = 0,
            budgetCents = 0,
            zone = zone,
            today = today,
        )
        assertEquals(listOf("盒马鲜生"), stats.topMerchants.map { it.merchant })
    }


    @Test
    fun `account breakdown aggregates by payment account`() {
        val stats = StatsCalculator.build(
            month = month,
            transactions = listOf(
                tx(2_000, "food", "瑞幸咖啡", 2, account = "wechat"),
                tx(3_000, "shopping", "盒马鲜生", 3, account = "alipay"),
                tx(1_000, "food", "星巴克", 4, account = "wechat"),
            ),
            previousExpenseCents = 0,
            budgetCents = 0,
            zone = zone,
            today = today,
        )
        assertEquals(2, stats.accountSlices.size)
        assertEquals(3_000L, stats.accountSlices.first { it.account.id == "wechat" }.amountCents)
        assertEquals(3_000L, stats.accountSlices.first { it.account.id == "alipay" }.amountCents)
    }

    @Test
    fun `payment platform prefixes merge into one offline store`() {
        val stats = StatsCalculator.build(
            month = month,
            transactions = listOf(
                tx(1_000, "food", "微信支付-星巴克", 2),
                tx(1_000, "food", "支付宝-星巴克", 3),
                tx(2_000, "food", "星巴克", 4),
            ),
            previousExpenseCents = 0,
            budgetCents = 0,
            zone = zone,
            today = today,
        )
        assertEquals("星巴克", stats.topMerchants.first().merchant)
        assertEquals(3, stats.topMerchants.first().count)
    }

    @Test
    fun `no expenses produces zero slices and no insights`() {
        val stats = StatsCalculator.build(
            month = month,
            transactions = listOf(tx(1_000_000, "salary", "公司", 6, TxType.INCOME)),
            previousExpenseCents = 0,
            budgetCents = 0,
            zone = zone,
            today = today,
        )
        assertEquals(0L, stats.expenseCents)
        assertTrue(stats.slices.isEmpty())
        assertTrue(stats.insights.isEmpty())
    }

    @Test
    fun `budget overrun creates warning insight`() {
        val stats = StatsCalculator.build(
            month = month,
            transactions = listOf(tx(25_000, "food", "美团", 2)),
            previousExpenseCents = 0,
            budgetCents = 20_000,
            zone = zone,
            today = today,
        )
        assertTrue(stats.insights.any { it.tone == InsightTone.WARN })
    }
}
