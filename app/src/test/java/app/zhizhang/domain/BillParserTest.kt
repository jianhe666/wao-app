package app.zhizhang.domain

import app.zhizhang.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class BillParserTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 10, 23, 14, 0)
    private val parser = BillParser()

    private fun parse(input: String, at: LocalDateTime = now) =
        parser.parse(input, now = at, zone = zone, defaultAccountId = "wechat")

    @Test
    fun `parses bank sms and ignores balance`() {
        val bills = parse(
            "【招商银行】您账户 ****1234 于 10月22日 12:30 支出人民币 156.30 元，余额 2,015.30 元。",
        )
        assertEquals(1, bills.size)
        val bill = bills.first()
        assertEquals(15_630L, bill.amountCents)
        assertEquals(TxType.EXPENSE, bill.type)
        assertEquals("bank", bill.accountId)
        assertEquals("other_expense", bill.categoryId)
        assertEquals(true, bill.merchant.isBlank())
        val time = java.time.Instant.ofEpochMilli(bill.occurredAt).atZone(zone)
        assertEquals(10, time.monthValue)
        assertEquals(22, time.dayOfMonth)
        assertEquals(12, time.hour)
    }

    @Test
    fun `does not leave yuan suffix in merchant`() {
        val bill = parse("支付宝 付款成功 美团外卖 支出 38.50元 14:38").single()
        assertEquals("美团外卖", bill.merchant)
        assertEquals(3_850L, bill.amountCents)
        assertEquals("food", bill.categoryId)
    }

    @Test
    fun `parses wechat notification with merchant and category`() {
        val bill = parse("微信支付 付款成功 ¥19.90 商户：瑞幸咖啡").single()
        assertEquals(1_990L, bill.amountCents)
        assertEquals(TxType.EXPENSE, bill.type)
        assertEquals("瑞幸咖啡", bill.merchant)
        assertEquals("wechat", bill.accountId)
        assertEquals("food", bill.categoryId)
        assertTrue(bill.confidence >= 0.7f)
    }

    @Test
    fun `parses multiple bank sms lines`() {
        val bills = parse(
            """
            【招商银行】您账户 ****1234 于 10月22日 12:30 支出人民币 25.00 元，余额 1,000.00 元。
            【招商银行】您账户 ****1234 于 10月22日 18:10 支出人民币 32.00 元，余额 968.00 元。
            """.trimIndent(),
        )
        assertEquals(2, bills.size)
        assertEquals(listOf(2_500L, 3_200L), bills.map { it.amountCents })
    }

    @Test
    fun `parses multiline alipay field block`() {
        val bill = parse(
            """
            支付宝
            2026-10-21 12:30:00
            付款成功
            -25.00
            收款方：美团外卖
            """.trimIndent(),
        ).single()
        assertEquals(2_500L, bill.amountCents)
        assertEquals("美团外卖", bill.merchant)
        assertEquals("alipay", bill.accountId)
        assertEquals("food", bill.categoryId)
        val time = java.time.Instant.ofEpochMilli(bill.occurredAt).atZone(zone)
        assertEquals(21, time.dayOfMonth)
        assertEquals(12, time.hour)
    }

    @Test
    fun `filters discount and balance amounts`() {
        val bill = parse("支付宝 支出 ¥25.00 优惠 ¥2.00 余额 ¥1,000.00").single()
        assertEquals(2_500L, bill.amountCents)
    }

    @Test
    fun `detects salary income`() {
        val bill = parse("工资入账 人民币 18,500.00 元").single()
        assertEquals(TxType.INCOME, bill.type)
        assertEquals(1_850_000L, bill.amountCents)
        assertEquals("salary", bill.categoryId)
    }

    @Test
    fun `detects refund income`() {
        val bill = parse("微信支付 退款到账 ¥25.00").single()
        assertEquals(TxType.INCOME, bill.type)
        assertEquals("refund", bill.categoryId)
        assertEquals("wechat", bill.accountId)
    }

    @Test
    fun `skips failed or cancelled payments`() {
        assertTrue(parse("微信支付 支付失败 ¥25.00").isEmpty())
        assertTrue(parse("支付宝 待支付 订单金额 ¥25.00").isEmpty())
    }

    @Test
    fun `skips text without amount`() {
        assertTrue(parse("今天下午和朋友去看了电影，很开心。").isEmpty())
    }

    @Test
    fun `infers previous year for december bill in january`() {
        val bills = parse(
            "12月30日 12:00 星巴克 32元",
            at = LocalDateTime.of(2026, 1, 5, 10, 0),
        )
        val time = java.time.Instant.ofEpochMilli(bills.single().occurredAt).atZone(zone)
        assertEquals(2025, time.year)
        assertEquals(12, time.monthValue)
    }

    @Test
    fun `deduplicates identical records in one batch`() {
        val text = "微信支付 付款成功 ¥19.90 商户：瑞幸咖啡"
        val bills = parse("$text\n$text")
        assertEquals(1, bills.size)
    }

    @Test
    fun `parses simple merchant amount line`() {
        val bill = parse("10月23日 08:15 肯德基 -32.00").single()
        assertEquals(3_200L, bill.amountCents)
        assertEquals("肯德基", bill.merchant)
        assertEquals("food", bill.categoryId)
    }

    @Test
    fun `normalizes full width amount and comma`() {
        val bill = parse("支付宝 付款成功 ￥１,２３４.５０ 收款方：京东").single()
        assertEquals(123_450L, bill.amountCents)
        assertEquals("shopping", bill.categoryId)
    }

    @Test
    fun `parses income with date and merchant`() {
        val bill = parse("10月20日 09:00 公司 奖金 5,000元").single()
        assertNotNull(bill)
        assertEquals(TxType.INCOME, bill.type)
        assertEquals(500_000L, bill.amountCents)
        assertEquals("bonus", bill.categoryId)
    }
}
