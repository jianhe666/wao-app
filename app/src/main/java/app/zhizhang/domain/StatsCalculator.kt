package app.zhizhang.domain

import app.zhizhang.data.Categories
import app.zhizhang.data.Category
import app.zhizhang.data.Formats
import app.zhizhang.data.TxType
import app.zhizhang.data.TransactionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

data class CategorySlice(
    val category: Category,
    val amountCents: Long,
    val ratio: Float,
)

data class MerchantTotal(
    val merchant: String,
    val count: Int,
    val amountCents: Long,
)

data class AccountSlice(
    val account: app.zhizhang.data.Account,
    val amountCents: Long,
    val ratio: Float,
    val color: Long,
)

enum class InsightTone {
    GOOD,
    NEUTRAL,
    WARN,
}

data class Insight(
    val title: String,
    val body: String,
    val tone: InsightTone,
)

data class MonthStats(
    val month: YearMonth,
    val expenseCents: Long = 0L,
    val incomeCents: Long = 0L,
    val balanceCents: Long = 0L,
    val slices: List<CategorySlice> = emptyList(),
    val accountSlices: List<AccountSlice> = emptyList(),
    val dailyExpenses: List<Long> = emptyList(),
    val topMerchants: List<MerchantTotal> = emptyList(),
    val largestExpense: TransactionEntity? = null,
    val transactionCount: Int = 0,
    val dailyAverageCents: Long = 0L,
    val previousExpenseCents: Long = 0L,
    val budgetCents: Long = 0L,
    val insights: List<Insight> = emptyList(),
)

object StatsCalculator {

    fun build(
        month: YearMonth,
        transactions: List<TransactionEntity>,
        previousExpenseCents: Long,
        budgetCents: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): MonthStats {
        val expenses = transactions.filter { it.txType == TxType.EXPENSE }
        val incomes = transactions.filter { it.txType == TxType.INCOME }
        val totalExpense = expenses.sumOf { it.amountCents }
        val totalIncome = incomes.sumOf { it.amountCents }

        val slices = expenses
            .groupBy { it.categoryId }
            .map { (categoryId, list) ->
                val amount = list.sumOf { it.amountCents }
                CategorySlice(
                    category = Categories.byId(categoryId),
                    amountCents = amount,
                    ratio = if (totalExpense == 0L) 0f else amount.toFloat() / totalExpense,
                )
            }
            .sortedByDescending { it.amountCents }

        val accountSlices = expenses
            .groupBy { it.accountId }
            .map { (accountId, list) ->
                val amount = list.sumOf { it.amountCents }
                AccountSlice(
                    account = app.zhizhang.data.Accounts.byId(accountId),
                    amountCents = amount,
                    ratio = if (totalExpense == 0L) 0f else amount.toFloat() / totalExpense,
                    color = accountColor(accountId),
                )
            }
            .sortedByDescending { it.amountCents }

        val daysInMonth = month.lengthOfMonth()
        val days = LongArray(daysInMonth)
        for (expense in expenses) {
            val day = Instant.ofEpochMilli(expense.occurredAt).atZone(zone).dayOfMonth
            if (day in 1..daysInMonth) {
                days[day - 1] += expense.amountCents
            }
        }

        val daysElapsed = when {
            month == YearMonth.from(today) -> today.dayOfMonth
            month.isBefore(YearMonth.from(today)) -> daysInMonth
            else -> 0
        }
        val dailyAverage = if (daysElapsed == 0) 0L else totalExpense / daysElapsed

        val topMerchants = expenses
            .filter { it.merchant.isNotBlank() && !it.isPersonalTransfer() && it.isOfflineStore() }
            .groupBy { MerchantNormalizer.canonical(it.merchant) }
            .map { (merchant, list) ->
                MerchantTotal(
                    merchant = merchant,
                    count = list.size,
                    amountCents = list.sumOf { it.amountCents },
                )
            }
            .sortedWith(
                compareByDescending<MerchantTotal> { it.count }
                    .thenByDescending { it.amountCents },
            )
            .take(4)

        val largest = expenses.maxByOrNull { it.amountCents }
        val insights = buildInsights(
            totalExpense = totalExpense,
            previousExpense = previousExpenseCents,
            slices = slices,
            largest = largest,
            dailyAverage = dailyAverage,
            budgetCents = budgetCents,
        )

        return MonthStats(
            month = month,
            expenseCents = totalExpense,
            incomeCents = totalIncome,
            balanceCents = totalIncome - totalExpense,
            slices = slices,
            accountSlices = accountSlices,
            dailyExpenses = days.toList(),
            topMerchants = topMerchants,
            largestExpense = largest,
            transactionCount = transactions.size,
            dailyAverageCents = dailyAverage,
            previousExpenseCents = previousExpenseCents,
            budgetCents = budgetCents,
            insights = insights,
        )
    }

    private fun accountColor(accountId: String): Long = when (accountId) {
        "wechat" -> 0xFF2A9D8F
        "alipay" -> 0xFF4F76C7
        "bank" -> 0xFFE0A458
        "cash" -> 0xFF7BA05B
        "other" -> 0xFF8A8F9C
        else -> {
            val palette = listOf(0xFFC65D8A, 0xFF3A9BC1, 0xFFA06CD5, 0xFFE76F51)
            palette[Math.floorMod(accountId.hashCode(), palette.size)]
        }
    }

    private fun TransactionEntity.isOfflineStore(): Boolean {
        val category = Categories.byId(categoryId)
        if (category.id !in OFFLINE_CATEGORIES) return false
        val text = "$merchant $note"
        return ONLINE_WORDS.none(text::contains)
    }

    private fun TransactionEntity.isPersonalTransfer(): Boolean {
        val text = "$merchant $note"
        if (PERSONAL_TRANSFER_WORDS.any(text::contains)) return true
        return Categories.byId(categoryId).id in setOf("social", "other_expense")
    }

    private fun buildInsights(
        totalExpense: Long,
        previousExpense: Long,
        slices: List<CategorySlice>,
        largest: TransactionEntity?,
        dailyAverage: Long,
        budgetCents: Long,
    ): List<Insight> {
        val result = mutableListOf<Insight>()
        if (totalExpense == 0L) return emptyList()

        if (budgetCents > 0) {
            if (totalExpense > budgetCents) {
                result += Insight(
                    title = "预算已经超了",
                    body = "超出 ${Formats.money(totalExpense - budgetCents)}，下笔可以缓一缓",
                    tone = InsightTone.WARN,
                )
            } else {
                result += Insight(
                    title = "预算还剩 ${Formats.money(budgetCents - totalExpense)}",
                    body = "已用 ${(totalExpense * 100 / budgetCents)}%，日均可再花 ${
                        Formats.money((budgetCents - totalExpense) / 30)
                    }",
                    tone = if (totalExpense * 100 / budgetCents >= 80) InsightTone.WARN else InsightTone.GOOD,
                )
            }
        }

        if (previousExpense > 0) {
            val ratio = (totalExpense - previousExpense).toFloat() / previousExpense
            val percent = abs(ratio * 100).roundToInt()
            if (percent >= 3) {
                result += if (ratio > 0) {
                    Insight(
                        title = "比上月同期多花 $percent%",
                        body = "上月同期支出 ${Formats.money(previousExpense)}",
                        tone = InsightTone.WARN,
                    )
                } else {
                    Insight(
                        title = "比上月同期省了 $percent%",
                        body = "继续保持，上月支出 ${Formats.money(previousExpense)}",
                        tone = InsightTone.GOOD,
                    )
                }
            } else {
                result += Insight(
                    title = "和上月同期基本持平",
                    body = "上月支出 ${Formats.money(previousExpense)}",
                    tone = InsightTone.NEUTRAL,
                )
            }
        }

        slices.firstOrNull()?.let { top ->
            result += Insight(
                title = "${top.category.name}是最大开销",
                body = "占本月 ${(top.ratio * 100).roundToInt()}% · ${Formats.money(top.amountCents)}",
                tone = InsightTone.NEUTRAL,
            )
        }

        largest?.let { transaction ->
            val name = transaction.merchant.ifBlank { Categories.byId(transaction.categoryId).name }
            result += Insight(
                title = "最大的一笔",
                body = "$name ${Formats.money(transaction.amountCents)}",
                tone = InsightTone.NEUTRAL,
            )
        }

        if (dailyAverage > 0 && result.size < 3) {
            result += Insight(
                title = "日均支出 ${Formats.money(dailyAverage)}",
                body = "按当前节奏，一个月大约花费 ${Formats.money(dailyAverage * 30)}",
                tone = InsightTone.NEUTRAL,
            )
        }

        return result.take(3)
    }

    private val OFFLINE_CATEGORIES = setOf("food", "shopping", "entertainment", "medical")
    private val ONLINE_WORDS = listOf(
        "拼多多", "淘宝", "天猫", "京东", "抖音", "唯品会", "得物", "苏宁", "小红书",
        "美团", "美团外卖", "美团优选", "美团买菜", "小象超市", "饿了么", "外卖", "哈啰", "青桔", "共享单车", "单车",
        "滴滴", "出行", "打车", "12306", "会员", "充值", "话费", "流量",
        "旗舰店", "专营店", "商城", "自营", "快递", "菜鸟", "网课", "线上",
    )

    private val PERSONAL_TRANSFER_WORDS = listOf(
        "转账", "红包", "还款", "提现", "收款", "AA", "代付", "亲属卡", "信用卡还款",
    )
}
