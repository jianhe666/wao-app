package app.zhizhang.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.zhizhang.ZhizhangApp
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class DailyReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? ZhizhangApp ?: return Result.success()
        if (!app.settings.dailyReminderEnabled.first()) return Result.success()
        DailyReminderNotifier.post(applicationContext)
        return Result.success()
    }
}

object DailyReminderScheduler {
    const val ACTION_REMIND = "app.zhizhang.DAILY_REMINDER_ALARM"
    private const val WORK_NAME = "zhizhang_daily_accounting_reminder"
    private const val ALARM_REQUEST_CODE = 8101

    fun schedule(context: Context, hour: Int, minute: Int) {
        val next = nextTriggerMillis(hour, minute)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val canExact = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            val pending = alarmPending(context)
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    next,
                    pending,
                )
            }.onSuccess {
                // 精确闹钟已接管，移除 WorkManager 周期任务，避免重复通知。
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                return
            }
        }
        // 没有精确闹钟权限时保留 WorkManager 兜底；可能延迟约 1 分钟。
        scheduleWorkFallback(context, hour, minute)
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(alarmPending(context))
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun scheduleWorkFallback(context: Context, hour: Int, minute: Int) {
        val now = ZonedDateTime.now()
        var next = now
            .withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delayMinutes = Duration.between(now, next).toMinutes().coerceAtLeast(1)
        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request,
        )
    }

    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val now = ZonedDateTime.now()
        var next = now
            .withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.toInstant().toEpochMilli()
    }

    private fun alarmPending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, DailyReminderReceiver::class.java).setAction(ACTION_REMIND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
