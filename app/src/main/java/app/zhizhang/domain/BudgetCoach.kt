package app.zhizhang.domain

import app.zhizhang.data.Formats

enum class BudgetTone {
    CALM,
    WATCH,
    ALERT,
    BIG,
}

data class BudgetPulse(
    val title: String,
    val message: String,
    val tone: BudgetTone,
)

/**
 * 记账时的本地预算教练。只做提醒，不阻止用户保存。
 * 文案刻意避免指责，目标是促成一次「要不要现在买」的思考。
 */
object BudgetCoach {

    fun evaluate(
        amountCents: Long,
        monthExpenseCents: Long,
        budgetCents: Long,
    ): BudgetPulse? {
        if (amountCents <= 0L || budgetCents <= 0L) return null
        val projected = monthExpenseCents + amountCents
        val remaining = budgetCents - projected
        return when {
            remaining < 0L -> BudgetPulse(
                title = "这笔会超出预算",
                message = "会超 ${Formats.money(-remaining)}。先停三秒。",
                tone = BudgetTone.ALERT,
            )

            projected >= budgetCents * 0.9 ->
                BudgetPulse(
                    title = "预算快到线了",
                    message = "之后只剩 ${Formats.money(remaining)}，慢一点。",
                    tone = BudgetTone.WATCH,
                )

            amountCents >= 100_000L -> BudgetPulse(
                title = "这是一笔大额支出",
                message = "先放 24 小时，再决定。",
                tone = BudgetTone.BIG,
            )

            // 普通小额、预算充足时不打扰。
            else -> null
        }
    }
}
