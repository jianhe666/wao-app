package app.zhizhang.domain

import android.app.Notification
import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import app.zhizhang.notification.buildNotificationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationSnapshotTest {

    @Test
    fun `extracts all supported notification extras`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notification = Notification.Builder(context, "test")
            .setContentTitle("微信支付")
            .setContentText("付款成功")
            .setTicker("ticker")
            .build()
        notification.extras.apply {
            putCharSequence(Notification.EXTRA_BIG_TEXT, "商户：瑞幸咖啡 ¥19.90")
            putCharSequence(Notification.EXTRA_SUB_TEXT, "副标题")
            putCharSequence(Notification.EXTRA_SUMMARY_TEXT, "摘要")
            putCharSequence(Notification.EXTRA_INFO_TEXT, "信息")
            putCharSequenceArray(
                Notification.EXTRA_TEXT_LINES,
                arrayOf("交易单号 420000123", "第二行"),
            )
            putParcelableArray(
                Notification.EXTRA_MESSAGES,
                arrayOf(Bundle().apply { putCharSequence("text", "消息正文") }),
            )
        }

        val snapshot = buildNotificationSnapshot(
            packageName = "com.tencent.mm",
            appLabel = "微信",
            notificationKey = "key-1",
            postTime = 1_000L,
            notification = notification,
        )
        requireNotNull(snapshot)
        assertEquals("微信支付", snapshot.title)
        assertTrue(snapshot.combined.contains("付款成功"))
        assertTrue(snapshot.combined.contains("瑞幸咖啡"))
        assertTrue(snapshot.combined.contains("交易单号 420000123"))
        assertTrue(snapshot.combined.contains("消息正文"))
        assertEquals(1_000L, snapshot.postedAt)
    }

    @Test
    fun `empty notification returns null`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notification = Notification.Builder(context, "test").build()
        assertNull(
            buildNotificationSnapshot(
                "com.tencent.mm",
                "微信",
                "key",
                1L,
                notification,
            ),
        )
    }
}
