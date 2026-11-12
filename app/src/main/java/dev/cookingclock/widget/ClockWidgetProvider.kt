package dev.cookingclock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import dev.cookingclock.MainActivity
import dev.cookingclock.R
import dev.cookingclock.data.Repository
import dev.cookingclock.service.ClockViews

/** Home screen widget listing the active clocks with live countdowns. */
class ClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context)
    }

    companion object {
        private const val MAX_ROWS = 12

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
            if (ids.isEmpty()) return
            Repository.init(context)
            val now = System.currentTimeMillis()
            val clocks = ClockViews.activeSorted(Repository.current.clocks, now)

            val views = RemoteViews(context.packageName, R.layout.widget)
            views.removeAllViews(R.id.widget_rows)
            val size = if (clocks.size <= 4) ClockViews.Size.NORMAL else ClockViews.Size.COMPACT
            clocks.take(MAX_ROWS).forEach {
                views.addView(R.id.widget_rows, ClockViews.row(context, it, size, now, darkText = true))
            }
            views.setViewVisibility(R.id.widget_empty, if (clocks.isEmpty()) View.VISIBLE else View.GONE)
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(ids, views)
        }
    }
}
