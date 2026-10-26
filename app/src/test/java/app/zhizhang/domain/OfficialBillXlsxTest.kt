package app.zhizhang.domain

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfficialBillXlsxTest {

    @Test
    fun `wechat xlsx header and values are imported`() {
        val zip = buildXlsx(
            sharedStrings = listOf(
                "微信支付账单明细",
                "交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)",
                "支付方式", "当前状态", "交易单号", "商户单号", "备注",
                "2026-10-23 12:30:00", "商户消费", "瑞幸咖啡", "拿铁", "支出",
                "零钱", "支付成功", "4200001234567890", "M001", "",
            ),
            dataRow = """
                <row r="3">
                  <c r="A3" t="s"><v>12</v></c>
                  <c r="B3" t="s"><v>13</v></c>
                  <c r="C3" t="s"><v>14</v></c>
                  <c r="D3" t="s"><v>15</v></c>
                  <c r="E3" t="s"><v>16</v></c>
                  <c r="F3"><v>19.90</v></c>
                  <c r="G3" t="s"><v>17</v></c>
                  <c r="H3" t="s"><v>18</v></c>
                  <c r="I3" t="s"><v>19</v></c>
                  <c r="J3" t="s"><v>20</v></c>
                  <c r="K3" t="s"><v>21</v></c>
                </row>
            """.trimIndent(),
        )
        val result = OfficialBillCsv.decode(
            bytes = zip,
            fileName = "微信支付账单.xlsx",
            zone = ZoneId.of("Asia/Shanghai"),
            now = LocalDateTime.of(2026, 10, 24, 12, 0),
        )
        assertEquals("微信", result.source)
        assertEquals(1, result.bills.size)
        assertEquals(1_990L, result.bills.first().amountCents)
        assertEquals("瑞幸咖啡", result.bills.first().merchant)
        assertEquals("4200001234567890", result.bills.first().externalId)
        assertTrue(result.bills.first().categoryId.isNotBlank())
    }

    private fun buildXlsx(sharedStrings: List<String>, dataRow: String): ByteArray {
        val sharedXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            sharedStrings.forEach { append("<si><t>").append(escapeXml(it)).append("</t></si>") }
            append("</sst>")
        }
        val sheetXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
            append("<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c></row>")
            append("<row r=\"2\">")
            for (index in 1..11) {
                append("<c r=\"${column(index)}2\" t=\"s\"><v>$index</v></c>")
            }
            append("</row>")
            append(dataRow)
            append("</sheetData></worksheet>")
        }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            add(zip, "xl/sharedStrings.xml", sharedXml)
            add(zip, "xl/worksheets/sheet1.xml", sheetXml)
        }
        return output.toByteArray()
    }

    private fun add(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun column(index: Int): String {
        var value = index
        val result = StringBuilder()
        while (value > 0) {
            value--
            result.insert(0, ('A' + value % 26))
            value /= 26
        }
        return result.toString()
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
