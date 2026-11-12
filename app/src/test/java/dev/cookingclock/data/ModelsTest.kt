package dev.cookingclock.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    private fun clock(state: ClockState, remaining: Long = 60_000, endAt: Long = 0, profileId: String? = null) =
        CookingClock(id = 1, name = "Rice", durationMs = 300_000, remainingMs = remaining, state = state, endAt = endAt, profileId = profileId)

    @Test
    fun formatDurationUsesMinutesAndSeconds() {
        assertEquals("00:00", formatDuration(0))
        assertEquals("05:00", formatDuration(300_000))
        assertEquals("59:59", formatDuration(3_599_000))
    }

    @Test
    fun formatDurationAddsHoursWhenNeeded() {
        assertEquals("1:00:00", formatDuration(3_600_000))
        assertEquals("2:05:09", formatDuration(7_509_000))
    }

    @Test
    fun formatDurationRoundsPartialSecondsUp() {
        // A clock with 0.2 s left still shows one second, so it never reads 00:00 while running.
        assertEquals("00:01", formatDuration(200))
        assertEquals("00:02", formatDuration(1_001))
    }

    @Test
    fun runningClockCountsDownFromEndTime() {
        val c = clock(ClockState.RUNNING, endAt = 10_000)
        assertEquals(4_000, c.remainingAt(6_000))
        assertEquals(0, c.remainingAt(12_000))
    }

    @Test
    fun stoppedClocksKeepTheirValue() {
        assertEquals(60_000, clock(ClockState.PAUSED).remainingAt(999_999))
        assertEquals(60_000, clock(ClockState.IDLE).remainingAt(999_999))
        assertEquals(0, clock(ClockState.RINGING).remainingAt(0))
        assertEquals(0, clock(ClockState.FINISHED).remainingAt(0))
    }

    @Test
    fun activeStates() {
        assertTrue(clock(ClockState.RUNNING).isActive)
        assertTrue(clock(ClockState.PAUSED).isActive)
        assertTrue(clock(ClockState.RINGING).isActive)
        assertFalse(clock(ClockState.IDLE).isActive)
        assertFalse(clock(ClockState.FINISHED).isActive)
    }

    @Test
    fun profileForFallsBackToDefault() {
        val default = AlarmProfile(AppState.DEFAULT_PROFILE_ID, "Default", "sound", true)
        val quiet = AlarmProfile("quiet", "Quiet", null, false)
        val state = AppState(profiles = listOf(default, quiet))
        assertEquals(quiet, state.profileFor(clock(ClockState.IDLE, profileId = "quiet")))
        assertEquals(default, state.profileFor(clock(ClockState.IDLE, profileId = null)))
        assertEquals(default, state.profileFor(clock(ClockState.IDLE, profileId = "deleted")))
    }
}
