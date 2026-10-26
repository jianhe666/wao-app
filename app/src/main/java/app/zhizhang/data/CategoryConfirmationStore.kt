package app.zhizhang.data

import android.content.Context
import app.zhizhang.domain.CategoryClassifier
import org.json.JSONObject

/** 用户确认过的“商户 + 备注”分类，之后同类账单直接本地分类。 */
class CategoryConfirmationStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("category_confirmations", Context.MODE_PRIVATE)

    fun load(): Map<String, String> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { key ->
                    val categoryId = json.optString(key)
                    if (categoryId.isNotBlank()) put(key, categoryId)
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun put(signature: String, categoryId: String) {
        if (signature.isBlank() || categoryId.isBlank()) return
        val json = JSONObject()
        load().forEach { (key, value) -> json.put(key, value) }
        json.put(signature, categoryId)
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        fun signature(transaction: TransactionEntity): String = buildString {
            append(transaction.type)
            append('|')
            append(CategoryClassifier.ruleKey(transaction.merchant))
            append('|')
            append(CategoryClassifier.normalize(transaction.note).take(32))
        }
        private const val KEY = "items"
    }
}
