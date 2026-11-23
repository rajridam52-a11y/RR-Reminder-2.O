package com.example.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.core.database.RRDatabase
import com.example.core.datastore.UserPreferencesRepository
import com.example.core.network.WeatherCodeInterpreter
import com.example.core.notifications.ScheduleCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WidgetUpdateHelper {
    fun updateAllWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val providers = listOf(
            NextReminderWidgetProvider::class.java,
            ClockWidgetProvider::class.java,
            WeatherWidgetProvider::class.java,
            QuickAddWidgetProvider::class.java,
            TimerWidgetProvider::class.java,
        )
        providers.forEach { cls ->
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, cls).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    internal fun launchMainIntent(context: Context, destination: String? = null, reqCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (destination != null) putExtra("navigate_to", destination)
        }
        return PendingIntent.getActivity(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class NextReminderWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = RRDatabase.getInstance(context)
                val prefs = UserPreferencesRepository(context).getSnapshot()
                val next = db.reminderDao().getAllActiveSync().firstOrNull()
                appWidgetIds.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_next_reminder)
                    if (next != null) {
                        views.setTextViewText(R.id.widget_reminder_title, next.title)
                        val timeText = "${ScheduleCalculator.formatDate(next.triggerAtMillis, prefs.dateFormatStyle.pattern)} • ${ScheduleCalculator.formatTime(next.triggerAtMillis, prefs.use24Hour)}"
                        views.setTextViewText(R.id.widget_reminder_time, timeText)
                    } else {
                        views.setTextViewText(R.id.widget_reminder_title, "All caught up")
                        views.setTextViewText(R.id.widget_reminder_time, "Tap to add a reminder")
                    }
                    views.setOnClickPendingIntent(
                        R.id.widget_root,
                        WidgetUpdateHelper.launchMainIntent(context, "reminders", 501),
                    )
                    manager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class ClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = RRDatabase.getInstance(context)
                val citiesCount = db.worldClockDao().getAllSync().size
                appWidgetIds.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_clock)
                    views.setTextViewText(R.id.widget_clock_sub, "$citiesCount world cities tracked")
                    views.setOnClickPendingIntent(
                        R.id.widget_clock_root,
                        WidgetUpdateHelper.launchMainIntent(context, "worldclock", 502),
                    )
                    manager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class WeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cache = RRDatabase.getInstance(context).weatherCacheDao().getCacheSync()
                appWidgetIds.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_weather)
                    if (cache != null) {
                        views.setTextViewText(R.id.widget_weather_city, cache.cityName)
                        views.setTextViewText(R.id.widget_weather_temp, "${cache.currentTempC.toInt()}°C")
                        views.setTextViewText(
                            R.id.widget_weather_cond,
                            WeatherCodeInterpreter.describe(cache.weatherCode),
                        )
                    } else {
                        views.setTextViewText(R.id.widget_weather_city, "RR Weather")
                        views.setTextViewText(R.id.widget_weather_temp, "--°")
                        views.setTextViewText(R.id.widget_weather_cond, "Tap to select city")
                    }
                    views.setOnClickPendingIntent(
                        R.id.widget_weather_root,
                        WidgetUpdateHelper.launchMainIntent(context, "weather", 503),
                    )
                    manager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class QuickAddWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_add)
            views.setOnClickPendingIntent(
                R.id.widget_quick_add_root,
                WidgetUpdateHelper.launchMainIntent(context, "quick_add", 504),
            )
            manager.updateAppWidget(id, views)
        }
    }
}

class TimerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timers = RRDatabase.getInstance(context).timerDao().getAllSync()
                val active = timers.firstOrNull { it.isRunning } ?: timers.firstOrNull()
                appWidgetIds.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_timer)
                    if (active != null) {
                        val remaining = if (active.isRunning && active.endEpochMillis != null) {
                            (active.endEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                        } else {
                            active.remainingMillis
                        }
                        views.setTextViewText(R.id.widget_timer_label, active.label)
                        views.setTextViewText(
                            R.id.widget_timer_status,
                            if (active.isRunning) "Running • ${ScheduleCalculator.formatDurationMillis(remaining)}"
                            else "Paused • ${ScheduleCalculator.formatDurationMillis(remaining)}",
                        )
                    } else {
                        views.setTextViewText(R.id.widget_timer_label, "No active timer")
                        views.setTextViewText(R.id.widget_timer_status, "Tap to start countdown")
                    }
                    views.setOnClickPendingIntent(
                        R.id.widget_timer_root,
                        WidgetUpdateHelper.launchMainIntent(context, "timer", 505),
                    )
                    manager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
