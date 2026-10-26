package app.zhizhang.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import app.zhizhang.domain.CsvBillCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BackupManager(
    private val context: Context,
    private val repository: TransactionRepository,
    private val settings: SettingsRepository,
) {

    suspend fun exportCsv(): Uri = withContext(Dispatchers.IO) {
        val csv = CsvBillCodec.encode(repository.all())
        val file = newFile("wao-bills-${fileStamp()}.csv")
        file.writeText(csv, Charsets.UTF_8)
        uriFor(file)
    }

    suspend fun buildBackupJson(): String = withContext(Dispatchers.IO) {
        val transactions = repository.all()
        val rules = repository.rulesSnapshot()
        val budget = repositoryBudget()
        val root = JSONObject()
        root.put("app", "zhizhang")
        root.put("schema", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("budgetCents", budget)

        val array = JSONArray()
        transactions.sortedBy { it.occurredAt }.forEach { transaction ->
            array.put(
                JSONObject().apply {
                    put("id", transaction.id)
                    put("amountCents", transaction.amountCents)
                    put("type", transaction.type)
                    put("categoryId", transaction.categoryId)
                    put("accountId", transaction.accountId)
                    put("merchant", transaction.merchant)
                    put("note", transaction.note)
                    put("occurredAt", transaction.occurredAt)
                    put("source", transaction.source)
                    put("fingerprint", transaction.fingerprint ?: JSONObject.NULL)
                    put("reimbursementStatus", transaction.reimbursementStatus)
                    put("reimbursementForId", transaction.reimbursementForId ?: JSONObject.NULL)
                    put("sourcePackage", transaction.sourcePackage ?: JSONObject.NULL)
                    put("externalId", transaction.externalId ?: JSONObject.NULL)
                    put("transactionStatus", transaction.transactionStatus)
                    put("confidence", transaction.confidence)
                    put("needsConfirmation", transaction.needsConfirmation)
                    put("notificationEventId", transaction.notificationEventId ?: JSONObject.NULL)
                    put("refundOfId", transaction.refundOfId ?: JSONObject.NULL)
                    put("createdAt", transaction.createdAt)
                },
            )
        }
        root.put("transactions", array)

        val ruleArray = JSONArray()
        rules.forEach { (merchantKey, categoryId) ->
            ruleArray.put(
                JSONObject().apply {
                    put("merchantKey", merchantKey)
                    put("categoryId", categoryId)
                },
            )
        }
        root.put("rules", ruleArray)
        root.toString(2)
    }

    suspend fun exportJson(): Uri = withContext(Dispatchers.IO) {
        val json = buildBackupJson()
        val file = newFile("wao-backup-${fileStamp()}.json")
        file.writeText(json, Charsets.UTF_8)
        uriFor(file)
    }

    suspend fun restoreJson(uri: Uri): Int = withContext(Dispatchers.IO) {
        val json = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: throw IllegalArgumentException("无法读取这个文件")
        restoreJsonText(json)
    }

    suspend fun restoreJsonText(json: String): Int = withContext(Dispatchers.IO) {
        if (json.length > 20_000_000) throw IllegalArgumentException("备份文件过大")

        val root = JSONObject(json)
        require(root.optString("app") == "zhizhang") { "这不是哇哦记账的备份文件" }
        val schema = root.optInt("schema", 0)
        require(schema == 1) { "备份文件版本暂不支持" }

        val transactionsArray = root.optJSONArray("transactions") ?: JSONArray()
        require(transactionsArray.length() <= 100_000) { "备份文件包含的记录过多" }
        val restored = mutableListOf<TransactionEntity>()
        for (index in 0 until transactionsArray.length()) {
            val item = transactionsArray.optJSONObject(index) ?: continue
            val amount = item.optLong("amountCents", 0L)
            val occurredAt = item.optLong("occurredAt", 0L)
            if (amount <= 0L || occurredAt <= 0L) continue
            val type = TxType.entries.firstOrNull { it.name == item.optString("type") } ?: continue
            val categoryId = item.optString("categoryId")
                .takeIf { id -> Categories.all.any { it.id == id } }
                ?: Categories.defaultFor(type).id
            val accountId = item.optString("accountId")
                .takeIf { id -> Accounts.isValid(id) }
                ?: "other"
            val fingerprint = if (item.isNull("fingerprint")) {
                null
            } else {
                item.optString("fingerprint").takeIf { it.isNotBlank() }
            }
            restored += TransactionEntity(
                id = item.optLong("id", 0L),
                amountCents = amount,
                type = type.name,
                categoryId = categoryId,
                accountId = accountId,
                merchant = item.optString("merchant"),
                note = item.optString("note"),
                occurredAt = occurredAt,
                source = TxSource.entries.firstOrNull { it.name == item.optString("source") }?.name
                    ?: TxSource.MANUAL.name,
                fingerprint = fingerprint,
                reimbursementStatus = ReimbursementStatus.from(
                    item.optString("reimbursementStatus"),
                ).name,
                reimbursementForId = if (item.isNull("reimbursementForId")) {
                    null
                } else {
                    item.optLong("reimbursementForId", 0L).takeIf { it > 0L }
                },
                sourcePackage = item.optString("sourcePackage").takeIf { it.isNotBlank() },
                externalId = item.optString("externalId").takeIf { it.isNotBlank() },
                transactionStatus = TransactionStatus.from(
                    item.optString("transactionStatus"),
                ).name,
                confidence = item.optDouble("confidence", 0.9).toFloat().coerceIn(0f, 1f),
                needsConfirmation = item.optBoolean("needsConfirmation", false),
                notificationEventId = if (item.isNull("notificationEventId")) {
                    null
                } else {
                    item.optLong("notificationEventId", 0L).takeIf { it > 0L }
                },
                refundOfId = if (item.isNull("refundOfId")) {
                    null
                } else {
                    item.optLong("refundOfId", 0L).takeIf { it > 0L }
                },
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
            )
        }

        val ruleArray = root.optJSONArray("rules") ?: JSONArray()
        val restoredRules = mutableListOf<MerchantRuleEntity>()
        for (index in 0 until ruleArray.length()) {
            val item = ruleArray.optJSONObject(index) ?: continue
            val key = item.optString("merchantKey")
            val categoryId = item.optString("categoryId")
            if (key.isBlank() || Categories.all.none { it.id == categoryId }) continue
            restoredRules += MerchantRuleEntity(merchantKey = key, categoryId = categoryId)
        }

        repository.replaceAll(restored, restoredRules)
        settings.setBudget(root.optLong("budgetCents", 0L))
        restored.size
    }

    private suspend fun repositoryBudget(): Long = settings.budgetCents.first()

    private fun newFile(name: String): File {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(directory, name)
    }

    private fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    private fun fileStamp(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))


}
