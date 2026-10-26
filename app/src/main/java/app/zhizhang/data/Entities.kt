package app.zhizhang.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["occurredAt"]),
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["sourcePackage", "externalId"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val amountCents: Long,
    val type: String,
    val categoryId: String,
    val accountId: String,
    val merchant: String = "",
    val note: String = "",
    val occurredAt: Long,
    val source: String = TxSource.MANUAL.name,
    val fingerprint: String? = null,
    @ColumnInfo(defaultValue = "'NONE'")
    val reimbursementStatus: String = ReimbursementStatus.NONE.name,
    /** 报销收入指向原支出；普通流水为 null。 */
    val reimbursementForId: Long? = null,
    /** 自动记账来源包名、官方单号、状态和置信度。 */
    val sourcePackage: String? = null,
    val externalId: String? = null,
    val transactionStatus: String = TransactionStatus.SUCCESS.name,
    val confidence: Float = 0.9f,
    val needsConfirmation: Boolean = false,
    val notificationEventId: Long? = null,
    val refundOfId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val txType: TxType get() = TxType.from(type)
    val txSource: TxSource get() = TxSource.from(source)
    val reimbursement: ReimbursementStatus get() = ReimbursementStatus.from(reimbursementStatus)
    val status: TransactionStatus get() = TransactionStatus.from(transactionStatus)
}

@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey val merchantKey: String,
    val categoryId: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
