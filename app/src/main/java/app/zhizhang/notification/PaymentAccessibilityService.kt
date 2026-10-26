package app.zhizhang.notification

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.zhizhang.ZhizhangApp
import app.zhizhang.domain.CategoryClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 可选的支付结果页增强识别。
 *
 * 只有用户在系统设置里手动开启、且“支付通知记账”开关同时打开时才工作。
 * 仅处理微信/支付宝，只读取当前窗口的可见文字，不截屏、不保存原始页面、不上传。
 */
class PaymentAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastPackage = ""
    private var lastEventAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName !in ALLOWED_PACKAGES) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val now = System.currentTimeMillis()
        if (packageName == lastPackage && now - lastEventAt < 1_200L) return
        lastPackage = packageName
        lastEventAt = now

        val app = applicationContext as? ZhizhangApp ?: return
        val eventText = buildString {
            event.text?.forEach { part -> if (part.isNotBlank()) append(part).append(' ') }
            event.contentDescription?.takeIf { it.isNotBlank() }?.let { append(it).append(' ') }
        }
        val text = (eventText + " " + collectAllWindowText()).trim()
        if (text.length < 8) return
        if (PAYMENT_WORDS.none(text::contains)) return
        if (CHAT_NOISE.any(text::contains)) return

        scope.launch {
            runCatching {
                if (!app.settings.autoCaptureEnabled.first()) return@runCatching
                val rules = app.repository.rulesSnapshot()
                val now = java.time.LocalDateTime.now()
                val snapshot = NotificationSnapshot(
                    packageName = packageName,
                    appLabel = packageName,
                    notificationKey = "accessibility-${System.currentTimeMillis()}",
                    text = text,
                    postedAt = System.currentTimeMillis(),
                )
                val draft = NotificationCaptureParser(
                    classifier = CategoryClassifier(rules),
                ).parse(
                    snapshot = snapshot,
                    allowAllApps = false,
                    now = now,
                ).draft ?: return@runCatching
                if (draft.amountCents <= 0) return@runCatching
                val result = app.repository.captureNotificationResult(draft, eventId = 0L)
                if (result.inserted && result.transaction != null) {
                    BillChangeNotifier.notify(app, result.transaction)
                }
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun collectAllWindowText(): String {
        val builder = StringBuilder()
        var visited = 0
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots += it }
        runCatching {
            windows?.forEach { window ->
                window.root?.let { roots += it }
            }
        }
        val seen = HashSet<String>()

        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || visited > 900 || builder.length > 6_000 || depth > 16) return
            visited += 1
            listOf(node.text, node.contentDescription).forEach { value ->
                val text = value?.toString()?.trim().orEmpty()
                if (text.isNotBlank() && seen.add(text)) builder.append(text).append(' ')
            }
            val count = minOf(node.childCount, 50)
            for (index in 0 until count) {
                visit(node.getChild(index), depth + 1)
            }
        }

        roots.forEach { visit(it, 0) }
        return builder.toString().replace(Regex("\\s+"), " ").trim()
    }

    private companion object {
        // 仅微信、支付宝、购物和外卖；不包含银行、云闪付。
        val ALLOWED_PACKAGES = setOf(
            "com.tencent.mm",
            "com.eg.android.AlipayGphone",
            "com.taobao.taobao",
            "com.jingdong.app.mall",
            "com.xunmeng.pinduoduo",
            "com.ss.android.ugc.aweme",
            "com.xingin.xhs",
            "com.sankuai.meituan",
            "com.sankuai.meituan.takeoutnew",
            "me.ele",
        )
        val PAYMENT_WORDS = listOf(
            "付款成功", "支付成功", "交易成功", "已支付", "收款到账", "收款成功",
            "扣款成功", "支出", "转账", "付款", "支付",
        )
        val CHAT_NOISE = listOf("条新消息", "群聊", "有人@你", "[图片]", "[语音]", "[视频]")
    }
}
