package dev.cookingclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.cookingclock.MainActivity
import dev.cookingclock.data.ClockState
import dev.cookingclock.data.CookingClock

/** Schedules one exact alarm per running clock with [AlarmManager.setAlarmClock], which fires even in Doze. */
object AlarmScheduler {
    private fun alarmIntent(context: Context, id: Int, flags: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).setAction("dev.cookingclock.ALARM.$id")
        return PendingIntent.getBroadcast(context, id, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    fun sync(context: Context, clocks: List<CookingClock>, removedIds: Collection<Int>) {
        val am = context.getSystemService(AlarmManager::class.java)
        for (id in removedIds) cancel(context, am, id)
        for (c in clocks) {
            if (c.state == ClockState.RUNNING) schedule(context, am, c) else cancel(context, am, c.id)
        }
    }

    private fun cancel(context: Context, am: AlarmManager, id: Int) {
        alarmIntent(context, id, PendingIntent.FLAG_NO_CREATE)?.let {
            am.cancel(it)
            it.cancel()
        }
    }

    private fun schedule(context: Context, am: AlarmManager, c: CookingClock) {
        val pi = alarmIntent(context, c.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(c.endAt, show), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.endAt, pi)
        }
    }
}
