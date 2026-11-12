package dev.cookingclock.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Process-wide store for all app data, persisted as JSON in shared preferences. */
object Repository {
    private const val PREFS = "cooking_clock"
    private const val KEY_STATE = "state"

    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_STATE, null)
        var loaded = if (json != null) runCatching { decode(JSONObject(json)) }.getOrNull() else null
        if (loaded == null) {
            loaded = AppState(profiles = listOf(AppState.defaultProfile()))
        }
        if (loaded.profiles.none { it.id == AppState.DEFAULT_PROFILE_ID }) {
            loaded = loaded.copy(profiles = listOf(AppState.defaultProfile()) + loaded.profiles)
        }
        _state.value = loaded
    }

    val current: AppState get() = _state.value

    @Synchronized
    fun update(transform: (AppState) -> AppState): AppState {
        val next = transform(_state.value)
        if (next != _state.value) {
            _state.value = next
            prefs.edit().putString(KEY_STATE, encode(next).toString()).apply()
        }
        return next
    }

    private fun encode(s: AppState): JSONObject = JSONObject().apply {
        put("nextId", s.nextId)
        put("keepScreenOn", s.keepScreenOn)
        put("names", JSONArray(s.names))
        put("clocks", JSONArray().apply {
            s.clocks.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("durationMs", c.durationMs)
                    put("remainingMs", c.remainingMs)
                    put("state", c.state.name)
                    put("endAt", c.endAt)
                    c.color?.let { put("color", it) }
                    c.profileId?.let { put("profileId", it) }
                })
            }
        })
        put("profiles", JSONArray().apply {
            s.profiles.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    p.soundUri?.let { put("soundUri", it) }
                    put("vibrate", p.vibrate)
                })
            }
        })
    }

    private fun decode(o: JSONObject): AppState {
        val names = o.optJSONArray("names")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
        val clocks = o.optJSONArray("clocks")?.let { a ->
            List(a.length()) { i ->
                val c = a.getJSONObject(i)
                CookingClock(
                    id = c.getInt("id"),
                    name = c.getString("name"),
                    durationMs = c.getLong("durationMs"),
                    remainingMs = c.getLong("remainingMs"),
                    state = runCatching { ClockState.valueOf(c.getString("state")) }.getOrDefault(ClockState.IDLE),
                    endAt = c.optLong("endAt"),
                    color = if (c.has("color")) c.getInt("color") else null,
                    profileId = if (c.has("profileId")) c.getString("profileId") else null,
                )
            }
        } ?: emptyList()
        val profiles = o.optJSONArray("profiles")?.let { a ->
            List(a.length()) { i ->
                val p = a.getJSONObject(i)
                AlarmProfile(
                    id = p.getString("id"),
                    name = p.getString("name"),
                    soundUri = if (p.has("soundUri")) p.getString("soundUri") else null,
                    vibrate = p.optBoolean("vibrate", true),
                )
            }
        } ?: emptyList()
        return AppState(
            clocks = clocks,
            names = names,
            profiles = profiles,
            keepScreenOn = o.optBoolean("keepScreenOn"),
            nextId = o.optInt("nextId", (clocks.maxOfOrNull { it.id } ?: 0) + 1),
        )
    }
}
