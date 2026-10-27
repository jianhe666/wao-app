package app.zhizhang.domain

/** AI 有时会口头说“记下了”，却没有返回 transactions；这里阻止这类假确认。 */
object ChatReplyGuard {
    private val recordedClaims = listOf(
        "记下了", "记好了", "记好啦", "已记下", "已记录", "已记账",
        "已入账", "记进去了", "加进去了", "帮你记", "记上了一笔",
    )
    private val negations = listOf("没有", "没记", "未记", "无法", "不能", "并未", "还没")

    fun claimsRecorded(reply: String): Boolean {
        val text = reply.trim()
        if (text.isBlank()) return false
        if (negations.any { text.contains(it) }) return false
        return recordedClaims.any { text.contains(it) }
    }

    fun honestReply(hasBills: Boolean): String {
        return if (hasBills) {
            "我刚刚没有整理出可确认的账目，先用本地规则识别了这笔，你确认一下。"
        } else {
            "我这次没有生成可确认的账目，这笔还没有记下。请补一下金额和商户，或者手动记账。"
        }
    }
}
