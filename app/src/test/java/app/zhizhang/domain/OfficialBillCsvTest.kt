package app.zhizhang.domain

import app.zhizhang.data.TransactionStatus
import app.zhizhang.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.ZoneId

class OfficialBillCsvTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 10, 24, 12, 0)

    @Test
    fun `wechat bill skips transfers and failed records`() {
        val csv = """
            微信支付账单明细
            微信昵称：[本人]
            起始时间：[2026-10-01 00:00:00]
            ----------------------
            交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
            2026-10-23 12:30:00,商户消费,瑞幸咖啡,拿铁,支出,¥19.90,零钱,支付成功,4200001234567890,M001,
            2026-10-23 09:00:00,转账,张三,转账,支出,¥100.00,零钱,支付成功,420000999,,
            2026-10-22 09:00:00,商户消费,某商户,商品,支出,¥5.00,零钱,支付失败,420000888,,
        """.trimIndent()

        val result = OfficialBillCsv.decode(csv.toByteArray(), "微信支付账单.csv", zone = zone, now = now)
        assertEquals("微信", result.source)
        assertEquals(1, result.bills.size)
        assertEquals(1_990L, result.bills.first().amountCents)
        assertEquals("瑞幸咖啡", result.bills.first().merchant)
        assertEquals("4200001234567890", result.bills.first().externalId)
        assertEquals(TxType.EXPENSE, result.bills.first().type)
        assertEquals(TransactionStatus.SUCCESS, result.bills.first().status)
        assertEquals(false, result.bills.first().needsConfirmation)
        assertEquals(2, result.skipped)
    }

    @Test
    fun `alipay gb18030 bill is decoded and keeps refund as income`() {
        val csv = """
            支付宝交易记录明细查询
            账号：[test@example.com]
            起始日期：[2026-10-01 00:00:00]
            ----------------------
            交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注
            2026-10-23 12:30:00,餐饮美食,美团外卖,商户,午饭,支出,38.50,余额,交易成功,202610230001,M001,
            2026-10-23 13:00:00,退款,美团外卖,商户,退款,收入,38.50,余额,退款成功,202610230002,M002,
            2026-10-23 14:00:00,转账,张三,账号,转账,不计收支,100.00,余额,交易成功,202610230003,M003,
        """.trimIndent()
        val bytes = csv.toByteArray(Charset.forName("GB18030"))

        val result = OfficialBillCsv.decode(bytes, "支付宝账单.csv", zone = zone, now = now)
        assertEquals("支付宝", result.source)
        assertEquals(2, result.bills.size)
        val expense = result.bills.first { it.type == TxType.EXPENSE }
        assertEquals(3_850L, expense.amountCents)
        assertEquals("202610230001", expense.externalId)
        val refund = result.bills.first { it.type == TxType.INCOME }
        assertEquals(TransactionStatus.REFUNDED, refund.status)
        assertEquals("refund", refund.categoryId)
        assertTrue(!refund.needsConfirmation)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `own csv format still falls back`() {
        val csv = """
            date,time,type,category,account,merchant,note,amount,source
            2026-10-23,12:30,支出,餐饮,微信,瑞幸咖啡,午饭,19.90,手动
        """.trimIndent()
        val result = OfficialBillCsv.decode(csv.toByteArray(), "zhizhang.csv", zone = zone, now = now)
        assertEquals("有数 CSV", result.source)
        assertEquals(1, result.bills.size)
        assertEquals(1_990L, result.bills.first().amountCents)
    }
}
