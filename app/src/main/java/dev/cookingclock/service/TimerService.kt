package dev.cookingclock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dev.cookingclock.MainActivity
import dev.cookingclock.R
import dev.cookingclock.alarm.Ringer
import dev.cookingclock.alarm.StopAlarmReceiver
import dev.cookingclock.data.AppState
import dev.cookingclock.data.ClockController
import dev.cookingclock.data.ClockState
import dev.cookingclock.data.Repository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that runs while any clock is running or ringing. It keeps the
 * ongoing notification current, plays alarms, and checks every second for expired
 * clocks as a backup to the exact alarms.
 */
class TimerService : LifecycleService() {
    private lateinit var ringer: Ringer
    private lateinit var nm: NotificationManager
    private var lastRingingIds: Set<Int> = emptySet()

    override fun onCreate() {
        super.onCreate()
        Repository.init(this)
        ringer = Ringer(this)
        nm = getSystemService(NotificationManager::class.java)
        createChannels(this)
        goForeground(Repository.current)
        lifecycleScope.launch {
            Repository.state.collect { render(it) }
        }
        lifecycleScope.launch {
            while (true) {
                ClockController.fireExpired(this@TimerService)
                delay(1000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        goForeground(Repository.current)
        return START_STICKY
    }

    override fun onDestroy() {
        ringer.stop()
        nm.cancel(ALARM_NOTIFICATION_ID)
        super.onDestroy()
    }

    private fun goForeground(state: AppState) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, ONGOING_NOTIFICATION_ID, buildOngoing(state), type)
    }

    private fun render(state: AppState) {
        val running = state.clocks.any { it.state == ClockState.RUNNING }
        val ringing = state.clocks.filter { it.state == ClockState.RINGING }
        if (!running && ringing.isEmpty()) {
            ringer.stop()
            nm.cancel(ALARM_NOTIFICATION_ID)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        nm.notify(ONGOING_NOTIFICATION_ID, buildOngoing(state))

        val ringingIds = ringing.map { it.id }.toSet()
        if (ringing.isEmpty()) {
            ringer.stop()
            nm.cancel(ALARM_NOTIFICATION_ID)
        } else {
            // The clock that finished most recently decides which alarm profile plays.
            val latest = ringing.maxBy { it.endAt }
            ringer.play(state.profileFor(latest))
            if (ringingIds != lastRingingIds) nm.notify(ALARM_NOTIFICATION_ID, buildAlarm(state))
        }
        lastRingingIds = ringingIds
    }

    private fun openAppIntent(ringing: Boolean): PendingIntent = PendingIntent.getActivity(
        this, if (ringing) 1 else 0,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_RINGING, ringing),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildOngoing(state: AppState): Notification {
        val now = System.currentTimeMillis()
        val clocks = ClockViews.activeSorted(state.clocks, now)
        val running = clocks.filter { it.state == ClockState.RUNNING }
        val title = resources.getQuantityString(R.plurals.clocks_running, running.size, running.size)

        // Collapsed view: the first clocks in one line.
        val collapsed = RemoteViews(packageName, R.layout.notification_collapsed)
        clocks.take(COLLAPSED_MAX).forEach {
            collapsed.addView(R.id.collapsed_rows, ClockViews.row(this, it, ClockViews.Size.CHIP, now))
        }
        if (clocks.size > COLLAPSED_MAX) {
            collapsed.setViewVisibility(R.id.collapsed_more, View.VISIBLE)
            collapsed.setTextViewText(R.id.collapsed_more, "+${clocks.size - COLLAPSED_MAX}")
        }

        // Expanded view: every clock. More clocks means smaller rows, then two columns.
        val expanded = RemoteViews(packageName, R.layout.notification_expanded)
        val size = when {
            clocks.size <= 3 -> ClockViews.Size.NORMAL
            clocks.size <= 6 -> ClockViews.Size.COMPACT
            else -> ClockViews.Size.TINY
        }
        val twoColumns = clocks.size > 6
        clocks.forEachIndexed { i, c ->
            val col = if (twoColumns && i >= (clocks.size + 1) / 2) R.id.column_2 else R.id.column_1
            expanded.addView(col, ClockViews.row(this, c, size, now))
        }
        expanded.setViewVisibility(R.id.column_2, if (twoColumns) View.VISIBLE else View.GONE)

        return NotificationCompat.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(false))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .build()
    }

    private fun buildAlarm(state: AppState): Notification {
        val ringing = state.clocks.filter { it.state == ClockState.RINGING }
        val names = ringing.joinToString(", ") { it.name }
        val stopAll = PendingIntent.getBroadcast(
            this, 0, Intent(this, StopAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.times_up))
            .setContentText(names)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(openAppIntent(true))
            .setFullScreenIntent(openAppIntent(true), true)
            .addAction(R.drawable.ic_stop, getString(R.string.stop), stopAll)
            .build()
    }

    companion object {
        const val CHANNEL_RUNNING = "running"
        const val CHANNEL_ALARM = "alarm"
        private const val ONGOING_NOTIFICATION_ID = 1
        private const val ALARM_NOTIFICATION_ID = 2
        private const val COLLAPSED_MAX = 3

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
            } catch (e: Exception) {
                // Starting from the background can be refused. Exact alarms still fire,
                // and the alarm receiver is allowed to start the service.
            }
        }

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_RUNNING, context.getString(R.string.channel_running), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALARM, context.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        // The service plays the sound and vibration from the clock's alarm profile.
                        setSound(null, null)
                        enableVibration(false)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    }
            )
        }
    }
}
