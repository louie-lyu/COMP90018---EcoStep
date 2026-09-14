package com.ecostep.app

import android.app.Application
import com.ecostep.app.core.di.AppContainer

class EcoStepApp : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer()
    }
}
