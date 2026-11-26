package com.example.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.RRReminderApp
import com.example.core.database.RRDatabase
import com.example.widgets.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootAndTimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action in SUPPORTED_ACTIONS) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val appContext = context.applicationContext
                    val container = (appContext as? RRReminderApp)?.container
                    val db = container?.database ?: RRDatabase.getInstance(appContext)
                    val scheduler = container?.scheduler ?: ReminderAlarmScheduler(appContext)
                    scheduler.rescheduleAll(db)
                    WidgetUpdateHelper.updateAllWidgets(appContext)
                } catch (t: Throwable) {
                    Log.e("BootAndTimeReceiver", "Failed processing broadcast $action", t)
                } finally {
                    runCatching { pendingResult.finish() }
                }
            }
        }
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
