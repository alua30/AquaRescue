package com.aquarescue.app

import android.app.Application

class AquaRescueApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.monitoringRepository.start()
    }
}
