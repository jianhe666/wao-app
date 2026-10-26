package app.zhizhang.domain

import app.zhizhang.data.Accounts
import app.zhizhang.data.Categories
import app.zhizhang.data.TransactionStatus
import app.zhizhang.data.TxType
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BillImportResult(
    val bills: List<ParsedBill>,
    val skipped: Int,
    val warnings: List<String>,
    val source: String,
)

class BillImportException(message: String) : Exception(message)

/**
 * 微信支付 / 支付宝官方账单 CSV 适配层。
 *
 * 两份导出都在正式表头前带多行说明，且列名、编码和状态文案不完全一致。
 * 本类只做格式适配，不把转账、充值、信用卡还款当成普通消费。
 */
object OfficialBillCsv {

    private val dateTimeFormats = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
    )

    fun decode(
        bytes: ByteArray,
        fileName: String = "",
        rules: Map<String, String> = emptyMap(),
        zone: ZoneId = ZoneId.systemDefault(),
        now: LocalDateTime = LocalDateTime.now(),
    ): BillImportResult {
        if (bytes.isEmpty()) throw BillImportException("账单文件是空的")
        if (bytes.size > 10_000_000) throw BillImportException("账单文件太大，请拆分后再导入")
        if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            return OfficialBillXlsx.decode(bytes, fileName, rules, zone, now)
        }
        val text = decodeText(bytes)
        val rows = parseCsv(text)
        if (rows.isEmpty()) throw BillImportException("没有读到表格内容")

        val headerIndex = rows.indexOfFirst { row ->
            val values = row.map(::normalize)
            values.any { it.contains("交易时间") || it == "交易日期" } &&
                values.any { it.contains("金额") }
        }
        if (headerIndex < 0) {
            // 兼容哇哦记账自己导出的 CSV。
            val own = runCatching { CsvBillCodec.decode(text, zone, now) }
                .getOrElse { throw BillImportException("无法识别这个 CSV 的表头，请使用微信/支付宝官方导出文件") }
            return BillImportResult(
                bills = own.bills,
                skipped = own.skipped,
                warnings = emptyList(),
                source = "哇哦记账 CSV",
            )
        }

        val header = rows[headerIndex].map(::normalize)
        val allText = rows.take(headerIndex).joinToString(" ") { it.joinToString(" ") }
        val source = detectSource(header, allText, fileName)
        val amountIndex = find(header, "金额", "金额(元)", "金额（元）", "交易金额")
        val timeIndex = find(header, "交易时间", "交易日期", "时间")
        if (amountIndex < 0 || timeIndex < 0) {
            throw BillImportException("账单缺少交易时间或金额列")
        }
        val directionIndex = find(header, "收/支", "收支", "收/支类型")
        val statusIndex = find(header, "当前状态", "交易状态", "状态")
        val merchantIndex = find(header, "交易对方", "对方", "商户", "商家")
        val productIndex = find(header, "商品", "商品说明", "商品名称")
        val transactionIdIndex = find(header, "交易单号", "交易订单号", "交易号")
        val merchantIdIndex = find(header, "商户单号", "商家订单号")
        val categoryIndex = find(header, "交易分类", "交易类型", "分类")
        val noteIndex = find(header, "备注")
        val paymentIndex = find(header, "支付方式", "收/付款方式")

        var skipped = 0
        val warnings = mutableListOf<String>()
        val bills = mutableListOf<ParsedBill>()
        for (index in headerIndex + 1 until rows.size) {
            if (bills.size >= 5_000) {
                warnings += "单次最多导入 5000 笔，已截断"
                break
            }
            val row = rows[index]
            if (row.all { it.isBlank() }) continue
            val statusRaw = row.getOrNull(statusIndex).orEmpty().trim()
            val directionRaw = row.getOrNull(directionIndex).orEmpty().trim()
            val categoryRaw = row.getOrNull(categoryIndex).orEmpty().trim()
            val merchantRaw = row.getOrNull(merchantIndex).orEmpty().trim()
            val product = row.getOrNull(productIndex).orEmpty().trim()
            val combined = "$directionRaw $statusRaw $categoryRaw $merchantRaw $product"
            val isRefund = statusRaw.contains("退款") || categoryRaw.contains("退款")

            if (directionRaw.contains("不计收支") ||
                categoryRaw.containsAny(EXCLUDED_CATEGORIES) ||
                (!isRefund && product.containsAny(EXCLUDED_PRODUCTS))
            ) {
                skipped++
                continue
            }
            if (!isRefund && statusRaw.containsAny(FAILED_STATUSES)) {
                skipped++
                continue
            }

            val amount = parseCents(row.getOrNull(amountIndex).orEmpty())
            if (amount == null) {
                skipped++
                continue
            }
            val parsedDateTime = parseDateTime(row.getOrNull(timeIndex).orEmpty(), zone)
            if (parsedDateTime == null) {
                skipped++
                continue
            }
            val occurredAt = parsedDateTime.atZone(zone).toInstant().toEpochMilli()

            val type = when {
                isRefund -> TxType.INCOME
                directionRaw.contains("收入") || directionRaw.contains("收款") -> TxType.INCOME
                directionRaw.contains("支出") -> TxType.EXPENSE
                amount < 0 -> TxType.EXPENSE
                else -> TxType.EXPENSE
            }
            val merchant = merchantRaw.ifBlank { product }.take(40)
            val category = when {
                isRefund -> "refund"
                !merchant.isBlank() -> CategoryClassifier(rules).classify("$merchant $product $categoryRaw", type)
                else -> Categories.fallbackFor(type).id
            }
            val successLike = statusRaw.containsAny(SUCCESS_STATUSES) ||
                statusRaw.contains("成功") ||
                statusRaw.contains("完成") ||
                statusRaw.contains("已存入") ||
                statusRaw.contains("已到账")
            val status = when {
                isRefund -> TransactionStatus.REFUNDED
                statusRaw.isBlank() || successLike -> TransactionStatus.SUCCESS
                else -> TransactionStatus.PENDING
            }
            val externalId = row.getOrNull(transactionIdIndex).orEmpty().trim().ifBlank {
                row.getOrNull(merchantIdIndex).orEmpty().trim()
            }.takeIf { it.length >= 6 }
            val note = listOf(product, row.getOrNull(noteIndex).orEmpty().trim())
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .take(120)
            val account = when {
                source == "微信" -> "wechat"
                source == "支付宝" -> "alipay"
                row.getOrNull(paymentIndex).orEmpty().contains("微信") -> "wechat"
                row.getOrNull(paymentIndex).orEmpty().contains("支付宝") -> "alipay"
                else -> "other"
            }
            val confidence = when {
                externalId != null -> 0.98f
                merchant.isNotBlank() -> 0.88f
                else -> 0.72f
            }
            // 官方账单里成功的消费记录已经足够确定；商户缺失只影响分类，不应阻塞流水。
            val needsConfirmation = status == TransactionStatus.PENDING
            val raw = row.joinToString(",")
            bills += ParsedBill(
                amountCents = amount,
                type = type,
                occurredAt = occurredAt,
                merchant = merchant,
                categoryId = category,
                accountId = account,
                note = note,
                rawText = raw,
                confidence = confidence,
                fingerprint = BillFingerprint.of(type, amount, occurredAt, merchant, raw),
                externalId = externalId,
                status = status,
                needsConfirmation = needsConfirmation,
            )
        }
        if (skipped > 0 && warnings.isEmpty()) {
            warnings += "已跳过 $skipped 行（转账、充值、还款、失败/关闭等非消费记录）"
        }
        if (bills.isEmpty()) {
            throw BillImportException(
                if (skipped > 0) "没有可导入的消费记录，已跳过 $skipped 行（转账/失败/不计收支）"
                else "没有读到可导入的消费记录",
            )
        }
        return BillImportResult(
            bills = bills.distinctBy { it.fingerprint }.sortedByDescending { it.occurredAt },
            skipped = skipped,
            warnings = warnings,
            source = source,
        )
    }

    private fun detectSource(header: List<String>, metadata: String, fileName: String): String {
        val joined = (header + metadata + fileName).joinToString(" ")
        return when {
            joined.contains("支付宝") || header.any { it == "对方账号" || it == "商品说明" || it == "收/付款方式" } -> "支付宝"
            joined.contains("微信") || header.any { it == "交易类型" || it == "支付方式" || it == "商户单号" } -> "微信"
            else -> "支付宝/微信账单"
        }
    }

    private fun find(header: List<String>, vararg aliases: String): Int {
        aliases.forEach { alias ->
            val index = header.indexOf(alias.lowercase(Locale.ROOT))
            if (index >= 0) return index
        }
        return -1
    }

    private fun normalize(value: String): String =
        value.trim().removePrefix("\uFEFF").lowercase(Locale.ROOT)

    private fun parseCents(raw: String): Long? {
        val clean = raw
            .replace("¥", "")
            .replace("￥", "")
            .replace(",", "")
            .replace("元", "")
            .replace("+", "")
            .trim()
        if (clean.isBlank() || clean == "-") return null
        val value = clean.toDoubleOrNull() ?: return null
        if (value == 0.0 || value >= 1_000_000.0) return null
        return Math.round(Math.abs(value) * 100).takeIf { it > 0 }
    }

    private fun parseDateTime(raw: String, zone: ZoneId): LocalDateTime? {
        val clean = raw.trim().removeSurrounding("\"")
        if (clean.isBlank()) return null
        for (format in dateTimeFormats) {
            runCatching { LocalDateTime.parse(clean, format) }.getOrNull()?.let { return it }
        }
        // Excel 数字日期：1900 日期系统，基准 1899-12-30。
        val serial = clean.toDoubleOrNull()
        if (serial != null && serial > 20_000.0 && serial < 80_000.0) {
            val seconds = Math.round(serial * 86_400.0)
            return LocalDateTime.of(1899, 12, 30, 0, 0).plusSeconds(seconds)
        }
        return runCatching {
            java.time.Instant.parse(clean).atZone(zone).toLocalDateTime()
        }.getOrNull()
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        }
        strictDecode(bytes, StandardCharsets.UTF_8)?.let { return it }
        val gb = runCatching { Charset.forName("GB18030") }.getOrNull()
        if (gb != null) strictDecode(bytes, gb)?.let { return it }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun strictDecode(bytes: ByteArray, charset: Charset): String? = runCatching {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()

    /** 支持引号、逗号、换行的 CSV 解析。 */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (quoted) {
                if (char == '"') {
                    if (index + 1 < text.length && text[index + 1] == '"') {
                        cell.append('"')
                        index++
                    } else {
                        quoted = false
                    }
                } else {
                    cell.append(char)
                }
            } else {
                when (char) {
                    '"' -> quoted = true
                    ',' -> {
                        row += cell.toString()
                        cell.clear()
                    }
                    '\n' -> {
                        row += cell.toString()
                        cell.clear()
                        rows += row.toList()
                        row.clear()
                    }
                    '\r' -> Unit
                    else -> cell.append(char)
                }
            }
            index++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row.toList()
        }
        return rows
    }

    private fun String.containsAny(values: List<String>): Boolean = values.any(::contains)

    private val EXCLUDED_CATEGORIES = listOf("转账", "充值", "提现", "还款", "理财", "信贷", "借款", "信用卡")
    private val EXCLUDED_PRODUCTS = listOf("转账", "充值", "提现", "还款", "信用卡还款")
    private val FAILED_STATUSES = listOf("失败", "关闭", "已关闭", "等待付款", "未支付", "撤销", "取消")
    private val SUCCESS_STATUSES = listOf("支付成功", "交易成功", "已支出", "已收入", "收款成功", "已收款", "交易完成")
}
