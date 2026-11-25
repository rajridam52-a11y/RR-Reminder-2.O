package com.example

import android.app.Application
import com.example.core.di.AppContainer
import com.google.firebase.FirebaseApp

class RRReminderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        runCatching {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        }
        container = AppContainer(this)
        runCatching {
            container.notificationManager.ensureChannels()
        }
    }
}
