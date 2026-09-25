package com.ecostep.app.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ecostep.app.EcoStepApp
import com.ecostep.app.auth.LoginRoute
import com.ecostep.app.core.di.ViewModelFactory
import com.ecostep.app.sensors.location.PlaceNameResolver
import com.ecostep.app.sensors.ui.TrackingRoute
import com.ecostep.app.sensors.ui.TrackingRoutes
import com.ecostep.app.sensors.ui.TrackingViewModel
import com.ecostep.app.ui.components.EcoStepBottomBar
import com.ecostep.app.ui.screens.HistoryScreen
import com.ecostep.app.ui.screens.HomeScreen
import com.ecostep.app.ui.screens.JourneyReviewScreen
import com.ecostep.app.ui.screens.MissionScreen
import com.ecostep.app.ui.screens.SettingsScreen
import com.ecostep.app.ui.viewmodels.JourneyReviewViewModel

@Composable
fun EcoStepNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val appContainerContext = LocalContext.current.applicationContext
    val appContainer = (appContainerContext as EcoStepApp).appContainer
    val placeNameResolver = remember { PlaceNameResolver(appContainerContext) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar =
        currentRoute != null && currentRoute != Routes.LOGIN

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                EcoStepBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(
                                navController.graph
                                    .findStartDestination()
                                    .id,
                            ) {
                                saveState = true
                            }

                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.LOGIN) {
                LoginRoute(
                    authRepository = appContainer.authRepository,
                    onLoginSuccess = {
                        navController.navigate(TrackingRoutes.TRACKING) {
                            popUpTo(Routes.LOGIN) {
                                inclusive = true
                            }
                        }
                    },
                )
            }

            composable(TrackingRoutes.TRACKING) {
                val trackingViewModel: TrackingViewModel = viewModel(
                    factory = ViewModelFactory {
                        TrackingViewModel(
                            context = appContainerContext,
                            tracker = appContainer.journeyTracker,
                            authRepository = appContainer.authRepository,
                            journeyRepository = appContainer.journeyRepository,
                        )
                    },
                )
                TrackingRoute(
                    viewModel = trackingViewModel,
                    onJourneySaved = { id -> navController.navigate(Routes.journeyReview(id)) },
                )
            }

            composable(Routes.HOME) {
                HomeScreen()
            }

            composable(
                route = Routes.JOURNEY_REVIEW_WITH_ID,
                arguments = listOf(
                    navArgument(Routes.JOURNEY_ID_ARGUMENT) {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                val journeyId =
                    backStackEntry.arguments
                        ?.getString(Routes.JOURNEY_ID_ARGUMENT)
                        ?: return@composable

                val journeyReviewViewModel:
                        JourneyReviewViewModel = viewModel(
                    key = journeyId,
                    factory = ViewModelFactory {
                        JourneyReviewViewModel(
                            journeyRepository =
                                appContainer.journeyRepository,
                            journeyId = journeyId,
                            placeNameResolver = placeNameResolver::resolve,
                        )
                    },
                )

                /*
                 * The ViewModel is created here first.
                 * The next step connects it to JourneyReviewScreen.
                 */
                JourneyReviewScreen(
                    viewModel = journeyReviewViewModel,
                    onBackToMap = {
                        navController.navigate(Routes.HOME) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Routes.MISSIONS) {
                MissionScreen()
            }

            composable(Routes.HISTORY) {
                HistoryScreen()
            }

            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}
