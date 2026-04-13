package com.notiflistener.app

import android.app.Application
import com.notiflistener.app.data.AppDatabase
import com.notiflistener.app.service.WebhookRetryWorker
import com.notiflistener.app.util.PreferenceManager

/**
 * Application class utama.
 * Inisialisasi database dan schedule webhook retry worker.
 */
class App : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var prefManager: PreferenceManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize database
        database = AppDatabase.getInstance(this)

        // Initialize preferences
        prefManager = PreferenceManager(this)

        // Schedule webhook retry worker
        if (prefManager.autoRetry) {
            WebhookRetryWorker.schedule(this)
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
