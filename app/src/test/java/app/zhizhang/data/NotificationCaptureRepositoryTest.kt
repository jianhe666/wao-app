package app.zhizhang.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.zhizhang.domain.CategoryClassifier
import app.zhizhang.notification.NotificationCaptureParser
import app.zhizhang.notification.NotificationDraft
import app.zhizhang.notification.NotificationSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationCaptureRepositoryTest {

    @Test
    fun `same external id does not create duplicate and refund links original`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = TransactionRepository(database, SettingsRepository(context))
            val zone = ZoneId.of("Asia/Shanghai")
            val now = LocalDateTime.of(2026, 10, 24, 12, 0)
            val parser = NotificationCaptureParser(CategoryClassifier(emptyMap()), zone)
            val snapshot = NotificationSnapshot(
                packageName = "com.tencent.mm",
                appLabel = "微信",
                notificationKey = "com.tencent.mm:1",
                title = "微信支付",
                text = "付款成功 商户：滴滴出行 ¥26.40 交易单号 TX-10001",
                postedAt = now.atZone(zone).toInstant().toEpochMilli(),
            )
            val draft = parser.parse(snapshot, false, now).draft!!
            val firstResult = repository.captureNotificationResult(draft, 1L)
            val secondResult = repository.captureNotificationResult(draft, 2L)
            val first = firstResult.transactionId
            val second = secondResult.transactionId
            assertNotNull(first)
            assertEquals(first, second)
            assertTrue(firstResult.inserted)
            assertTrue(!secondResult.inserted)
            assertEquals(1, repository.all().size)

            val refundDraft = draft.copy(
                type = TxType.INCOME,
                status = TransactionStatus.REFUNDED,
                categoryId = "refund",
                externalId = "TX-10001",
            )
            val failedDraft = draft.copy(status = TransactionStatus.FAILED)
            val failedId = repository.captureNotification(failedDraft, 20L)
            assertEquals(first, failedId)
            var originalAfterFailure = repository.all().first { it.id == first }
            assertEquals(TransactionStatus.FAILED.name, originalAfterFailure.transactionStatus)

            val refundId = repository.captureNotification(refundDraft, 3L)
            assertNotNull(refundId)
            val all = repository.all()
            assertEquals(2, all.size)
            val original = all.first { it.id == first }
            val refund = all.first { it.id == refundId }
            assertEquals(TransactionStatus.REFUNDED.name, original.transactionStatus)
            assertEquals(first, refund.refundOfId)
            assertEquals(TxType.INCOME.name, refund.type)
            assertNotEquals(first, refundId)
        } finally {
            database.close()
        }
    }


    @Test
    fun `bill import skips record already captured from notification`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = TransactionRepository(database, SettingsRepository(context))
            val amount = 3_850L
            val occurredAt = System.currentTimeMillis()
            val draft = app.zhizhang.notification.NotificationDraft(
                amountCents = amount,
                type = TxType.EXPENSE,
                merchant = "美团外卖",
                categoryId = "food",
                accountId = "alipay",
                occurredAt = occurredAt,
                sourcePackage = "com.eg.android.AlipayGphone",
                externalId = "202610230001",
                status = TransactionStatus.SUCCESS,
                confidence = 0.95f,
                needsConfirmation = false,
                rawText = "支付宝 交易成功 38.50",
            )
            repository.captureNotification(draft, 1L)
            val imported = repository.importBills(
                listOf(
                    ImportSelection(
                        bill = app.zhizhang.domain.ParsedBill(
                            amountCents = amount,
                            type = TxType.EXPENSE,
                            occurredAt = occurredAt,
                            merchant = "美团外卖",
                            categoryId = "food",
                            accountId = "alipay",
                            note = "",
                            rawText = "csv row",
                            confidence = 0.95f,
                            fingerprint = "csv-fingerprint",
                            externalId = "202610230001",
                        ),
                        categoryId = "food",
                    ),
                ),
            )
            assertEquals(0, imported)
            assertEquals(1, repository.all().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun `pending capture is persisted and appears in review`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = TransactionRepository(database, SettingsRepository(context))
            val draft = NotificationDraft(
                amountCents = 1_000,
                type = TxType.EXPENSE,
                merchant = "",
                categoryId = "other_expense",
                accountId = "other",
                occurredAt = System.currentTimeMillis(),
                sourcePackage = "com.taobao.taobao",
                externalId = "ORDER-1",
                status = TransactionStatus.PENDING,
                confidence = 0.55f,
                needsConfirmation = true,
                rawText = "订单 100 元",
            )
            repository.captureNotification(draft, 10L)
            assertEquals(1, repository.observeNeedsReviewCount().first())
            assertEquals(1, repository.markAllReviewed())
            assertEquals(0, repository.observeNeedsReviewCount().first())
            val event = NotificationEventEntity(
                packageName = "com.taobao.taobao",
                appLabel = "淘宝",
                notificationKey = "key-1",
                title = "订单",
                body = "订单 100 元",
                postedAt = 1_000L,
                contentHash = "hash",
            )
            val dao = database.notificationEventDao()
            assertTrue(dao.insertIgnore(event) > 0L)
            assertEquals(-1L, dao.insertIgnore(event.copy(id = 0L)))
        } finally {
            database.close()
        }
    }
}
