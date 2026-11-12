package dev.cookingclock

import android.app.Application
import dev.cookingclock.data.ClockController
import dev.cookingclock.data.Repository
import dev.cookingclock.service.TimerService

class CookingClockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repository.init(this)
        TimerService.createChannels(this)
    }
}
