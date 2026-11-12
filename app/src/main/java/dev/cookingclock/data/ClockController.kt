package dev.cookingclock.data

import android.content.Context
import dev.cookingclock.alarm.AlarmScheduler
import dev.cookingclock.service.TimerService
import dev.cookingclock.widget.ClockWidgetProvider
import java.util.UUID

/**
 * All state changes go through this object. After every change, [sync] reschedules
 * alarms, starts the foreground service when needed and refreshes the widget.
 */
object ClockController {
    private const val MINUTE = 60_000L

    private fun now() = System.currentTimeMillis()

    private fun change(context: Context, transform: (AppState) -> AppState) {
        Repository.init(context)
        val before = Repository.current
        val after = Repository.update(transform)
        val removed = before.clocks.map { it.id } - after.clocks.map { it.id }.toSet()
        sync(context, removed)
    }

    fun sync(context: Context, removedIds: Collection<Int> = emptyList()) {
        Repository.init(context)
        val state = Repository.current
        AlarmScheduler.sync(context, state.clocks, removedIds)
        if (state.clocks.any { it.state == ClockState.RUNNING || it.state == ClockState.RINGING }) {
            TimerService.start(context)
        }
        ClockWidgetProvider.updateAll(context)
    }

    private fun AppState.mapClock(id: Int, f: (CookingClock) -> CookingClock) =
        copy(clocks = clocks.map { if (it.id == id) f(it) else it })

    fun addClock(context: Context, name: String, minutes: Int = 5) = change(context) { s ->
        val ms = minutes * MINUTE
        s.copy(
            nextId = s.nextId + 1,
            clocks = s.clocks + CookingClock(s.nextId, name.trim(), ms, ms, ClockState.IDLE),
            names = rememberName(s.names, name),
        )
    }

    fun rename(context: Context, id: Int, name: String) = change(context) { s ->
        s.mapClock(id) { it.copy(name = name.trim()) }.copy(names = rememberName(s.names, name))
    }

    private fun rememberName(names: List<String>, name: String): List<String> {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || names.any { it.equals(trimmed, ignoreCase = true) }) return names
        return (names + trimmed).sortedBy { it.lowercase() }
    }

    fun deleteName(context: Context, name: String) = change(context) { s -> s.copy(names = s.names - name) }

    /** Start a stopped clock, pause a running one, or silence a ringing one. */
    fun toggle(context: Context, id: Int) = change(context) { s ->
        val t = now()
        s.mapClock(id) { c ->
            when (c.state) {
                ClockState.IDLE, ClockState.PAUSED ->
                    if (c.remainingMs > 0) c.copy(state = ClockState.RUNNING, endAt = t + c.remainingMs) else c
                ClockState.RUNNING -> c.copy(state = ClockState.PAUSED, remainingMs = c.remainingAt(t))
                ClockState.RINGING -> c.copy(state = ClockState.FINISHED, remainingMs = 0)
                ClockState.FINISHED -> c
            }
        }
    }

    fun stopRinging(context: Context, id: Int) = change(context) { s ->
        s.mapClock(id) { if (it.state == ClockState.RINGING) it.copy(state = ClockState.FINISHED, remainingMs = 0) else it }
    }

    fun reset(context: Context, id: Int) = change(context) { s ->
        s.mapClock(id) { it.copy(state = ClockState.IDLE, remainingMs = it.durationMs, endAt = 0) }
    }

    /** Add [minutes] (may be negative) to the current value of a clock. */
    fun adjust(context: Context, id: Int, minutes: Int) = change(context) { s ->
        val t = now()
        val delta = minutes * MINUTE
        s.mapClock(id) { c ->
            when (c.state) {
                ClockState.RUNNING -> {
                    val remaining = (c.remainingAt(t) + delta).coerceAtLeast(0)
                    c.copy(endAt = t + remaining)
                }
                ClockState.IDLE -> {
                    val v = (c.remainingMs + delta).coerceAtLeast(0)
                    c.copy(remainingMs = v, durationMs = v)
                }
                ClockState.PAUSED -> c.copy(remainingMs = (c.remainingMs + delta).coerceAtLeast(0))
                // Adding time to a ringing clock acts as a snooze.
                ClockState.RINGING ->
                    if (delta > 0) c.copy(state = ClockState.RUNNING, endAt = t + delta) else c
                ClockState.FINISHED ->
                    if (delta > 0) c.copy(state = ClockState.PAUSED, remainingMs = delta) else c
            }
        }
    }

    fun setColor(context: Context, id: Int, color: Int?) =
        change(context) { s -> s.mapClock(id) { it.copy(color = color) } }

    fun setProfile(context: Context, id: Int, profileId: String?) =
        change(context) { s -> s.mapClock(id) { it.copy(profileId = profileId) } }

    fun delete(context: Context, id: Int) =
        change(context) { s -> s.copy(clocks = s.clocks.filterNot { it.id == id }) }

    fun clearAll(context: Context) = change(context) { s -> s.copy(clocks = emptyList()) }

    fun stopAllRinging(context: Context) = change(context) { s ->
        s.copy(clocks = s.clocks.map {
            if (it.state == ClockState.RINGING) it.copy(state = ClockState.FINISHED, remainingMs = 0) else it
        })
    }

    /** Move every running clock whose time is up into the ringing state. */
    fun fireExpired(context: Context) {
        Repository.init(context)
        val t = now()
        if (Repository.current.clocks.none { it.state == ClockState.RUNNING && it.endAt <= t }) return
        change(context) { s ->
            s.copy(clocks = s.clocks.map {
                if (it.state == ClockState.RUNNING && it.endAt <= t) it.copy(state = ClockState.RINGING, remainingMs = 0) else it
            })
        }
    }

    fun setKeepScreenOn(context: Context, on: Boolean) = change(context) { s -> s.copy(keepScreenOn = on) }

    fun saveProfile(context: Context, profile: AlarmProfile) = change(context) { s ->
        if (s.profiles.any { it.id == profile.id }) {
            s.copy(profiles = s.profiles.map { if (it.id == profile.id) profile else it })
        } else {
            s.copy(profiles = s.profiles + profile)
        }
    }

    fun newProfileId(): String = UUID.randomUUID().toString()

    fun deleteProfile(context: Context, id: String) = change(context) { s ->
        if (id == AppState.DEFAULT_PROFILE_ID) s
        else s.copy(
            profiles = s.profiles.filterNot { it.id == id },
            clocks = s.clocks.map { if (it.profileId == id) it.copy(profileId = null) else it },
        )
    }
}
