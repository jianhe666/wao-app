package app.zhizhang.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.zhizhang.MainActivity
import app.zhizhang.R
import app.zhizhang.ZhizhangApp
import app.zhizhang.data.Formats
import app.zhizhang.data.TxType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId

/** 桌面小组件：本月支出和预算剩余，点击打开 App。 */
class BillWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                refreshWidgets(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, BillWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                refreshWidgets(context, manager, ids)
            }
        }

        private suspend fun refreshWidgets(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
        ) {
            val app = context.applicationContext as? ZhizhangApp ?: return
            val zone = ZoneId.systemDefault()
            val month = YearMonth.now(zone)
            val transactions = app.repository.observeMonth(month, zone).first()
            val budget = app.settings.budgetCents.first()
            val expense = transactions
                .filter { it.txType == TxType.EXPENSE }
                .sumOf { it.amountCents }
            val remaining = (budget - expense).coerceAtLeast(0L)
            val budgetText = if (budget > 0L) {
                "预算剩余 ${Formats.money(remaining)}"
            } else {
                "本月还没有设置预算"
            }
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_summary)
                views.setTextViewText(R.id.widget_month, "${month.year}年${month.monthValue}月")
                views.setTextViewText(R.id.widget_expense, Formats.money(expense))
                views.setTextViewText(R.id.widget_budget, budgetText)
                val intent = Intent(context, MainActivity::class.java)
                val pending = PendingIntent.getActivity(
                    context,
                    id,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widget_root, pending)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
