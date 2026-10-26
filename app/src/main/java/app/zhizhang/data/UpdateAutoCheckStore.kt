package app.zhizhang.data

import android.content.Context

/** 每天最多自动检查一次更新，不在后台轮询。 */
class UpdateAutoCheckStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("update_auto_check", Context.MODE_PRIVATE)

    fun shouldCheck(now: Long = System.currentTimeMillis()): Boolean {
        val last = prefs.getLong(LAST_CHECK, 0L)
        return now - last >= 24 * 60 * 60 * 1000L
    }

    fun markChecked(now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(LAST_CHECK, now).apply()
    }

    private companion object {
        const val LAST_CHECK = "last_check"
    }
}
