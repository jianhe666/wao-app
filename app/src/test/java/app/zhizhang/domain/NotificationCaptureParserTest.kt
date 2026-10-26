package app.zhizhang.domain

import app.zhizhang.data.TransactionStatus
import app.zhizhang.data.TxType
import app.zhizhang.notification.NotificationCaptureParser
import app.zhizhang.notification.NotificationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class NotificationCaptureParserTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 10, 24, 12, 0)
    private val parser = NotificationCaptureParser(CategoryClassifier(emptyMap()), zone)

    private fun snapshot(
        packageName: String,
        title: String,
        text: String,
        bigText: String = "",
        lines: List<String> = emptyList(),
    ) = NotificationSnapshot(
        packageName = packageName,
        appLabel = packageName,
        notificationKey = "$packageName:1",
        title = title,
        text = text,
        bigText = bigText,
        textLines = lines,
        postedAt = now.atZone(zone).toInstant().toEpochMilli(),
    )

    @Test
    fun `wechat payment success extracts amount merchant and transaction id`() {
        val result = parser.parse(
            snapshot(
                "com.tencent.mm",
                "微信支付",
                "付款成功 商户：瑞幸咖啡 金额 ¥19.90 交易单号 4200001234567890",
            ),
            allowAllApps = false,
            now = now,
        )
        val draft = result.draft
        assertNotNull(draft)
        assertEquals(1_990L, draft?.amountCents)
        assertEquals(TxType.EXPENSE, draft?.type)
        assertEquals("瑞幸咖啡", draft?.merchant)
        assertEquals("4200001234567890", draft?.externalId)
        assertEquals(TransactionStatus.SUCCESS, draft?.status)
        assertEquals("wechat", draft?.accountId)
        assertEquals("food", draft?.categoryId)
        assertTrue((draft?.confidence ?: 0f) > 0.7f)
    }

    @Test
    fun `alipay success and refund have different directions`() {
        val success = parser.parse(
            snapshot(
                "com.eg.android.AlipayGphone",
                "支付宝",
                "交易成功 12.50元 商户：美团外卖 交易号 20261024000001",
            ),
            false,
            now,
        ).draft
        assertEquals(TxType.EXPENSE, success?.type)
        assertEquals(TransactionStatus.SUCCESS, success?.status)

        val refund = parser.parse(
            snapshot(
                "com.eg.android.AlipayGphone",
                "支付宝",
                "退款成功 26.40元 商户：滴滴出行 交易号 20261024000002",
            ),
            false,
            now,
        ).draft
        assertEquals(TxType.INCOME, refund?.type)
        assertEquals("refund", refund?.categoryId)
        assertEquals(TransactionStatus.REFUNDED, refund?.status)
    }

    @Test
    fun `shopping notification without merchant is captured but needs confirmation`() {
        val draft = parser.parse(
            snapshot(
                "com.taobao.taobao",
                "淘宝",
                "订单支付成功 实付 ¥299.00 订单号 123456789012",
            ),
            false,
            now,
        ).draft
        assertNotNull(draft)
        assertEquals(29_900L, draft?.amountCents)
        assertEquals("other", draft?.accountId)
        assertEquals(TransactionStatus.SUCCESS, draft?.status)
        assertTrue(draft?.needsConfirmation == true)
    }

    @Test
    fun `irrelevant notifications are rejected before storage`() {
        assertTrue(
            parser.isPotentiallyRelevant(
                snapshot("com.tencent.mm", "微信支付", "付款成功 商户：全家 ¥8.50"),
                allowAllApps = false,
            ),
        )
        assertTrue(
            !parser.isPotentiallyRelevant(
                snapshot("com.taobao.taobao", "淘宝", "限时优惠券 营销活动 立即领取"),
                allowAllApps = false,
            ),
        )
        assertTrue(
            !parser.isPotentiallyRelevant(
                snapshot("com.ss.android.ugc.aweme", "抖音", "李行翔 私信了你：[分享视频]"),
                allowAllApps = true,
            ),
        )
        assertTrue(
            !parser.isPotentiallyRelevant(
                snapshot("com.miui.wallet", "小米智能卡", "钱包 小米智能卡正在运行中"),
                allowAllApps = true,
            ),
        )
    }

    @Test
    fun `missing amount does not create a transaction`() {
        val result = parser.parse(
            snapshot("com.tencent.mm", "微信支付", "付款成功 商户：瑞幸咖啡"),
            false,
            now,
        )
        assertNull(result.draft)
        assertEquals("amount missing", result.reason)
    }

    @Test
    fun `failed and marketing notifications are not expenses`() {
        val failed = parser.parse(
            snapshot("com.eg.android.AlipayGphone", "支付宝", "付款失败 ¥99.00 商户：测试商户"),
            false,
            now,
        ).draft
        assertEquals(TransactionStatus.FAILED, failed?.status)

        val marketing = parser.parse(
            snapshot("com.taobao.taobao", "淘宝", "限时优惠券 营销活动 立即领取"),
            false,
            now,
        )
        assertNull(marketing.draft)
    }

    @Test
    fun `messaging style body is parsed from text lines`() {
        val draft = parser.parse(
            snapshot(
                packageName = "com.tencent.mm",
                title = "微信支付",
                text = "",
                lines = listOf("付款成功", "商户：全家便利店", "¥8.50", "交易单号 420000999"),
            ),
            false,
            now,
        ).draft
        assertEquals(850L, draft?.amountCents)
        assertEquals("全家便利店", draft?.merchant)
    }
}
