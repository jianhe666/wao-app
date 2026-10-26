package app.zhizhang.data

import android.content.Context

/** 用户选择“保留两笔”后，记住这组候选，避免重启后又反复提示。 */
class MergeDismissalStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("merge_dismissals", Context.MODE_PRIVATE)

    fun load(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty()

    fun add(key: String) {
        prefs.edit().putStringSet(KEY, load() + key).apply()
    }

    private companion object {
        const val KEY = "keys"
    }
}
