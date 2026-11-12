package dev.cookingclock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cookingclock.data.ClockController
import dev.cookingclock.data.ClockState
import dev.cookingclock.data.Repository
import dev.cookingclock.ui.AlarmProfilesScreen
import dev.cookingclock.ui.MainScreen
import dev.cookingclock.ui.SettingsScreen
import dev.cookingclock.ui.theme.CookingClockTheme

/** The screens of [MainActivity]. There is no navigation library. */
private enum class Screen { MAIN, SETTINGS, ALARM_PROFILES }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repository.init(this)
        handleRingingIntent(intent)
        requestNotificationPermission()
        // Catch up on anything that expired while no process was running.
        ClockController.fireExpired(this)
        ClockController.sync(this)

        setContent {
            CookingClockTheme {
                val state by Repository.state.collectAsStateWithLifecycle()
                var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }

                LaunchedEffect(state.keepScreenOn) {
                    if (state.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                val ringing = state.clocks.any { it.state == ClockState.RINGING }
                LaunchedEffect(ringing) {
                    if (!ringing) showOverLockScreen(false)
                }

                when (screen) {
                    Screen.MAIN -> MainScreen(state = state, onOpenSettings = { screen = Screen.SETTINGS })
                    Screen.SETTINGS -> {
                        BackHandler { screen = Screen.MAIN }
                        SettingsScreen(
                            onBack = { screen = Screen.MAIN },
                            onOpenAlarmProfiles = { screen = Screen.ALARM_PROFILES },
                        )
                    }
                    Screen.ALARM_PROFILES -> {
                        BackHandler { screen = Screen.SETTINGS }
                        AlarmProfilesScreen(state = state, onBack = { screen = Screen.SETTINGS })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRingingIntent(intent)
    }

    private fun handleRingingIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_RINGING, false) == true) showOverLockScreen(true)
    }

    private fun showOverLockScreen(show: Boolean) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(show)
            setTurnScreenOn(show)
        } else {
            @Suppress("DEPRECATION")
            val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (show) window.addFlags(flags) else window.clearFlags(flags)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    companion object {
        const val EXTRA_RINGING = "ringing"
    }
}
