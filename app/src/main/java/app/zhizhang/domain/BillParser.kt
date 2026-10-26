package app.zhizhang.domain

import app.zhizhang.data.Accounts
import app.zhizhang.data.TransactionStatus
import app.zhizhang.data.TxType
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ParsedBill(
    val amountCents: Long,
    val type: TxType,
    val occurredAt: Long,
    val merchant: String,
    val categoryId: String,
    val accountId: String,
    val note: String,
    val rawText: String,
    val confidence: Float,
    val fingerprint: String,
    val externalId: String? = null,
    val status: TransactionStatus = TransactionStatus.SUCCESS,
    val needsConfirmation: Boolean = false,
)

/**
 * 账单解析器：支持支付宝/微信通知、银行短信、以及「日期 + 商家 + 金额」的普通文本。
 * 完全离线，不依赖任何网络或第三方服务。
 */
class BillParser(
    private val classifier: CategoryClassifier = CategoryClassifier(),
) {

    fun parse(
        input: String,
        now: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES),
        zone: ZoneId = ZoneId.systemDefault(),
        defaultAccountId: String = "wechat",
    ): List<ParsedBill> {
        val normalized = normalizeInput(input)
        if (normalized.isBlank()) return emptyList()

        return splitRecords(normalized)
            .mapNotNull { parseRecord(it, now, zone, defaultAccountId) }
            .distinctBy { it.fingerprint }
    }

    fun parseFirst(
        input: String,
        now: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES),
        zone: ZoneId = ZoneId.systemDefault(),
        defaultAccountId: String = "wechat",
    ): ParsedBill? = parse(input, now, zone, defaultAccountId).firstOrNull()

    private fun parseRecord(
        record: String,
        now: LocalDateTime,
        zone: ZoneId,
        defaultAccountId: String,
    ): ParsedBill? {
        val raw = record.replace(Regex("\\s+"), " ").trim()
        if (raw.isBlank()) return null
        if (FAILURE_WORDS.any(raw::contains)) return null

        val amountHit = findAmount(raw) ?: return null
        val type = detectType(raw, amountHit.raw)
        val dateHit = findDate(raw, now, zone)
        val merchant = extractMerchant(raw)
        val account = Accounts.fromText(raw)?.id ?: defaultAccountId
        val category = classifier.classify("$merchant $raw", type)
        val occurredAt = dateHit.dateTime.atZone(zone).toInstant().toEpochMilli()

        val note = if (merchant.isBlank()) {
            raw.take(52)
        } else {
            ""
        }

        var confidence = 0.5f
        if (dateHit.explicitDay) confidence += 0.2f else if (dateHit.timeFound) confidence += 0.06f
        if (merchant.isNotBlank()) confidence += 0.18f
        if (amountHit.strong) confidence += 0.14f
        if (raw.length < 100) confidence += 0.02f

        return ParsedBill(
            amountCents = amountHit.cents,
            type = type,
            occurredAt = occurredAt,
            merchant = merchant,
            categoryId = category,
            accountId = account,
            note = note,
            rawText = raw,
            confidence = confidence.coerceIn(0.35f, 0.98f),
            fingerprint = BillFingerprint.of(type, amountHit.cents, occurredAt, merchant, raw),
        )
    }

    // ---------------------------------------------------------------- splitting

    private fun splitRecords(text: String): List<String> {
        val paragraphs = text.split(Regex("\\n\\s*\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val records = mutableListOf<String>()
        for (paragraph in paragraphs) {
            val lines = paragraph.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size <= 1) {
                records += splitInline(paragraph)
                continue
            }

            val completeLines = lines.filter { line ->
                hasAmount(line) && (hasDate(line) || hasTransactionWord(line))
            }
            if (completeLines.size >= 2 && lines.count { hasAmount(it) } >= 2) {
                records += completeLines.flatMap(::splitInline)
            } else {
                records += splitInline(paragraph)
            }
        }
        return records.filter { it.isNotBlank() }
    }

    private fun splitInline(text: String): List<String> {
        if (!text.contains('；') && !text.contains(';')) return listOf(text)
        val parts = text.split(Regex("[；;]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return if (parts.count { hasAmount(it) } >= 2) parts else listOf(text)
    }

    private fun hasAmount(text: String): Boolean = QUICK_AMOUNT.containsMatchIn(text)

    private fun hasDate(text: String): Boolean =
        QUICK_DATE_PATTERNS.any { it.containsMatchIn(text) }

    private fun hasTransactionWord(text: String): Boolean =
        TRANSACTION_WORDS.any(text::contains)

    // ------------------------------------------------------------------- amount

    private data class AmountHit(
        val cents: Long,
        val index: Int,
        val priority: Int,
        val strong: Boolean,
        val raw: String,
    )

    private fun findAmount(text: String): AmountHit? {
        val hits = mutableListOf<AmountHit>()
        for (pattern in AMOUNT_PATTERNS) {
            pattern.regex.findAll(text).forEach { match ->
                val group = match.groups[1] ?: return@forEach
                val rawNumber = group.value
                val cents = toCents(rawNumber) ?: return@forEach
                val index = group.range.first
                val context = text.substring(maxOf(0, index - 12), index)
                if (BALANCE_CONTEXT.any(context::contains)) return@forEach
                val after = text.substring(minOf(text.length, group.range.last + 1))
                if (after.trimStart().startsWith("%")) return@forEach
                hits += AmountHit(
                    cents = cents,
                    index = index,
                    priority = pattern.priority,
                    strong = pattern.priority >= 9 || text.substring(
                        maxOf(0, index - 2),
                        index,
                    ).contains('¥'),
                    raw = rawNumber,
                )
            }
        }
        if (hits.isEmpty()) return null
        return hits.maxWithOrNull(
            compareBy<AmountHit> { it.priority }.thenByDescending { it.index },
        )
    }

    private fun toCents(raw: String): Long? {
        val clean = raw.replace(",", "").trim().removePrefix("+").removePrefix("-")
        if (clean.isEmpty()) return null
        val value = clean.toDoubleOrNull() ?: return null
        if (value <= 0 || value >= 1_000_000) return null
        val cents = Math.round(value * 100)
        return cents.takeIf { it > 0 }
    }

    // --------------------------------------------------------------------- date

    private data class DateHit(
        val dateTime: LocalDateTime,
        val explicitDay: Boolean,
        val timeFound: Boolean,
    )

    private fun findDate(text: String, now: LocalDateTime, zone: ZoneId): DateHit {
        val time = findTime(text)

        for (pattern in FULL_DATE_PATTERNS) {
            val match = pattern.find(text) ?: continue
            val groups = match.groupValues
            val year = groups[1].toIntOrNull()
            val month = groups[2].toIntOrNull()
            val day = groups[3].toIntOrNull()
            if (year == null || month == null || day == null) continue
            val parsedTime = groups.getOrNull(4)?.toIntOrNull()?.let { hour ->
                val minute = groups.getOrNull(5)?.toIntOrNull() ?: 0
                val second = groups.getOrNull(6)?.toIntOrNull() ?: 0
                safeTime(hour, minute, second)
            } ?: time ?: DEFAULT_TIME
            val date = makeDate(year, month, day) ?: continue
            return DateHit(date.atTime(parsedTime), explicitDay = true, timeFound = parsedTime != DEFAULT_TIME || time != null)
        }

        for (pattern in EIGHT_DIGIT_DATE_PATTERNS) {
            val match = pattern.find(text) ?: continue
            val year = match.groupValues[1].toIntOrNull() ?: continue
            val month = match.groupValues[2].toIntOrNull() ?: continue
            val day = match.groupValues[3].toIntOrNull() ?: continue
            val date = makeDate(year, month, day) ?: continue
            return DateHit(date.atTime(time ?: DEFAULT_TIME), explicitDay = true, timeFound = time != null)
        }

        for (pattern in MONTH_DAY_PATTERNS) {
            val match = pattern.find(text) ?: continue
            val groups = match.groupValues
            val month = groups[1].toIntOrNull() ?: continue
            val day = groups[2].toIntOrNull() ?: continue
            var date = makeDate(now.year, month, day) ?: continue
            if (date.isAfter(now.toLocalDate().plusDays(1))) {
                date = makeDate(now.year - 1, month, day) ?: continue
            }
            val parsedTime = groups.getOrNull(3)?.toIntOrNull()?.let { hour ->
                safeTime(hour, groups.getOrNull(4)?.toIntOrNull() ?: 0)
            } ?: time ?: DEFAULT_TIME
            return DateHit(date.atTime(parsedTime), explicitDay = true, timeFound = time != null || groups.getOrNull(3) != null)
        }

        for ((word, daysAgo) in RELATIVE_DAYS) {
            if (!text.contains(word)) continue
            val date = now.toLocalDate().minusDays(daysAgo)
            val timeAfterWord = Regex(
                """${Regex.escape(word)}\s*(\d{1,2}):(\d{2})""",
            ).find(text)?.let {
                safeTime(
                    it.groupValues[1].toIntOrNull() ?: 0,
                    it.groupValues[2].toIntOrNull() ?: 0,
                )
            }
            return DateHit(date.atTime(timeAfterWord ?: time ?: DEFAULT_TIME), explicitDay = true, timeFound = true)
        }

        val parsedTime = time ?: now.toLocalTime()
        return DateHit(
            now.toLocalDate().atTime(parsedTime),
            explicitDay = false,
            timeFound = time != null,
        )
    }

    private fun findTime(text: String): LocalTime? {
        val match = TIME_PATTERN.find(text) ?: return null
        return safeTime(
            match.groupValues[1].toIntOrNull() ?: return null,
            match.groupValues[2].toIntOrNull() ?: return null,
            match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0,
        )
    }

    private fun safeTime(hour: Int, minute: Int, second: Int = 0): LocalTime? =
        try {
            LocalTime.of(hour, minute, second)
        } catch (_: DateTimeException) {
            null
        }

    private fun makeDate(year: Int, month: Int, day: Int): LocalDate? =
        try {
            if (year !in 2000..2100) null else LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }

    // ----------------------------------------------------------------- merchant

    private fun extractMerchant(text: String): String {
        for (pattern in MERCHANT_PATTERNS) {
            val match = pattern.find(text) ?: continue
            val value = cleanMerchant(match.groupValues[1])
            if (value.isNotBlank()) return value
        }

        val bracket = Regex("""【([^】]{2,16})】""").find(text)
        if (bracket != null) {
            val value = cleanMerchant(bracket.groupValues[1])
            if (value.isNotBlank() && APP_NAMES.none(value::contains)) return value
        }

        return deriveMerchant(text)
    }

    private fun cleanMerchant(value: String): String {
        var cleaned = value
            .replace(Regex("""[（(].*?[)）]"""), " ")
            .replace(Regex("""[，,。.;；:：/\\|]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        cleaned = cleaned.substringBefore(' ').trim()
        if (cleaned.length > 24) cleaned = cleaned.take(24)
        if (cleaned.length < 2) return ""
        if (cleaned.all { it.isDigit() || it == '*' }) return ""
        if (NOISE_MERCHANT.any(cleaned::contains)) return ""
        return cleaned
    }

    private fun deriveMerchant(text: String): String {
        var cleaned = text
        FULL_DATE_PATTERNS.forEach { cleaned = it.replace(cleaned, " ") }
        EIGHT_DIGIT_DATE_PATTERNS.forEach { cleaned = it.replace(cleaned, " ") }
        MONTH_DAY_PATTERNS.forEach { cleaned = it.replace(cleaned, " ") }
        RELATIVE_DAYS.keys.forEach { cleaned = cleaned.replace(it, " ") }
        cleaned = TIME_PATTERN.replace(cleaned, " ")
        AMOUNT_PATTERNS.forEach { cleaned = it.regex.replace(cleaned, " ") }
        cleaned = cleaned
            .replace(Regex("""[¥￥]"""), " ")
            .replace(Regex("""(?:^|\s)(?:元|块)(?=\s|$)"""), " ")
            .replace(Regex("""\d[\d,.]*\s*(?:元|块)?"""), " ")
            .replace(Regex("""[（(].*?[)）]"""), " ")
            .replace(Regex("""[【】\[\]<>《》]"""), " ")
            .replace(Regex("""[，,。.;；:：/\\|*]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        for (word in BOILERPLATE.sortedByDescending { it.length }) {
            cleaned = cleaned.replace(word, " ")
        }
        cleaned = cleaned
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('-', '·', '_')

        if (cleaned.length < 2 || cleaned.length > 20) return ""
        if (cleaned.none { it.isLetter() }) return ""
        if (NOISE_MERCHANT.any(cleaned::contains)) return ""
        return cleaned
    }

    // --------------------------------------------------------------------- type

    private fun detectType(text: String, amountRaw: String): TxType {
        if (INCOME_STRONG.any(text::contains)) return TxType.INCOME
        if (EXPENSE_STRONG.any(text::contains)) return TxType.EXPENSE
        if (amountRaw.trimStart().startsWith("-")) return TxType.EXPENSE
        if (amountRaw.trimStart().startsWith("+")) return TxType.INCOME
        if (INCOME_WEAK.any(text::contains)) return TxType.INCOME
        return TxType.EXPENSE
    }

    // ------------------------------------------------------------------- input

    private fun normalizeInput(input: String): String {
        if (input.isBlank()) return ""
        val builder = StringBuilder(input.length)
        for (char in input) {
            val mapped = when (char) {
                in '０'..'９' -> '0' + (char - '０')
                '．' -> '.'
                '￥' -> '¥'
                '：' -> ':'
                '，' -> ','
                '－', '—', '–' -> '-'
                '＋' -> '+'
                '\u00A0', '\u3000' -> ' '
                '\r' -> '\n'
                else -> char
            }
            builder.append(mapped)
        }
        return builder.toString()
            .replace(Regex("""[ \t]+"""), " ")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    private data class AmountPattern(
        val priority: Int,
        val regex: Regex,
    )

    private companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(12, 0)

        val QUICK_AMOUNT = Regex(
            """(?:[¥￥]\s*\d|\d[\d,]*(?:\.\d{1,2})?\s*(?:元|块)|(?<![\d:.])\d{1,7}(?:\.\d{1,2})?\s*$)""",
        )
        val QUICK_DATE_PATTERNS = listOf(
            Regex("""20\d{2}\s*[-/年.]\s*\d{1,2}\s*[-/月.]\s*\d{1,2}"""),
            Regex("""\d{1,2}\s*月\s*\d{1,2}\s*日"""),
            Regex("""\d{1,2}[-/]\d{1,2}(?!\d)"""),
            Regex("""今天|今日|昨天|昨日|前天"""),
        )

        val TRANSACTION_WORDS = listOf(
            "支出", "收入", "付款", "支付", "消费", "扣款", "转账", "收款", "退款",
            "入账", "到账", "实付",
        )

        val AMOUNT_PATTERNS = listOf(
            AmountPattern(
                11,
                Regex(
                    """(?:实付|实际支付|实收款|成交价)\s*[:：]?\s*(?:人民币|RMB|CNY)?\s*[¥￥]?\s*(-?\d[\d,]*(?:\.\d{1,2})?)\s*(?:元|块)?""",
                    RegexOption.IGNORE_CASE,
                ),
            ),
            AmountPattern(
                10,
                Regex(
                    """(?:付款金额|支付金额|交易金额|消费金额|支出金额|收入金额|转账金额|退款金额|扣款金额|金额)\s*[:：]?\s*(?:人民币|RMB|CNY)?\s*[¥￥]?\s*(-?\d[\d,]*(?:\.\d{1,2})?)\s*(?:元|块)?""",
                    RegexOption.IGNORE_CASE,
                ),
            ),
            AmountPattern(
                9,
                Regex(
                    """(?:支出|收入|付款|消费|扣款|转账|收款|退款|入账|到账)\s*(?:人民币|RMB|CNY)?\s*[¥￥]?\s*(-?\d[\d,]*(?:\.\d{1,2})?)\s*(?:元|块)?""",
                    RegexOption.IGNORE_CASE,
                ),
            ),
            AmountPattern(
                8,
                Regex("""(?<![\d.])([+-]\d[\d,]*(?:\.\d{1,2})?)"""),
            ),
            AmountPattern(
                6,
                Regex("""[¥￥]\s*(-?\d[\d,]*(?:\.\d{1,2})?)"""),
            ),
            AmountPattern(
                5,
                Regex("""(-?\d[\d,]*(?:\.\d{1,2})?)\s*(?:元|块)"""),
            ),
            AmountPattern(
                3,
                Regex("""(?:^|\s)(\d{1,7}(?:\.\d{1,2})?)\s*$"""),
            ),
        )

        val BALANCE_CONTEXT = listOf(
            "余额", "结余", "可用", "额度", "积分", "优惠", "立减", "减免", "折扣",
            "已省", "红包抵扣", "待还", "欠款", "账单金额为",
        )

        val INCOME_STRONG = listOf(
            "退款成功", "已退款", "退款到账", "退货退款", "退款", "退货", "收款到账",
            "收款成功", "已收款", "收入", "入账", "到账", "转入", "工资", "奖金",
            "利息", "分红", "报销", "收到一笔",
        )

        val EXPENSE_STRONG = listOf(
            "支出", "消费", "付款成功", "支付成功", "已支付", "扣款", "转出",
            "交易成功", "还款", "缴费", "付款给",
        )

        val INCOME_WEAK = listOf("返现", "返还", "退回", "收到", "收款")

        val FAILURE_WORDS = listOf(
            "支付失败", "付款失败", "交易失败", "扣款失败", "退款失败", "交易关闭",
            "已取消", "未支付", "待支付", "待付款", "支付超时",
        )

        val FULL_DATE_PATTERNS = listOf(
            Regex(
                """(?<!\d)(20\d{2})\s*[-/年.]\s*(\d{1,2})\s*[-/月.]\s*(\d{1,2})\s*日?(?:[ T]*(\d{1,2})[:：](\d{2})(?::(\d{2}))?)?""",
            ),
        )

        val EIGHT_DIGIT_DATE_PATTERNS = listOf(
            Regex("""(?<!\d)(20\d{2})(\d{2})(\d{2})(?!\d)"""),
        )

        val MONTH_DAY_PATTERNS = listOf(
            Regex(
                """(?<!\d)(\d{1,2})\s*月\s*(\d{1,2})\s*日(?:\s*(\d{1,2}):(\d{2})(?::(\d{2}))?)?""",
            ),
            Regex(
                """(?<!\d)(\d{1,2})[-/](\d{1,2})(?!\d)(?:\s+(\d{1,2}):(\d{2}))?""",
            ),
        )

        val TIME_PATTERN = Regex("""(?<!\d)(\d{1,2}):(\d{2})(?::(\d{2}))?(?!\d)""")

        val RELATIVE_DAYS = linkedMapOf(
            "今天" to 0L,
            "今日" to 0L,
            "昨天" to 1L,
            "昨日" to 1L,
            "前天" to 2L,
        )

        val MERCHANT_PATTERNS = listOf(
            Regex(
                """(?:商户名称|商户|商家名称|商家|收款方名称|收款方|收款人|交易对方|对方名称|对方|付款给|转账给)\s*[:：]?\s*([^\n，,。;；]{2,30})""",
            ),
            Regex("""向\s*([^\s，,。;；]{2,24})\s*(?:付款|转账)"""),
            Regex("""在\s*([^，,。;；]{2,24})\s*(?:消费|付款)"""),
        )

        val APP_NAMES = listOf("支付宝", "微信", "云闪付", "银联", "银行")

        val NOISE_MERCHANT = listOf(
            "银行", "账户", "卡号", "尾号", "余额", "可用额度", "验证码", "通知",
            "提醒", "客户", "人民币", "支付成功", "付款成功", "交易成功",
        )

        val BOILERPLATE = listOf(
            "支付宝", "微信支付", "微信", "云闪付", "招商银行", "工商银行", "建设银行",
            "农业银行", "中国银行", "交通银行", "邮储银行", "中信银行", "浦发银行",
            "民生银行", "兴业银行", "光大银行", "平安银行", "广发银行", "华夏银行",
            "付款成功", "支付成功", "交易成功", "扣款成功", "消费", "支出", "收入",
            "到账", "入账", "支付", "付款", "收款", "转账", "扣款", "退款", "还款",
            "缴费", "人民币", "余额", "可用", "账户", "尾号", "储蓄卡", "信用卡",
            "银行卡", "本次", "交易", "商户", "订单号", "订单", "验证码", "通知",
            "提醒", "短信", "尊敬的", "客户", "您", "于", "通过", "成功", "失败",
            "付款给", "收款方", "实付", "金额", "共计", "合计", "元整",
        )
    }
}
