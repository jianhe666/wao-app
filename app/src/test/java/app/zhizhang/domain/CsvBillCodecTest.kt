package app.zhizhang.domain

import app.zhizhang.data.TxSource
import app.zhizhang.data.TxType
import app.zhizhang.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class CsvBillCodecTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun entity(
        amount: Long,
        type: TxType,
        category: String,
        account: String,
        merchant: String,
        note: String,
        day: Int,
    ) = TransactionEntity(
        amountCents = amount,
        type = type.name,
        categoryId = category,
        accountId = account,
        merchant = merchant,
        note = note,
        occurredAt = LocalDateTime.of(2026, 10, day, 12, 30)
            .atZone(zone).toInstant().toEpochMilli(),
        source = TxSource.IMPORT.name,
    )

    @Test
    fun `roundtrip preserves bills`() {
        val source = listOf(
            entity(3_850, TxType.EXPENSE, "food", "wechat", "美团外卖", "午饭", 1),
            entity(1_850_000, TxType.INCOME, "salary", "bank", "公司", "", 2),
        )
        val csv = CsvBillCodec.encode(source, zone)
        assertTrue(csv.startsWith("\uFEFFdate,time,type,category,account,merchant,note,amount,source"))
        val result = CsvBillCodec.decode(csv, zone)
        assertEquals(2, result.bills.size)
        assertEquals(0, result.skipped)
        val food = result.bills.first { it.merchant == "美团外卖" }
        assertEquals(3_850L, food.amountCents)
        assertEquals(TxType.EXPENSE, food.type)
        assertEquals("food", food.categoryId)
        assertEquals("wechat", food.accountId)
        assertEquals("午饭", food.note)
    }

    @Test
    fun `decodes legacy chinese headers`() {
        val csv = """
            日期,时间,类型,分类,账户,商家,备注,金额(元),来源
            2026-10-03,08:15,支出,交通,支付宝,地铁,,4.00,导入
            2026-10-04,09:00,收入,工资,银行卡,公司,,18500.00,手动
        """.trimIndent()
        val result = CsvBillCodec.decode(csv, zone)
        assertEquals(2, result.bills.size)
        assertEquals("transport", result.bills[1].categoryId)
        assertEquals(TxType.INCOME, result.bills[0].type)
    }

    @Test
    fun `supports quoted commas and skips invalid rows`() {
        val csv = """
            date,time,type,category,account,merchant,note,amount,source
            2026-10-05,12:00,支出,餐饮,微信,"星巴克,国贸店","拿铁, 大杯",32.00,导入
            not-a-date,12:00,支出,餐饮,微信,坏数据,,20.00,导入
            2026-10-06,13:00,支出,购物,支付宝,京东,,299.00,导入
        """.trimIndent()
        val result = CsvBillCodec.decode(csv, zone)
        assertEquals(2, result.bills.size)
        assertEquals(1, result.skipped)
        assertTrue(result.bills.any { it.merchant == "星巴克,国贸店" })
    }

    @Test
    fun `rejects file without required columns`() {
        val csv = "商家,金额\n美团,38.50"
        val error = runCatching { CsvBillCodec.decode(csv, zone) }.exceptionOrNull()
        assertTrue(error is CsvException)
    }
}
