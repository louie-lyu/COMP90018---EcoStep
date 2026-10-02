package com.ecostep.app

import android.app.Application
import com.ecostep.app.core.di.AppContainer
import org.osmdroid.config.Configuration

class EcoStepApp : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(applicationContext)
        configureOsmdroid()
    }

    private fun configureOsmdroid() {
        val configuration = Configuration.getInstance()
        configuration.userAgentValue = packageName

        val basePath = applicationContext.cacheDir.resolve("osmdroid")
        configuration.osmdroidBasePath = basePath
        configuration.osmdroidTileCache = basePath.resolve("tiles")
    }
}
