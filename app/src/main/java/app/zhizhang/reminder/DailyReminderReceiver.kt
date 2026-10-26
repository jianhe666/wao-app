package app.zhizhang.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.zhizhang.ZhizhangApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DailyReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? ZhizhangApp ?: return
        when (intent.action) {
            DailyReminderScheduler.ACTION_REMIND -> {
                val result = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        if (app.settings.dailyReminderEnabled.first()) {
                            DailyReminderNotifier.post(context)
                            val hour = app.settings.dailyReminderHour.first()
                            val minute = app.settings.dailyReminderMinute.first()
                            DailyReminderScheduler.schedule(context, hour, minute)
                        }
                    } finally {
                        result.finish()
                    }
                }
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val result = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        if (app.settings.dailyReminderEnabled.first()) {
                            DailyReminderScheduler.schedule(
                                context,
                                app.settings.dailyReminderHour.first(),
                                app.settings.dailyReminderMinute.first(),
                            )
                        }
                    } finally {
                        result.finish()
                    }
                }
            }
        }
    }
}
