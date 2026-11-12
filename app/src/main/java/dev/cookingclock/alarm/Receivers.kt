package dev.cookingclock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.cookingclock.data.ClockController

/** Fired by [AlarmScheduler] when a clock reaches zero. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ClockController.fireExpired(context)
    }
}

/** Restores alarms after a reboot or app update. Clocks that expired meanwhile ring at once. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        ClockController.fireExpired(context)
        ClockController.sync(context)
    }
}

/** Handles the "Stop" actions in the alarm notification. */
class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_CLOCK_ID, -1)
        if (id >= 0) ClockController.stopRinging(context, id) else ClockController.stopAllRinging(context)
    }

    companion object {
        const val EXTRA_CLOCK_ID = "clock_id"
    }
}
