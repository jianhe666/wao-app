package app.zhizhang.data

import android.content.Context
import org.json.JSONObject

/** 记录 AI 已处理过的账单，避免同一笔账单被反复送去分类。 */
class AiCategoryHistoryStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("ai_category_history", Context.MODE_PRIVATE)

    fun load(): Map<Long, String> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { key ->
                    val id = key.toLongOrNull() ?: return@forEach
                    val categoryId = json.optString(key)
                    if (id > 0 && categoryId.isNotBlank()) put(id, categoryId)
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun record(transactionId: Long, categoryId: String) {
        if (transactionId <= 0L || categoryId.isBlank()) return
        val json = JSONObject()
        load().forEach { (id, value) -> json.put(id.toString(), value) }
        json.put(transactionId.toString(), categoryId)
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    fun remove(transactionId: Long) {
        val json = JSONObject()
        load().filterKeys { it != transactionId }.forEach { (id, value) ->
            json.put(id.toString(), value)
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private companion object {
        const val KEY = "items"
    }
}
