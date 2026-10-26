package app.zhizhang.domain

import app.zhizhang.data.Categories
import app.zhizhang.data.TxType
import app.zhizhang.data.TransactionEntity

data class LearningCandidate(
    val merchant: String,
    val merchantKey: String,
    val type: TxType,
)

/**
 * AI 分类的候选筛选：已有规则、已有分类、待确认建议都不再重复询问。
 * 这里的判断完全在本地完成。
 */
object CategoryLearning {

    fun candidates(
        transactions: List<TransactionEntity>,
        learnedRules: Map<String, String>,
        pendingKeys: Set<String> = emptySet(),
        limit: Int = 40,
    ): List<LearningCandidate> {
        return transactions
            .filter { it.merchant.isNotBlank() }
            .filterNot { it.isTransferLike() }
            .groupBy { CategoryClassifier.ruleKey(it.merchant) }
            .asSequence()
            .filter { (key, _) -> key.length >= 2 && key !in learnedRules && key !in pendingKeys }
            .mapNotNull { (key, list) ->
                val type = list.groupingBy { it.txType }.eachCount()
                    .maxByOrNull { it.value }?.key ?: TxType.EXPENSE
                val sameType = list.filter { it.txType == type }
                val fallback = Categories.fallbackFor(type).id
                // 只要这类消费已经被用户分到具体分类，就视为已学习，不再让 AI 重复建议。
                val alreadyCategorized = sameType.any {
                    it.categoryId != fallback && Categories.byId(it.categoryId).type == type
                }
                if (alreadyCategorized) return@mapNotNull null
                LearningCandidate(list.first().merchant, key, type)
            }
            .distinctBy { it.merchantKey }
            .take(limit)
            .toList()
    }

    private fun TransactionEntity.isTransferLike(): Boolean {
        val text = "$merchant $note"
        if (TRANSFER_WORDS.any(text::contains)) return true
        return Categories.byId(categoryId).id == "social"
    }

    private val TRANSFER_WORDS = listOf(
        "转账", "红包", "还款", "提现", "收款", "AA", "代付", "亲属卡", "信用卡还款",
    )
}
