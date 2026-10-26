package app.zhizhang.data

import android.content.Context

/** AI 对话记录本地持久化；只有用户手动清空时才删除。 */
class ChatHistoryStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("ai_chat_history", Context.MODE_PRIVATE)

    fun load(): String? = prefs.getString(KEY, null)

    fun save(json: String) {
        prefs.edit().putString(KEY, json).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "messages"
    }
}
