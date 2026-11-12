package dev.cookingclock.data

enum class ClockState { IDLE, RUNNING, PAUSED, RINGING, FINISHED }

/**
 * A countdown clock.
 *
 * [durationMs] is the value the clock returns to on reset.
 * [remainingMs] is the time left while the clock is not running.
 * [endAt] is the wall-clock time (epoch millis) at which a running clock reaches zero.
 */
data class CookingClock(
    val id: Int,
    val name: String,
    val durationMs: Long,
    val remainingMs: Long,
    val state: ClockState,
    val endAt: Long = 0L,
    val color: Int? = null,
    val profileId: String? = null,
) {
    fun remainingAt(now: Long): Long = when (state) {
        ClockState.RUNNING -> (endAt - now).coerceAtLeast(0L)
        ClockState.RINGING, ClockState.FINISHED -> 0L
        else -> remainingMs
    }

    /** Running, paused or ringing clocks are shown in the notification and widget. */
    val isActive: Boolean
        get() = state == ClockState.RUNNING || state == ClockState.PAUSED || state == ClockState.RINGING
}

data class AlarmProfile(
    val id: String,
    val name: String,
    /** Ringtone URI; null means silent. */
    val soundUri: String?,
    val vibrate: Boolean,
)

data class AppState(
    val clocks: List<CookingClock> = emptyList(),
    val names: List<String> = emptyList(),
    val profiles: List<AlarmProfile> = emptyList(),
    val keepScreenOn: Boolean = false,
    val nextId: Int = 1,
) {
    fun profileFor(clock: CookingClock): AlarmProfile =
        profiles.firstOrNull { it.id == clock.profileId }
            ?: profiles.firstOrNull { it.id == DEFAULT_PROFILE_ID }
            ?: defaultProfile()

    companion object {
        const val DEFAULT_PROFILE_ID = "default"

        fun defaultProfile() = AlarmProfile(
            id = DEFAULT_PROFILE_ID,
            name = "Default",
            soundUri = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI.toString(),
            vibrate = true,
        )
    }
}

/** Background colors a clock can use. The first entry, null, means "unset". */
val CLOCK_COLORS: List<Int?> = listOf(
    null,
    0xFFFFCDD2.toInt(), // red
    0xFFFFE0B2.toInt(), // orange
    0xFFFFF9C4.toInt(), // yellow
    0xFFC8E6C9.toInt(), // green
    0xFFB2EBF2.toInt(), // cyan
    0xFFBBDEFB.toInt(), // blue
    0xFFD1C4E9.toInt(), // purple
    0xFFF8BBD0.toInt(), // pink
    0xFFD7CCC8.toInt(), // brown
)

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms + 999) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
