package app.edge.launcher

import android.app.Application
import android.content.Context
import app.edge.launcher.data.AppRepository
import app.edge.launcher.data.RecentsRepository
import app.edge.launcher.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class EdgeApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    lateinit var settings: SettingsRepository
        private set
    lateinit var apps: AppRepository
        private set
    lateinit var recents: RecentsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this, scope)
        apps = AppRepository(this, scope, settings)
        recents = RecentsRepository(this, apps)
    }
}

val Context.edge: EdgeApp get() = applicationContext as EdgeApp
