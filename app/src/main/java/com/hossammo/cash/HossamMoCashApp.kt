package com.hossammo.cash

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.hossammo.cash.data.settings.UserPreferencesRepository
import com.hossammo.cash.notifications.DailyCheckWorker
import com.hossammo.cash.notifications.Notifier
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HossamMoCashApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var prefs: UserPreferencesRepository

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        appScope.launch {
            val hour = runCatching { prefs.current().notifications.hourOfDay }.getOrDefault(10)
            DailyCheckWorker.schedule(this@HossamMoCashApp, hour)
        }
    }
}
