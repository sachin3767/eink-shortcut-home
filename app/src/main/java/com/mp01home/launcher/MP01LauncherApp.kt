package com.mp01home.launcher

import android.app.Application
import android.content.Context

class MP01LauncherApp : Application() {

    /** Manual DI root. See [AppContainer] and docs/ARCHITECTURE.md. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}

/** Shortcut usable from any Context (Activity, Service, Worker, ViewModel's Application). */
val Context.appContainer: AppContainer
    get() = (applicationContext as MP01LauncherApp).container
