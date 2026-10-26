package app.zhizhang.notification

import app.zhizhang.domain.ParsedBill

/** 通知与无障碍可能同时看到同一笔支付，用同一个进程内窗口去重。 */
object AutomaticCaptureGate {
    private const val WINDOW_MS = 120_000L
    private val recent = HashMap<String, Long>()

    @Synchronized
    fun accept(packageName: String, bill: ParsedBill): Boolean {
        val now = System.currentTimeMillis()
        recent.entries.removeAll { now - it.value > WINDOW_MS }
        // 跨应用去重：支付通知和无障碍可能从不同包名看到同一笔。
        val key = "${bill.type}|${bill.amountCents}"
        if (recent.containsKey(key)) return false
        recent[key] = now
        return true
    }
}
