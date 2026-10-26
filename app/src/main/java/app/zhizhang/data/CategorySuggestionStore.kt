package app.zhizhang.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CategorySuggestion(
    val merchantKey: String,
    val merchant: String,
    val categoryId: String,
    val confidence: Float,
    val reason: String,
    val type: TxType,
)

class CategorySuggestionStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("category_suggestions", Context.MODE_PRIVATE)

    fun load(): List<CategorySuggestion> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val key = item.optString("merchantKey")
                    val name = item.optString("merchant")
                    val categoryId = item.optString("categoryId")
                    if (key.isBlank() || name.isBlank() || categoryId.isBlank()) continue
                    val type = TxType.entries.firstOrNull { it.name == item.optString("type") }
                        ?: TxType.EXPENSE
                    add(
                        CategorySuggestion(
                            merchantKey = key,
                            merchant = name,
                            categoryId = categoryId,
                            confidence = item.optDouble("confidence", 0.5).toFloat().coerceIn(0f, 1f),
                            reason = item.optString("reason"),
                            type = type,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun merge(suggestions: List<CategorySuggestion>): List<CategorySuggestion> {
        val merged = (load() + suggestions)
            .associateBy { it.merchantKey }
            .values
            .toList()
        save(merged)
        return merged
    }

    fun removeAll(merchantKeys: Set<String>): List<CategorySuggestion> {
        val next = load().filterNot { it.merchantKey in merchantKeys }
        save(next)
        return next
    }

    fun remove(merchantKey: String): List<CategorySuggestion> {
        val next = load().filterNot { it.merchantKey == merchantKey }
        save(next)
        return next
    }

    fun clear(): List<CategorySuggestion> {
        save(emptyList())
        return emptyList()
    }

    private fun save(suggestions: List<CategorySuggestion>) {
        val array = JSONArray()
        suggestions.forEach { suggestion ->
            array.put(
                JSONObject().apply {
                    put("merchantKey", suggestion.merchantKey)
                    put("merchant", suggestion.merchant)
                    put("categoryId", suggestion.categoryId)
                    put("confidence", suggestion.confidence.toDouble())
                    put("reason", suggestion.reason)
                    put("type", suggestion.type.name)
                },
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "items"
    }
}
