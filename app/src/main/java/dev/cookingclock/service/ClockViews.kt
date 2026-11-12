package dev.cookingclock.service

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import dev.cookingclock.R
import dev.cookingclock.data.ClockState
import dev.cookingclock.data.CookingClock
import dev.cookingclock.data.formatDuration

/**
 * Builds RemoteViews rows for the notification and the widget. Running clocks use a
 * counting-down Chronometer, so the system updates the display without app wake-ups.
 */
object ClockViews {
    enum class Size { NORMAL, COMPACT, TINY, CHIP }

    fun row(
        context: Context,
        clock: CookingClock,
        size: Size,
        now: Long = System.currentTimeMillis(),
        darkText: Boolean = false,
    ): RemoteViews {
        val layout = when (size) {
            Size.NORMAL -> R.layout.clock_row
            Size.COMPACT -> R.layout.clock_row_compact
            Size.TINY -> R.layout.clock_row_tiny
            Size.CHIP -> R.layout.clock_chip
        }
        val rv = RemoteViews(context.packageName, layout)
        rv.setTextViewText(R.id.row_name, clock.name)
        if (clock.color != null) rv.setInt(R.id.row_root, "setBackgroundColor", clock.color)
        if (clock.color != null || darkText) {
            // Pastel backgrounds need dark text, also when the system uses a dark notification theme.
            rv.setTextColor(R.id.row_name, Color.BLACK)
            rv.setTextColor(R.id.row_chrono, Color.BLACK)
            rv.setTextColor(R.id.row_static, Color.BLACK)
        }
        when (clock.state) {
            ClockState.RUNNING -> {
                val base = SystemClock.elapsedRealtime() + (clock.endAt - now)
                rv.setViewVisibility(R.id.row_chrono, View.VISIBLE)
                rv.setViewVisibility(R.id.row_static, View.GONE)
                rv.setChronometer(R.id.row_chrono, base, null, true)
                rv.setChronometerCountDown(R.id.row_chrono, true)
            }
            ClockState.RINGING -> showStatic(rv, context.getString(R.string.times_up))
            ClockState.PAUSED -> showStatic(rv, context.getString(R.string.paused_value, formatDuration(clock.remainingMs)))
            else -> showStatic(rv, formatDuration(clock.remainingAt(now)))
        }
        return rv
    }

    private fun showStatic(rv: RemoteViews, text: String) {
        rv.setViewVisibility(R.id.row_chrono, View.GONE)
        rv.setViewVisibility(R.id.row_static, View.VISIBLE)
        rv.setTextViewText(R.id.row_static, text)
    }

    /** Clocks shown in the notification and widget, ringing first, then by time left. */
    fun activeSorted(clocks: List<CookingClock>, now: Long): List<CookingClock> =
        clocks.filter { it.isActive }.sortedWith(
            compareBy<CookingClock> { if (it.state == ClockState.RINGING) 0 else 1 }
                .thenBy { if (it.state == ClockState.RUNNING) 0 else 1 }
                .thenBy { it.remainingAt(now) }
        )
}
