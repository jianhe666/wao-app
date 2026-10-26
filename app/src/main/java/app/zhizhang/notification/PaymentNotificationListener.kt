package app.zhizhang.notification

import android.app.Notification
import android.content.ComponentName
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.zhizhang.ZhizhangApp
import app.zhizhang.data.NotificationEventEntity
import app.zhizhang.domain.CategoryClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId

/** 监听服务的健康状态，设置页和排障页可以读取。 */
object NotificationCaptureStatus {
    val connected = MutableStateFlow(false)
    val lastEventAt = MutableStateFlow(0L)
    val lastError = MutableStateFlow<String?>(null)
}

/**
 * 支付通知自动记账。
 *
 * 通知回调只负责保存最小事件和入队；规则解析、去重、退款关联全部在 IO 线程完成。
 */
class PaymentNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val zone = ZoneId.systemDefault()

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationCaptureStatus.connected.value = true
        NotificationCaptureStatus.lastError.value = null
        val app = applicationContext as? ZhizhangApp ?: return
        scope.launch {
            runCatching {
                app.database.notificationEventDao().deleteOlderThan(
                    System.currentTimeMillis() - EVENT_RETENTION_MS,
                )
            }
            // 清掉旧版本误存的非支付通知；它们不会再出现在待确认页。
            runCatching {
                val allowAll = app.settings.autoCaptureAllApps.first()
                val parser = NotificationCaptureParser(
                    classifier = CategoryClassifier(emptyMap()),
                    zone = zone,
                )
                app.database.notificationEventDao().unprocessed(100).forEach { event ->
                    val snapshot = NotificationSnapshot(
                        packageName = event.packageName,
                        appLabel = event.appLabel,
                        notificationKey = event.notificationKey,
                        title = event.title,
                        text = event.body,
                        postedAt = event.postedAt,
                    )
                    if (!parser.isPotentiallyRelevant(snapshot, allowAll)) {
                        app.database.notificationEventDao().deleteById(event.id)
                    }
                }
            }
            // 服务断开期间还留在通知栏里的通知，重连后补处理。
            runCatching {
                activeNotifications
                    ?.takeLast(30)
                    ?.forEach(::enqueue)
            }.onFailure {
                NotificationCaptureStatus.lastError.value = it.message
            }
        }
    }

    override fun onListenerDisconnected() {
        NotificationCaptureStatus.connected.value = false
        super.onListenerDisconnected()
        // 系统通常会自动重绑；这里只尝试一次，不循环轮询。
        scope.launch {
            delay(2_000L)
            runCatching {
                requestRebind(ComponentName(this@PaymentNotificationListener, PaymentNotificationListener::class.java))
            }.onFailure {
                NotificationCaptureStatus.lastError.value = "通知监听断开，请重新授予通知访问权限"
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        NotificationCaptureStatus.lastEventAt.value = System.currentTimeMillis()
        enqueue(sbn)
    }

    private fun enqueue(sbn: StatusBarNotification) {
        val app = applicationContext as? ZhizhangApp ?: return
        val snapshot = snapshotOf(sbn) ?: return
        scope.launch {
            if (!app.settings.autoCaptureEnabled.first()) return@launch
            val allowAll = app.settings.autoCaptureAllApps.first()
            if (!NotificationCaptureParser(
                    classifier = CategoryClassifier(emptyMap()),
                    zone = zone,
                ).isPotentiallyRelevant(snapshot, allowAll)
            ) {
                return@launch
            }
            val eventId = runCatching {
                val hash = contentHash(snapshot)
                val event = NotificationEventEntity(
                    packageName = snapshot.packageName,
                    appLabel = snapshot.appLabel,
                    notificationKey = snapshot.notificationKey,
                    title = snapshot.title.take(300),
                    body = snapshot.combined.take(4_000),
                    postedAt = snapshot.postedAt,
                    contentHash = hash,
                )
                app.database.notificationEventDao().insertIgnore(event)
            }.getOrElse {
                NotificationCaptureStatus.lastError.value = "通知保存失败：${it.message}"
                return@launch
            }
            if (eventId == -1L) return@launch

            try {
                val rules = app.repository.rulesSnapshot()
                val parsed = NotificationCaptureParser(
                    classifier = CategoryClassifier(rules),
                    zone = zone,
                ).parse(
                    snapshot = snapshot,
                    allowAllApps = allowAll,
                    now = LocalDateTime.now(zone),
                )
                val draft = parsed.draft
                if (draft == null) {
                    app.database.notificationEventDao().markProcessed(
                        id = eventId,
                        processed = false,
                        error = parsed.reason,
                        transactionId = null,
                    )
                    return@launch
                }
                val result = app.repository.captureNotificationResult(draft, eventId)
                app.database.notificationEventDao().markProcessed(
                    id = eventId,
                    processed = result.transactionId != null,
                    error = if (result.transactionId == null) "未找到可关联的原交易" else null,
                    transactionId = result.transactionId,
                )
                if (result.inserted && result.transaction != null) {
                    BillChangeNotifier.notify(app, result.transaction)
                }
            } catch (error: Exception) {
                NotificationCaptureStatus.lastError.value = error.message
                app.database.notificationEventDao().markProcessed(
                    id = eventId,
                    processed = false,
                    error = error.message?.take(200),
                    transactionId = null,
                )
            }
        }
    }

    private fun snapshotOf(sbn: StatusBarNotification): NotificationSnapshot? {
        val notification = sbn.notification ?: return null
        return buildNotificationSnapshot(
            packageName = sbn.packageName,
            appLabel = appLabel(sbn.packageName),
            notificationKey = sbn.key ?: "${sbn.packageName}:${sbn.id}",
            postTime = sbn.postTime,
            notification = notification,
        )
    }

    private fun appLabel(packageName: String): String = runCatching {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    private fun contentHash(snapshot: NotificationSnapshot): String {
        val raw = buildString {
            append(snapshot.packageName).append('|')
            append(snapshot.notificationKey).append('|')
            append(snapshot.combined)
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            .take(32)
    }

    override fun onDestroy() {
        NotificationCaptureStatus.connected.value = false
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val EVENT_RETENTION_MS = 24 * 60 * 60 * 1000L
    }
}
