package com.ecostep.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ecostep.app.ui.screens.HistoryScreen
import com.ecostep.app.ui.screens.HomeScreen
import com.ecostep.app.ui.screens.JourneyReviewScreen
import com.ecostep.app.ui.screens.LoginScreen
import com.ecostep.app.ui.screens.MissionScreen
import com.ecostep.app.ui.screens.SettingsScreen

/**
 * Wires one Compose screen per [Routes] entry. Yu-Han (UI module owner) extends this as real
 * screens replace the placeholders and as navigation arguments (e.g. a journey/mission id) are
 * needed.
 */
@Composable
fun EcoStepNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) { LoginScreen() }
        composable(Routes.HOME) { HomeScreen() }
        composable(Routes.JOURNEY_REVIEW) { JourneyReviewScreen() }
        composable(Routes.MISSIONS) { MissionScreen() }
        composable(Routes.HISTORY) { HistoryScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}
