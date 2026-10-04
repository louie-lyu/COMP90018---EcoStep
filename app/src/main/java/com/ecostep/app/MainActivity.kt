package com.ecostep.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ecostep.app.core.navigation.EcoStepNavHost
import com.ecostep.app.core.theme.EcoStepTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Automatic journey detection only runs while the app is visible.
        val autoDetection = (application as EcoStepApp).appContainer.autoJourneyDetection
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = autoDetection.setAppInForeground(true)
                override fun onStop(owner: LifecycleOwner) = autoDetection.setAppInForeground(false)
            },
        )

        setContent {
            EcoStepTheme {
                EcoStepNavHost()
            }
        }
    }
}
