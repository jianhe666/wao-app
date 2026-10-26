package app.zhizhang.domain

import app.zhizhang.data.Accounts
import app.zhizhang.data.Categories
import app.zhizhang.data.TxSource
import app.zhizhang.data.TxType
import app.zhizhang.data.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CsvDecodeResult(
    val bills: List<ParsedBill>,
    val skipped: Int,
)

class CsvException(message: String) : Exception(message)

/**
 * 有数统一账单格式（CSV，UTF-8 with BOM）。
 *
 * date,time,type,category,account,merchant,note,amount,source
 * 2026-10-23,12:30,支出,餐饮,微信,美团外卖,午饭,38.50,导入
 *
 * - 金额永远为正数，方向由 type 表示。
 * - category / account 使用中文名称；也兼容分类 id。
 * - 该格式用于账单交换；完整恢复（规则、预算等）请使用 JSON 备份。
 */
object CsvBillCodec {

    const val HEADER = "date,time,type,category,account,merchant,note,amount,source"

    private val dateFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("yyyy/M/d"),
        DateTimeFormatter.ofPattern("yyyy.M.d"),
        DateTimeFormatter.ofPattern("yyyy年M月d日"),
        DateTimeFormatter.ofPattern("yyyyMMdd"),
    )

    fun encode(
        transactions: List<TransactionEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val builder = StringBuilder("\uFEFF")
        builder.append(HEADER).append("\r\n")
        transactions.sortedBy { it.occurredAt }.forEach { transaction ->
            val time = Instant.ofEpochMilli(transaction.occurredAt).atZone(zone)
            val category = Categories.byId(transaction.categoryId)
            val account = Accounts.byId(transaction.accountId)
            val row = listOf(
                time.format(DateTimeFormatter.ISO_LOCAL_DATE),
                time.format(DateTimeFormatter.ofPattern("HH:mm")),
                transaction.txType.label,
                category.name,
                account.name,
                transaction.merchant,
                transaction.note,
                String.format(Locale.US, "%.2f", transaction.amountCents / 100.0),
                transaction.txSource.label,
            )
            builder.append(row.joinToString(",") { cell -> escape(cell) }).append("\r\n")
        }
        return builder.toString()
    }

    fun decode(
        csv: String,
        zone: ZoneId = ZoneId.systemDefault(),
        now: LocalDateTime = LocalDateTime.now(),
    ): CsvDecodeResult {
        val cleaned = csv.removePrefix("\uFEFF").trim()
        if (cleaned.isBlank()) throw CsvException("文件是空的")
        val rows = parseCsv(cleaned)
        if (rows.isEmpty()) throw CsvException("没有找到表格内容")

        val header = rows.first().map { normalizeHeader(it) }
        val dateIndex = findHeader(header, "date", "日期", "交易日期")
        val amountIndex = findHeader(header, "amount", "金额", "金额(元)", "金额（元）", "交易金额")
        if (dateIndex == -1 || amountIndex == -1) {
            throw CsvException("CSV 缺少 date 或 amount 列，请使用有数导出的账单格式")
        }
        val timeIndex = findHeader(header, "time", "时间", "交易时间")
        val typeIndex = findHeader(header, "type", "类型", "收支")
        val categoryIndex = findHeader(header, "category", "分类", "类别")
        val accountIndex = findHeader(header, "account", "账户", "账号", "支付方式")
        val merchantIndex = findHeader(header, "merchant", "商家", "商户", "对方")
        val noteIndex = findHeader(header, "note", "备注", "说明")
        val sourceIndex = findHeader(header, "source", "来源")

        var skipped = 0
        val bills = mutableListOf<ParsedBill>()
        for (row in rows.drop(1)) {
            if (row.all { it.isBlank() }) continue
            val dateRaw = row.getOrNull(dateIndex).orEmpty()
            val amountRaw = row.getOrNull(amountIndex).orEmpty()
            val date = parseDate(dateRaw)
            val cents = parseAmountCents(amountRaw)
            if (date == null || cents == null) {
                skipped++
                continue
            }
            val time = parseTime(row.getOrNull(timeIndex).orEmpty())
            val typeRaw = row.getOrNull(typeIndex).orEmpty()
            val type = when {
                typeRaw.contains("收入") || typeRaw.equals("income", true) -> TxType.INCOME
                typeRaw.contains("支出") || typeRaw.equals("expense", true) -> TxType.EXPENSE
                amountRaw.trim().startsWith("+") -> TxType.INCOME
                else -> TxType.EXPENSE
            }
            val categoryRaw = row.getOrNull(categoryIndex).orEmpty().trim()
            val categoryId = Categories.forType(type).firstOrNull {
                it.id.equals(categoryRaw, true) || it.name == categoryRaw
            }?.id ?: Categories.fallbackFor(type).id
            val accountRaw = row.getOrNull(accountIndex).orEmpty().trim()
            val accountId = Accounts.all.firstOrNull {
                it.id.equals(accountRaw, true) || it.name == accountRaw
            }?.id ?: "other"
            val merchant = row.getOrNull(merchantIndex).orEmpty().trim().take(40)
            val note = row.getOrNull(noteIndex).orEmpty().trim().take(120)
            val sourceRaw = row.getOrNull(sourceIndex).orEmpty().trim()
            val source = TxSource.entries.firstOrNull {
                it.label == sourceRaw || it.name.equals(sourceRaw, true)
            } ?: TxSource.IMPORT
            val occurred = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
            val rawRow = row.joinToString(",")
            bills += ParsedBill(
                amountCents = cents,
                type = type,
                occurredAt = occurred,
                merchant = merchant,
                categoryId = categoryId,
                accountId = accountId,
                note = note,
                rawText = rawRow,
                confidence = 0.95f,
                fingerprint = BillFingerprint.of(type, cents, occurred, merchant, rawRow),
            )
            if (source == TxSource.NOTIFICATION) {
                // 保留来源文案只用于导出；导入后统一按导入来源记录。
            }
        }
        return CsvDecodeResult(
            bills = bills.distinctBy { it.fingerprint }.sortedByDescending { it.occurredAt },
            skipped = skipped,
        )
    }

    private fun parseDate(raw: String): LocalDate? {
        val clean = raw.trim()
        if (clean.isBlank()) return null
        for (format in dateFormats) {
            runCatching { LocalDate.parse(clean, format) }.getOrNull()?.let { return it }
        }
        // 兼容 "2026-10-23 12:30" 这样把时间写进日期列的文件。
        val prefix = clean.take(10)
        return runCatching { LocalDate.parse(prefix) }.getOrNull()
    }

    private fun parseTime(raw: String): LocalTime {
        val clean = raw.trim()
        if (clean.isBlank()) return LocalTime.NOON
        return runCatching { LocalTime.parse(clean) }.getOrElse {
            runCatching {
                val parts = clean.split(":")
                LocalTime.of(parts[0].toInt(), parts.getOrNull(1)?.take(2)?.toInt() ?: 0)
            }.getOrDefault(LocalTime.NOON)
        }
    }

    private fun parseAmountCents(raw: String): Long? {
        val clean = raw
            .replace("¥", "")
            .replace("￥", "")
            .replace(",", "")
            .replace("元", "")
            .replace("+", "")
            .replace(" ", "")
            .trim()
        val value = clean.toDoubleOrNull() ?: return null
        val cents = Math.round(kotlin.math.abs(value) * 100)
        return cents.takeIf { it > 0 }
    }

    private fun normalizeHeader(raw: String): String =
        raw.trim().removePrefix("\uFEFF").lowercase(Locale.ROOT)

    private fun findHeader(header: List<String>, vararg aliases: String): Int {
        aliases.forEach { alias ->
            val index = header.indexOf(alias.lowercase(Locale.ROOT))
            if (index >= 0) return index
        }
        return -1
    }

    private fun escape(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            return value
        }
        return "\"${value.replace("\"", "\"\"")}\""
    }

    /** RFC 4180 解析，支持引号、逗号、换行和 "" 转义。 */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                inQuotes && char == '"' && text.getOrNull(index + 1) == '"' -> {
                    cell.append('"')
                    index++
                }

                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    row += cell.toString()
                    cell.clear()
                }

                (char == '\n' || char == '\r') && !inQuotes -> {
                    if (char == '\r' && text.getOrNull(index + 1) == '\n') index++
                    row += cell.toString()
                    cell.clear()
                    if (row.any { it.isNotBlank() }) rows += row.toList()
                    row.clear()
                }

                else -> cell.append(char)
            }
            index++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            if (row.any { it.isNotBlank() }) rows += row.toList()
        }
        return rows
    }
}
