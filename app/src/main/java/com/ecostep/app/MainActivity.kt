package com.ecostep.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ecostep.app.core.navigation.EcoStepNavHost
import com.ecostep.app.core.theme.EcoStepTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EcoStepTheme {
                EcoStepNavHost()
            }
        }
    }
}
