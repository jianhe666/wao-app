package app.zhizhang.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 自定义分类保存在本机 SharedPreferences，启动时注册到 Categories。 */
class CustomCategoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("custom_categories", Context.MODE_PRIVATE)

    fun load(): List<Category> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val name = item.optString("name").trim()
                    if (id.isBlank() || name.isBlank()) continue
                    val type = TxType.entries.firstOrNull { it.name == item.optString("type") }
                        ?: TxType.EXPENSE
                    add(
                        Category(
                            id = id,
                            name = name.take(8),
                            emoji = item.optString("emoji", "✳️").ifBlank { "✳️" },
                            color = item.optLong("color", 0xFF8A8F9C).toULong().toLong(),
                            type = type,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(name: String, emoji: String, color: Long, type: TxType): Category? {
        val cleanName = name.trim().take(8)
        if (cleanName.isBlank()) return null
        val category = Category(
            id = "custom:${UUID.randomUUID()}",
            name = cleanName,
            emoji = emoji.trim().ifBlank { "✳️" }.take(2),
            color = color,
            type = type,
        )
        save(load() + category)
        return category
    }

    fun delete(id: String): List<Category> {
        val next = load().filterNot { it.id == id }
        save(next)
        return next
    }

    private fun save(categories: List<Category>) {
        val array = JSONArray()
        categories.forEach { category ->
            array.put(
                JSONObject().apply {
                    put("id", category.id)
                    put("name", category.name)
                    put("emoji", category.emoji)
                    put("color", category.color)
                    put("type", category.type.name)
                },
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "items"
    }
}
