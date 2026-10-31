package app.zhizhang.domain

/** AI 有时会口头说“记下了”，却没有返回 transactions；这里阻止这类假确认。 */
object ChatReplyGuard {
    private val recordedClaims = listOf(
        "记下了", "记好了", "记好啦", "已记下", "已记录", "已记账",
        "已入账", "记进去了", "加进去了", "帮你记", "记上了一笔",
    )
    private val negations = listOf("没有", "没记", "未记", "无法", "不能", "并未", "还没")

    private val confirmationHints = listOf(
        "请确认", "确认一下", "核对", "看看对不对", "整理好了", "账单如下", "这些账",
        "下面这些", "需要你确认", "待确认",
    )
    private val billActions = listOf(
        "花", "买", "吃", "喝", "付", "充值", "打车", "外卖", "工资", "收入",
        "报销", "消费", "转账", "缴费", "房租", "地铁", "公交", "订", "续费",
    )
    private val nonBillHints = listOf(
        "多少", "怎么", "为什么", "建议", "要不要", "预算", "余额", "统计",
        "查一下", "还能", "值不值", "该不该", "订阅", "资产", "岁", "几点",
        "日期", "星期", "天气",
    )

    fun mentionsConfirmation(reply: String): Boolean =
        confirmationHints.any { reply.contains(it) }

    /** AI 漏掉 transactions 时，用一个保守的本地兜底判断，避免账单卡片直接消失。 */
    fun looksLikeBillInput(text: String): Boolean {
        val value = text.trim()
        if (value.isBlank() || value.length > 240) return false
        if (nonBillHints.any { value.contains(it) }) return false
        val hasAmount = Regex("""(?<!\d)\d+(?:\.\d{1,2})?(?!\d)""").containsMatchIn(value)
        // 有动作词最稳；没有动作词的短句（如“星巴克 38”）也允许本地兜底。
        return hasAmount && (billActions.any { value.contains(it) } || value.length <= 80)
    }

    fun claimsRecorded(reply: String): Boolean {
        val text = reply.trim()
        if (text.isBlank()) return false
        if (negations.any { text.contains(it) }) return false
        return recordedClaims.any { text.contains(it) }
    }

    fun waitingForConfirmation(): String =
        "我整理好了，请先确认下面的账单，确认后才会入账。"

    fun honestReply(hasBills: Boolean): String {
        return if (hasBills) {
            "我刚刚没有整理出可确认的账目，先用本地规则识别了这笔，你确认一下。"
        } else {
            "我这次没有生成可确认的账目，这笔还没有记下。请补一下金额和商户，或者手动记账。"
        }
    }
}
