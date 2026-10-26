package app.zhizhang.domain

import app.zhizhang.data.TxSource
import app.zhizhang.data.TxType
import app.zhizhang.data.TransactionEntity
import kotlin.math.abs

data class MergeCandidate(
    val automatic: TransactionEntity,
    val userRecord: TransactionEntity,
) {
    val key: String get() = "${automatic.id}:${userRecord.id}"
}

/** 自动抓取记录与对话/手动记录的近似重复检测；只提示，不自动删除。 */
object DuplicateMerge {
    private const val WINDOW_MS = 10 * 60 * 1000L

    fun find(
        transactions: List<TransactionEntity>,
        windowMs: Long = WINDOW_MS,
    ): List<MergeCandidate> {
        val automatic = transactions.filter {
            it.txSource == TxSource.NOTIFICATION &&
                (it.txType == TxType.EXPENSE || it.txType == TxType.INCOME)
        }
        val userRecords = transactions.filter {
            it.txSource == TxSource.AI || it.txSource == TxSource.MANUAL
        }
        val usedUsers = mutableSetOf<Long>()
        val usersByAmount = userRecords.groupBy { it.txType to it.amountCents }
        return automatic
            .sortedByDescending { it.occurredAt }
            .mapNotNull { auto ->
                val match = usersByAmount[auto.txType to auto.amountCents]
                    .orEmpty()
                    .asSequence()
                    .filter { it.id !in usedUsers }
                    .filter { it.id != auto.id }
                    .filter { abs(it.occurredAt - auto.occurredAt) <= windowMs }
                    .minByOrNull { abs(it.occurredAt - auto.occurredAt) }
                    ?: return@mapNotNull null
                usedUsers += match.id
                MergeCandidate(auto, match)
            }
            .take(3)
    }
}
