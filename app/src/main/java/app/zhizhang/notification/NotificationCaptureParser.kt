package app.zhizhang.notification

import app.zhizhang.data.Categories
import app.zhizhang.data.TxType
import app.zhizhang.data.TransactionStatus
import app.zhizhang.domain.BillParser
import app.zhizhang.domain.CategoryClassifier
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

data class NotificationSnapshot(
    val packageName: String,
    val appLabel: String,
    val notificationKey: String,
    val title: String = "",
    val text: String = "",
    val bigText: String = "",
    val subText: String = "",
    val summaryText: String = "",
    val infoText: String = "",
    val textLines: List<String> = emptyList(),
    val tickerText: String = "",
    val postedAt: Long = System.currentTimeMillis(),
) {
    val combined: String
        get() = (listOf(title, text, bigText, subText, summaryText, infoText, tickerText) +
            textLines)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
}

data class NotificationDraft(
    val amountCents: Long,
    val type: TxType,
    val merchant: String,
    val categoryId: String,
    val accountId: String,
    val occurredAt: Long,
    val sourcePackage: String,
    val externalId: String?,
    val status: TransactionStatus,
    val confidence: Float,
    val needsConfirmation: Boolean,
    val rawText: String,
)

data class NotificationParse(
    val draft: NotificationDraft?,
    val reason: String,
)

/**
 * 只依赖通知文本和包名，可独立单测。
 * 这里不放宽到"有金额就记"：状态不明确会标待确认，失败/退款交给仓库层关联原交易。
 */
class NotificationCaptureParser(
    private val classifier: CategoryClassifier,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /** 入库前的廉价过滤：明显不是支付/退款/收款的通知直接丢弃，不落通知事件表。 */
    fun isPotentiallyRelevant(
        snapshot: NotificationSnapshot,
        allowAllApps: Boolean,
    ): Boolean {
        val packageName = snapshot.packageName
        if (packageName in IGNORED_PACKAGES) return false
        if (packageName !in KNOWN_PACKAGES && !allowAllApps) return false
        val text = snapshot.combined
        if (text.length < 6) return false
        if (NOISE.any(text::contains)) return false
        if (allowAllApps && packageName !in KNOWN_PACKAGES) {
            return STRONG_MONEY_WORDS.any(text::contains)
        }
        return PAYMENT_WORDS.any(text::contains) ||
            STRONG_MONEY_WORDS.any(text::contains)
    }

    fun parse(
        snapshot: NotificationSnapshot,
        allowAllApps: Boolean,
        now: LocalDateTime = LocalDateTime.now(zone),
    ): NotificationParse {
        val packageName = snapshot.packageName
        if (packageName in IGNORED_PACKAGES) return NotificationParse(null, "ignored package")
        if (snapshot.combined.length < 6) return NotificationParse(null, "empty notification")
        if (NOISE.any(snapshot.combined::contains)) return NotificationParse(null, "chat/ads")

        val source = sourceFor(packageName) ?: if (allowAllApps) Source.GENERIC else null
            ?: return NotificationParse(null, "source not enabled")
        val text = snapshot.combined
        val status = detectStatus(text)
        val looksLikeMoney = MONEY_WORDS.any(text::contains) || status != TransactionStatus.UNKNOWN
        if (!looksLikeMoney) return NotificationParse(null, "not payment-like")
        if (!trusted(source, text, status)) return NotificationParse(null, "not a trusted template")

        val amount = extractAmount(text) ?: return NotificationParse(null, "amount missing")
        val externalId = extractExternalId(text)
        val merchant = extractMerchant(text)
        val refund = status == TransactionStatus.REFUNDED
        val income = refund || INCOME_WORDS.any(text::contains)
        val type = if (income) TxType.INCOME else TxType.EXPENSE
        val account = when (source) {
            Source.WECHAT -> "wechat"
            Source.ALIPAY -> "alipay"
            else -> "other"
        }
        val categoryId = when {
            refund -> "refund"
            income -> "other_income"
            merchant.isBlank() -> "other_expense"
            else -> classifier.classify("$merchant $text", TxType.EXPENSE)
        }
        val occurredAt = if (snapshot.postedAt > 0L) snapshot.postedAt else {
            now.atZone(zone).toInstant().toEpochMilli()
        }
        var confidence = 0.5f
        if (amount > 0) confidence += 0.2f
        if (merchant.isNotBlank()) confidence += 0.1f
        if (externalId != null) confidence += 0.1f
        if (status == TransactionStatus.SUCCESS) confidence += 0.05f
        if (source != Source.GENERIC) confidence += 0.05f
        if (merchant.isBlank()) confidence -= 0.1f
        confidence = confidence.coerceIn(0.3f, 0.98f)

        val needsConfirmation = status != TransactionStatus.SUCCESS ||
            merchant.isBlank() ||
            source == Source.GENERIC ||
            confidence < 0.72f

        return NotificationParse(
            draft = NotificationDraft(
                amountCents = amount,
                type = type,
                merchant = merchant,
                categoryId = categoryId,
                accountId = account,
                occurredAt = occurredAt,
                sourcePackage = packageName,
                externalId = externalId,
                status = status,
                confidence = confidence,
                needsConfirmation = needsConfirmation,
                rawText = text,
            ),
            reason = when {
                needsConfirmation -> "needs confirmation"
                else -> "ok"
            },
        )
    }

    private fun sourceFor(packageName: String): Source? = when (packageName) {
        in WECHAT_PACKAGES -> Source.WECHAT
        in ALIPAY_PACKAGES -> Source.ALIPAY
        in SHOPPING_PACKAGES -> Source.SHOPPING
        in DELIVERY_PACKAGES -> Source.DELIVERY
        else -> null
    }

    private fun trusted(source: Source, text: String, status: TransactionStatus): Boolean {
        val hasPayment = PAYMENT_WORDS.any(text::contains) || status != TransactionStatus.UNKNOWN
        return when (source) {
            Source.WECHAT -> hasPayment &&
                (text.contains("微信支付") || text.contains("付款") || text.contains("支付") ||
                    text.contains("退款") || text.contains("收款"))
            Source.ALIPAY -> hasPayment &&
                (text.contains("支付宝") || text.contains("交易") || text.contains("付款") ||
                    text.contains("支付") || text.contains("退款") || text.contains("收款"))
            Source.SHOPPING, Source.DELIVERY -> hasPayment &&
                (text.contains("支付") || text.contains("付款") || text.contains("订单") ||
                    text.contains("退款"))
            Source.GENERIC -> hasPayment &&
                (text.contains("支付成功") || text.contains("付款成功") ||
                    text.contains("交易成功") || text.contains("扣款成功"))
        }
    }

    private fun detectStatus(text: String): TransactionStatus {
        if (FAILURE_WORDS.any(text::contains)) {
            return if (text.contains("撤销") || text.contains("取消")) {
                TransactionStatus.CANCELED
            } else {
                TransactionStatus.FAILED
            }
        }
        if (REFUND_WORDS.any(text::contains)) return TransactionStatus.REFUNDED
        if (SUCCESS_WORDS.any(text::contains)) return TransactionStatus.SUCCESS
        return TransactionStatus.UNKNOWN
    }

    private fun extractAmount(text: String): Long? {
        for (match in AMOUNT_REGEX.findAll(text)) {
            val before = text.substring(maxOf(0, match.range.first - 12), match.range.first)
            if (BALANCE_WORDS.any(before::contains)) continue
            val number = match.groups[1]?.value ?: match.groups[2]?.value ?: continue
            val value = number.replace(",", "").toDoubleOrNull() ?: continue
            if (value <= 0.0 || value >= 1_000_000.0) continue
            return Math.round(value * 100)
        }
        return null
    }

    private fun extractExternalId(text: String): String? {
        val match = EXTERNAL_ID_REGEX.find(text) ?: return null
        return match.groups[1]?.value?.takeIf { it.length >= 6 }
    }

    private fun extractMerchant(text: String): String {
        MERCHANT_REGEX.find(text)?.groups?.get(1)?.value?.let {
            return it.trim().trim('，', ',', '。', ';', '；').take(40)
        }
        return ""
    }

    companion object {
        val WECHAT_PACKAGES = setOf("com.tencent.mm")
        val ALIPAY_PACKAGES = setOf("com.eg.android.AlipayGphone")
        val SHOPPING_PACKAGES = setOf(
            "com.taobao.taobao",
            "com.jingdong.app.mall",
            "com.xunmeng.pinduoduo",
            "com.ss.android.ugc.aweme",
            "com.xingin.xhs",
        )
        val DELIVERY_PACKAGES = setOf(
            "com.sankuai.meituan",
            "com.sankuai.meituan.takeoutnew",
            "me.ele",
        )
        val IGNORED_PACKAGES = setOf("app.zhizhang")
        val KNOWN_PACKAGES: Set<String> =
            WECHAT_PACKAGES + ALIPAY_PACKAGES + SHOPPING_PACKAGES + DELIVERY_PACKAGES
        val NOISE = listOf("条新消息", "群聊", "有人@你", "[图片]", "[语音]", "[视频]", "营销", "优惠券")
        val MONEY_WORDS = listOf("支付", "付款", "消费", "扣款", "转账", "退款", "收款", "到账", "元", "¥", "￥")
        val STRONG_MONEY_WORDS = listOf(
            "支付成功", "付款成功", "交易成功", "已支付", "扣款成功", "支付完成",
            "退款成功", "已退款", "退款到账", "收款到账", "收款成功",
            "订单支付", "支付失败", "付款失败",
        )
        val PAYMENT_WORDS = listOf(
            "支付成功", "付款成功", "交易成功", "已支付", "消费", "扣款", "转账",
            "退款", "收款到账", "收款成功", "到账",
        )
        val SUCCESS_WORDS = listOf(
            "支付成功", "付款成功", "交易成功", "已支付", "扣款成功", "支付完成",
            "收款到账", "收款成功",
        )
        val FAILURE_WORDS = listOf("支付失败", "付款失败", "交易失败", "扣款失败", "未支付")
        val REFUND_WORDS = listOf("退款成功", "已退款", "退款到账", "退款金额")
        val INCOME_WORDS = listOf("收款到账", "收款成功", "收到转账")
        val BALANCE_WORDS = listOf("余额", "可用", "额度", "账单", "剩余")
        val AMOUNT_REGEX = Regex("""(?:¥|￥)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)|([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*元""")
        val EXTERNAL_ID_REGEX = Regex(
            """(?:交易单号|交易号|订单号|商户单号|支付订单号|流水号)[:：\s]*([A-Za-z0-9-]{6,})""",
            RegexOption.IGNORE_CASE,
        )
        val MERCHANT_REGEX = Regex(
            """(?:商户|商家|收款方|付款给|收款人)[:：\s]+([^，,。;；\s]{2,40})""",
        )
    }

    private enum class Source { WECHAT, ALIPAY, SHOPPING, DELIVERY, GENERIC }
}


/** 把通知 extras 转成可测试的快照；不包含任何网络或数据库逻辑。 */
internal fun buildNotificationSnapshot(
    packageName: String,
    appLabel: String,
    notificationKey: String,
    postTime: Long,
    notification: android.app.Notification,
): NotificationSnapshot? {
    val extras = notification.extras ?: android.os.Bundle.EMPTY
    val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
    val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
    val bigText = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
    val subText = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
    val summaryText = extras.getCharSequence(android.app.Notification.EXTRA_SUMMARY_TEXT)?.toString().orEmpty()
    val infoText = extras.getCharSequence(android.app.Notification.EXTRA_INFO_TEXT)?.toString().orEmpty()
    val ticker = notification.tickerText?.toString().orEmpty()
    val lines = extras.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)
        ?.mapNotNull { it?.toString() }
        .orEmpty()
    val messages = extras.getParcelableArray(android.app.Notification.EXTRA_MESSAGES)
        ?.mapNotNull { item ->
            (item as? android.os.Bundle)?.getCharSequence("text")?.toString()
        }
        .orEmpty()
    if (title.isBlank() && text.isBlank() && bigText.isBlank() && subText.isBlank() &&
        summaryText.isBlank() && infoText.isBlank() && ticker.isBlank() &&
        lines.isEmpty() && messages.isEmpty()
    ) {
        return null
    }
    return NotificationSnapshot(
        packageName = packageName,
        appLabel = appLabel,
        notificationKey = notificationKey,
        title = title,
        text = text,
        bigText = bigText,
        subText = subText,
        summaryText = summaryText,
        infoText = infoText,
        textLines = lines + messages,
        tickerText = ticker,
        postedAt = if (postTime > 0L) postTime else System.currentTimeMillis(),
    )
}
