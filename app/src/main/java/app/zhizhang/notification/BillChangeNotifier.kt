package app.zhizhang.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.zhizhang.MainActivity
import app.zhizhang.R
import app.zhizhang.data.Formats
import app.zhizhang.data.TransactionEntity
import app.zhizhang.data.TxType

/** 自动识别成功后的账单变动通知；只是本地提醒，不上传任何数据。 */
object BillChangeNotifier {
    const val CHANNEL_ID = "auto_capture_bill_change"
    private const val BASE_ID = 9200

    fun notify(context: Context, transaction: TransactionEntity) {
        if (transaction.txType != TxType.EXPENSE && transaction.txType != TxType.INCOME) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "账单变动提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "自动识别支出或收入后提醒你"
            },
        )
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            BASE_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val income = transaction.txType == TxType.INCOME
        val title = if (income) "自动记了一笔收入" else "自动记了一笔支出"
        val name = transaction.merchant.ifBlank { "未命名商户" }
        val amount = Formats.signedMoney(transaction.amountCents, transaction.txType)
        val suffix = if (transaction.needsConfirmation) " · 待确认" else ""
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("$name · $amount$suffix")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(
                BASE_ID + (transaction.id % 1_000).toInt(),
                notification,
            )
        }
    }
}
