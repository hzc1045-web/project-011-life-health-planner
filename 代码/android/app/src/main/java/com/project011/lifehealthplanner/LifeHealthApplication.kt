package com.project011.lifehealthplanner

import android.app.Application
import com.project011.lifehealthplanner.calendar.CalendarManager
import com.project011.lifehealthplanner.data.AppRepository
import com.project011.lifehealthplanner.data.local.AppDatabase
import com.project011.lifehealthplanner.health.HealthConnectManager
import com.project011.lifehealthplanner.notifications.ReminderWorker
import com.project011.lifehealthplanner.security.SecurePreferences

class LifeHealthApplication : Application() {
    lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.build(this)
        repository = AppRepository(
            context = this,
            database = database,
            securePreferences = SecurePreferences(this),
            healthConnect = HealthConnectManager(this),
            calendar = CalendarManager(this),
        )
        ReminderWorker.createChannel(this)
        ReminderWorker.scheduleDailyReview(this)
    }
}
