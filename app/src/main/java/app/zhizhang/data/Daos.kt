package app.zhizhang.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query(
        "SELECT * FROM transactions WHERE occurredAt >= :start AND occurredAt < :end " +
            "ORDER BY occurredAt DESC, id DESC",
    )
    fun observeBetween(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE occurredAt >= :start AND occurredAt < :end " +
            "ORDER BY occurredAt DESC, id DESC",
    )
    suspend fun between(start: Long, end: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC")
    suspend fun all(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE reimbursementForId = :originalId LIMIT 1")
    suspend fun reimbursementFor(originalId: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE sourcePackage = :packageName AND externalId = :externalId LIMIT 1")
    suspend fun findByExternalId(packageName: String, externalId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE externalId = :externalId LIMIT 1")
    suspend fun findByExternalIdAny(externalId: String): TransactionEntity?

    @Query(
        "SELECT * FROM transactions WHERE type = 'EXPENSE' AND amountCents = :amount " +
            "AND occurredAt BETWEEN :start AND :end ORDER BY ABS(occurredAt - :center) LIMIT 5",
    )
    suspend fun expenseCandidates(
        amount: Long,
        start: Long,
        end: Long,
        center: Long,
    ): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE needsConfirmation = 1 ORDER BY occurredAt DESC")
    fun observeNeedsReview(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE needsConfirmation = 1")
    fun observeNeedsReviewCount(): Flow<Int>

    @Query(
        "UPDATE transactions SET needsConfirmation = 0, " +
            "transactionStatus = CASE WHEN transactionStatus IN ('PENDING','UNKNOWN') " +
            "THEN 'SUCCESS' ELSE transactionStatus END WHERE id = :id",
    )
    suspend fun markReviewed(id: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE needsConfirmation = 1")
    suspend fun needsReviewCount(): Int

    @Query(
        "UPDATE transactions SET needsConfirmation = 0, " +
            "transactionStatus = CASE WHEN transactionStatus IN ('PENDING','UNKNOWN') " +
            "THEN 'SUCCESS' ELSE transactionStatus END WHERE needsConfirmation = 1",
    )
    suspend fun markAllReviewed(): Int

    @Query(
        "UPDATE transactions SET needsConfirmation = 0 " +
            "WHERE source = 'IMPORT' AND transactionStatus = 'SUCCESS' AND needsConfirmation = 1",
    )
    suspend fun clearLegacyImportConfirmations(): Int

    @Query("SELECT fingerprint FROM transactions WHERE fingerprint IS NOT NULL")
    suspend fun allFingerprints(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE source = 'NOTIFICATION'")
    fun observeAutoCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun countAll(): Int
}

@Dao
interface MerchantRuleDao {

    @Query("SELECT * FROM merchant_rules ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MerchantRuleEntity>>

    @Query("SELECT * FROM merchant_rules")
    suspend fun all(): List<MerchantRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<MerchantRuleEntity>)

    @Query("DELETE FROM merchant_rules")
    suspend fun deleteAll()

    @Query("DELETE FROM merchant_rules WHERE merchantKey = :merchantKey")
    suspend fun delete(merchantKey: String)

    @Query("SELECT COUNT(*) FROM merchant_rules")
    fun observeCount(): Flow<Int>
}

@Dao
interface NotificationEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(event: NotificationEventEntity): Long

    @Query("UPDATE notification_events SET processed = :processed, parseError = :error, transactionId = :transactionId WHERE id = :id")
    suspend fun markProcessed(id: Long, processed: Boolean, error: String?, transactionId: Long?)

    @Query("SELECT * FROM notification_events ORDER BY capturedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<NotificationEventEntity>>

    @Query("DELETE FROM notification_events WHERE capturedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM notification_events WHERE processed = 0")
    fun observeUnprocessedCount(): Flow<Int>

    @Query("UPDATE notification_events SET processed = 1 WHERE id = :id")
    suspend fun markIgnored(id: Long)

    @Query("SELECT * FROM notification_events WHERE processed = 0 ORDER BY capturedAt DESC LIMIT :limit")
    suspend fun unprocessed(limit: Int): List<NotificationEventEntity>

    @Query("DELETE FROM notification_events WHERE id = :id")
    suspend fun deleteById(id: Long)
}
