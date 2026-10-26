package app.zhizhang.data

import androidx.room.withTransaction
import app.zhizhang.domain.BillParser
import app.zhizhang.domain.CategoryClassifier
import app.zhizhang.domain.BillFingerprint
import app.zhizhang.domain.ParsedBill
import app.zhizhang.domain.DemoData
import kotlinx.coroutines.flow.Flow
import app.zhizhang.notification.NotificationDraft
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class ImportSelection(
    val bill: ParsedBill,
    val categoryId: String,
)

data class NotificationCaptureResult(
    val transactionId: Long?,
    val inserted: Boolean,
    val transaction: TransactionEntity?,
)

class TransactionRepository(
    private val database: AppDatabase,
    private val settings: SettingsRepository,
) {
    private val transactions = database.transactionDao()
    private val rules = database.merchantRuleDao()

    fun observeMonth(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Flow<List<TransactionEntity>> {
        val (start, end) = monthRange(month, zone)
        return transactions.observeBetween(start, end)
    }

    fun observeAll(): Flow<List<TransactionEntity>> = transactions.observeAll()

    fun observeCount(): Flow<Int> = transactions.observeCount()

    fun observeAutoCount(): Flow<Int> = transactions.observeAutoCount()

    fun observeRuleCount(): Flow<Int> = rules.observeCount()

    fun observeRules(): Flow<List<MerchantRuleEntity>> = rules.observeAll()

    suspend fun updateRule(merchantKey: String, categoryId: String) {
        if (Categories.all.none { it.id == categoryId }) return
        rules.upsert(MerchantRuleEntity(merchantKey = merchantKey, categoryId = categoryId))
    }

    suspend fun deleteRule(merchantKey: String) {
        rules.delete(merchantKey)
    }

    suspend fun getById(id: Long): TransactionEntity? = transactions.byId(id)

    fun observeNeedsReview(): Flow<List<TransactionEntity>> = transactions.observeNeedsReview()

    fun observeNeedsReviewCount(): Flow<Int> = transactions.observeNeedsReviewCount()

    fun observeUnprocessedNotificationCount(): Flow<Int> =
        database.notificationEventDao().observeUnprocessedCount()

    suspend fun markReviewed(id: Long) = transactions.markReviewed(id)

    suspend fun updateTransactionCategory(id: Long, categoryId: String): Boolean {
        val transaction = transactions.byId(id) ?: return false
        val category = Categories.byId(categoryId)
        if (category.type != transaction.txType) return false
        transactions.update(transaction.copy(categoryId = category.id))
        return true
    }

    suspend fun markAllReviewed(): Int = transactions.markAllReviewed()

    suspend fun clearLegacyImportConfirmations(): Int =
        transactions.clearLegacyImportConfirmations()

    suspend fun all(): List<TransactionEntity> = transactions.all()

    suspend fun count(): Int = transactions.countAll()

    suspend fun expenseForMonth(
        month: YearMonth,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val (start, end) = monthRange(month, zone)
        return transactions.between(start, end)
            .filter { it.type == TxType.EXPENSE.name }
            .sumOf { it.amountCents }
    }

    suspend fun rulesSnapshot(): Map<String, String> =
        rules.all().associate { it.merchantKey to it.categoryId }

    suspend fun existingFingerprints(): Set<String> =
        transactions.allFingerprints().toSet()

    suspend fun addManual(
        amountCents: Long,
        type: TxType,
        categoryId: String,
        accountId: String,
        merchant: String,
        note: String,
        occurredAt: Long,
        source: TxSource = TxSource.MANUAL,
        fingerprint: String? = null,
        reimbursementStatus: ReimbursementStatus = ReimbursementStatus.NONE,
    ): Long {
        val entity = TransactionEntity(
            amountCents = amountCents,
            type = type.name,
            categoryId = categoryId,
            accountId = accountId,
            merchant = merchant.trim(),
            note = note.trim(),
            occurredAt = occurredAt,
            source = source.name,
            fingerprint = fingerprint,
            reimbursementStatus = if (type == TxType.EXPENSE) reimbursementStatus.name else ReimbursementStatus.NONE.name,
        )
        val id = transactions.insertIgnore(entity)
        if (id != -1L) settings.setLastAccount(accountId)
        return id
    }

    suspend fun updateFromEditor(transaction: TransactionEntity) {
        transactions.update(transaction)
        settings.setLastAccount(transaction.accountId)
    }

    suspend fun mergeDuplicate(primaryId: Long, duplicateId: Long): Boolean =
        database.withTransaction {
            if (primaryId == duplicateId) return@withTransaction false
            val primary = transactions.byId(primaryId) ?: return@withTransaction false
            val duplicate = transactions.byId(duplicateId) ?: return@withTransaction false
            if (primary.txType != duplicate.txType ||
                primary.amountCents != duplicate.amountCents
            ) {
                return@withTransaction false
            }
            val fallback = Categories.fallbackFor(primary.txType).id
            val categoryId = if (primary.categoryId == fallback) {
                duplicate.categoryId
            } else {
                primary.categoryId
            }
            val note = listOf(primary.note, duplicate.note)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .take(160)
            transactions.update(
                primary.copy(
                    categoryId = categoryId,
                    merchant = primary.merchant.ifBlank { duplicate.merchant },
                    note = note,
                    needsConfirmation = false,
                ),
            )
            transactions.deleteById(duplicateId)
            true
        }

    suspend fun delete(id: Long) {
        database.withTransaction {
            val transaction = transactions.byId(id) ?: return@withTransaction
            if (transaction.reimbursementForId == null) {
                transactions.reimbursementFor(id)?.let { linked ->
                    transactions.deleteById(linked.id)
                }
            } else {
                val original = transactions.byId(transaction.reimbursementForId)
                if (original != null) {
                    transactions.update(
                        original.copy(reimbursementStatus = ReimbursementStatus.PENDING.name),
                    )
                }
            }
            transactions.deleteById(id)
        }
    }

    /**
     * 生成一笔报销收入，并把原支出标记为已报销。
     * 第一版只支持全额报销；已报销或找不到原支出时返回 false。
     */
    suspend fun completeReimbursement(
        originalId: Long,
        accountId: String,
        occurredAt: Long,
        source: TxSource = TxSource.AI,
    ): Boolean = database.withTransaction {
        val original = transactions.byId(originalId) ?: return@withTransaction false
        if (original.txType != TxType.EXPENSE) return@withTransaction false
        if (original.reimbursement == ReimbursementStatus.DONE) return@withTransaction false
        if (transactions.reimbursementFor(originalId) != null) return@withTransaction false

        val originalName = original.merchant.ifBlank { Categories.byId(original.categoryId).name }
        val originalDate = java.time.Instant.ofEpochMilli(original.occurredAt)
            .atZone(ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("M月d日"))
        val income = TransactionEntity(
            amountCents = original.amountCents,
            type = TxType.INCOME.name,
            categoryId = "reimbursement",
            accountId = accountId,
            merchant = "报销 · $originalName",
            note = "对应支出：$originalDate $originalName ${Formats.money(original.amountCents)}",
            occurredAt = occurredAt,
            source = source.name,
            reimbursementForId = originalId,
        )
        if (transactions.insertIgnore(income) == -1L) return@withTransaction false
        transactions.update(original.copy(reimbursementStatus = ReimbursementStatus.DONE.name))
        true
    }

    suspend fun importBills(
        items: List<ImportSelection>,
        source: TxSource = TxSource.IMPORT,
    ): Int {
        var inserted = 0
        for (item in items) {
            val bill = item.bill
            if (bill.externalId != null && transactions.findByExternalIdAny(bill.externalId) != null) {
                continue
            }
            val entity = TransactionEntity(
                amountCents = bill.amountCents,
                type = bill.type.name,
                categoryId = item.categoryId,
                accountId = bill.accountId,
                merchant = bill.merchant,
                note = bill.note,
                occurredAt = bill.occurredAt,
                source = source.name,
                fingerprint = bill.fingerprint,
                externalId = bill.externalId,
                transactionStatus = bill.status.name,
                confidence = bill.confidence,
                needsConfirmation = bill.needsConfirmation,
            )
            if (transactions.insertIgnore(entity) != -1L) {
                inserted++
                if (bill.merchant.isNotBlank()) {
                    learnRule(bill.merchant, item.categoryId)
                }
            }
        }
        return inserted
    }

    suspend fun addAiBills(items: List<ImportSelection>): Int {
        var inserted = 0
        for (item in items) {
            val bill = item.bill
            val entity = TransactionEntity(
                amountCents = bill.amountCents,
                type = bill.type.name,
                categoryId = item.categoryId,
                accountId = bill.accountId,
                merchant = bill.merchant,
                note = bill.note,
                occurredAt = bill.occurredAt,
                source = TxSource.AI.name,
                fingerprint = bill.fingerprint,
            )
            if (transactions.insertIgnore(entity) != -1L) {
                inserted++
                if (bill.merchant.isNotBlank()) learnRule(bill.merchant, item.categoryId)
            }
        }
        return inserted
    }

    /** 支付通知自动入账；返回 true 表示真的新增了一笔。 */
    suspend fun addAutomatic(bill: ParsedBill): Boolean {
        val entity = TransactionEntity(
            amountCents = bill.amountCents,
            type = bill.type.name,
            categoryId = bill.categoryId,
            accountId = bill.accountId,
            merchant = bill.merchant,
            note = bill.note,
            occurredAt = bill.occurredAt,
            source = TxSource.NOTIFICATION.name,
            fingerprint = bill.fingerprint,
        )
        return transactions.insertIgnore(entity) != -1L
    }

    /**
     * 通知自动记账的唯一落库入口。
     * 失败/撤销/退款优先关联原交易；无法确定时保留待确认记录，不按金额直接删除。
     */
    suspend fun captureNotification(draft: NotificationDraft, eventId: Long): Long? =
        captureNotificationResult(draft, eventId).transactionId

    suspend fun captureNotificationResult(
        draft: NotificationDraft,
        eventId: Long,
    ): NotificationCaptureResult = database.withTransaction {
        val now = System.currentTimeMillis()
        val status = draft.status

        if (status == TransactionStatus.FAILED || status == TransactionStatus.CANCELED) {
            val original = findOriginalForNotification(draft, now)
            if (original != null) {
                val updated = original.copy(transactionStatus = status.name)
                transactions.update(updated)
                return@withTransaction NotificationCaptureResult(original.id, false, updated)
            }
            return@withTransaction NotificationCaptureResult(null, false, null)
        }

        if (status == TransactionStatus.REFUNDED) {
            val original = findOriginalForNotification(draft, now)
            val refund = TransactionEntity(
                amountCents = draft.amountCents,
                type = TxType.INCOME.name,
                categoryId = "refund",
                accountId = draft.accountId,
                merchant = draft.merchant.ifBlank { "退款" },
                note = "自动识别退款",
                occurredAt = draft.occurredAt,
                source = TxSource.NOTIFICATION.name,
                fingerprint = BillFingerprint.of(
                    TxType.INCOME,
                    draft.amountCents,
                    draft.occurredAt,
                    draft.merchant,
                    draft.rawText + "#refund",
                ),
                sourcePackage = draft.sourcePackage,
                externalId = (draft.externalId ?: "refund-$eventId") + ":refund",
                transactionStatus = TransactionStatus.REFUNDED.name,
                confidence = draft.confidence,
                needsConfirmation = original == null || draft.needsConfirmation,
                notificationEventId = eventId,
                refundOfId = original?.id,
            )
            val id = transactions.insertIgnore(refund)
            if (id == -1L) return@withTransaction NotificationCaptureResult(null, false, null)
            if (original != null) {
                transactions.update(original.copy(transactionStatus = TransactionStatus.REFUNDED.name))
            }
            return@withTransaction NotificationCaptureResult(id, true, refund.copy(id = id))
        }

        if (draft.externalId != null) {
            val existing = transactions.findByExternalId(draft.sourcePackage, draft.externalId)
            if (existing != null) {
                val merged = existing.copy(
                    transactionStatus = if (status == TransactionStatus.UNKNOWN) {
                        existing.transactionStatus
                    } else {
                        status.name
                    },
                    merchant = existing.merchant.ifBlank { draft.merchant },
                    confidence = maxOf(existing.confidence, draft.confidence),
                    needsConfirmation = existing.needsConfirmation && draft.needsConfirmation,
                )
                transactions.update(merged)
                return@withTransaction NotificationCaptureResult(existing.id, false, merged)
            }
        }

        val start = draft.occurredAt - 10 * 60_000L
        val end = draft.occurredAt + 10 * 60_000L
        val candidates = transactions.expenseCandidates(draft.amountCents, start, end, draft.occurredAt)
        val duplicate = candidates.singleOrNull { candidate ->
            candidate.txType == TxType.EXPENSE &&
                candidate.txSource == TxSource.NOTIFICATION &&
                candidate.sourcePackage == draft.sourcePackage &&
                (draft.merchant.isBlank() || candidate.merchant.equals(draft.merchant, true))
        }
        if (duplicate != null) {
            transactions.update(
                duplicate.copy(
                    transactionStatus = if (status == TransactionStatus.UNKNOWN) {
                        duplicate.transactionStatus
                    } else {
                        status.name
                    },
                ),
            )
            return@withTransaction NotificationCaptureResult(duplicate.id, false, duplicate)
        }

        val entity = TransactionEntity(
            amountCents = draft.amountCents,
            type = draft.type.name,
            categoryId = draft.categoryId,
            accountId = draft.accountId,
            merchant = draft.merchant,
            note = "",
            occurredAt = draft.occurredAt,
            source = TxSource.NOTIFICATION.name,
            fingerprint = BillFingerprint.of(
                draft.type,
                draft.amountCents,
                draft.occurredAt,
                draft.merchant,
                draft.rawText,
            ),
            sourcePackage = draft.sourcePackage,
            externalId = draft.externalId,
            transactionStatus = status.name,
            confidence = draft.confidence,
            needsConfirmation = draft.needsConfirmation,
            notificationEventId = eventId,
        )
        val id = transactions.insertIgnore(entity)
        if (id == -1L) {
            NotificationCaptureResult(null, false, null)
        } else {
            NotificationCaptureResult(id, true, entity.copy(id = id))
        }
    }

    private suspend fun findOriginalForNotification(
        draft: NotificationDraft,
        now: Long,
    ): TransactionEntity? {
        if (draft.externalId != null) {
            transactions.findByExternalId(draft.sourcePackage, draft.externalId)?.let { return it }
        }
        val candidates = transactions.expenseCandidates(
            amount = draft.amountCents,
            start = draft.occurredAt - 24 * 60 * 60_000L,
            end = minOf(now, draft.occurredAt + 24 * 60 * 60_000L),
            center = draft.occurredAt,
        )
        return candidates
            .filter { it.txType == TxType.EXPENSE }
            .filter {
                draft.merchant.isBlank() ||
                    it.merchant.contains(draft.merchant, true) ||
                    draft.merchant.contains(it.merchant, true)
            }
            .singleOrNull()
    }

    suspend fun addDemoData(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): Int {
        if (transactions.countAll() > 0) return 0
        val demo = DemoData.build(now)
        var inserted = 0
        demo.forEach { transaction ->
            if (transactions.insertIgnore(transaction) != -1L) inserted++
        }
        if (inserted > 0) {
            settings.setBudget(600_000L)
        }
        return inserted
    }

    suspend fun learnRule(merchant: String, categoryId: String) {
        val key = CategoryClassifier.ruleKey(merchant)
        if (key.length < 2) return
        if (Categories.all.none { it.id == categoryId }) return
        rules.upsert(
            MerchantRuleEntity(
                merchantKey = key,
                categoryId = categoryId,
            ),
        )
    }

    suspend fun clearAll() {
        database.withTransaction {
            transactions.deleteAll()
            rules.deleteAll()
        }
    }

    suspend fun clearRules() {
        rules.deleteAll()
    }

    suspend fun replaceAll(transactionsToRestore: List<TransactionEntity>, rulesToRestore: List<MerchantRuleEntity>) {
        database.withTransaction {
            transactions.deleteAll()
            rules.deleteAll()
            if (transactionsToRestore.isNotEmpty()) transactions.insertAll(transactionsToRestore)
            if (rulesToRestore.isNotEmpty()) rules.upsertAll(rulesToRestore)
        }
    }

    fun parseBills(
        input: String,
        rules: Map<String, String>,
        now: java.time.LocalDateTime = java.time.LocalDateTime.now(),
        defaultAccountId: String = "wechat",
    ): List<ParsedBill> {
        val parser = BillParser(CategoryClassifier(rules))
        return parser.parse(input, now = now, zone = ZoneId.systemDefault(), defaultAccountId = defaultAccountId)
    }

    companion object {
        fun monthRange(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
            val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return start to end
        }

        fun dayRange(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return start to end
        }
    }
}
