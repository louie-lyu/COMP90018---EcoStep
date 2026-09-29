package com.ecostep.app.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ecostep.app.core.di.ViewModelFactory
import com.ecostep.app.ui.components.EcoStepBottomBar
import com.ecostep.app.ui.mock.MockJourneyRepository
import com.ecostep.app.ui.screens.HistoryScreen
import com.ecostep.app.ui.screens.HomeScreen
import com.ecostep.app.ui.screens.JourneyReviewScreen
import com.ecostep.app.ui.screens.LoginScreen
import com.ecostep.app.ui.screens.MissionScreen
import com.ecostep.app.ui.screens.ProfileScreen
import com.ecostep.app.ui.viewmodels.JourneyReviewViewModel
import androidx.compose.ui.platform.LocalContext
import com.ecostep.app.EcoStepApp
import com.ecostep.app.ui.viewmodels.HomeViewModel
import com.ecostep.app.ui.mock.MockHomeRouteDataSource
import com.ecostep.app.ui.mock.MockHomeMissionDataSource
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.ecostep.app.ui.screens.JourneyHistoryScreen
import com.ecostep.app.ui.viewmodels.JourneyHistoryViewModel

@Composable
fun EcoStepNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current

    val application =
        context.applicationContext as EcoStepApp

    val appContainer = application.appContainer
    /*
     * Temporary UI mock.
     * Replace this with appContainer.journeyRepository when the
     * Firebase-backed repository is available.
     */
    val mockJourneyRepository = remember {
        MockJourneyRepository()
    }

    val mockHomeRouteDataSource = remember {
        MockHomeRouteDataSource()
    }
    val mockHomeMissionDataSource = remember {
        MockHomeMissionDataSource()
    }


    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar =
        currentRoute in setOf(
            Routes.HOME,
            Routes.MISSIONS,
            Routes.HISTORY,
            Routes.PROFILE,
        )

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
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.LOGIN) {
                LoginScreen()
            }

            composable(Routes.HOME) {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = ViewModelFactory {
                        HomeViewModel(
                            externalDataRepository =
                                appContainer.externalDataRepository,
                            homeRouteDataSource =
                                mockHomeRouteDataSource,
                                mockHomeMissionDataSource,
                        )
                    },
                )

                HomeScreen(
                    viewModel = homeViewModel,
                    onStartMission = { _ ->
                        // TODO(Missions/Navigation): Start the selected mission
                        // when the mission navigation flow is available.
                    },
                    onViewMission = { _ ->
                        // TODO(Missions/Navigation): Open the selected mission details
                        // when the mission detail route is available.
                    },
                )
            }

            composable(
                route = Routes.JOURNEY_REVIEW_WITH_ID,
                arguments = listOf(
                    navArgument(Routes.JOURNEY_ID_ARGUMENT) {
                        type = NavType.StringType
                    },
                    navArgument(Routes.READ_ONLY_ARGUMENT) {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { backStackEntry ->
                val journeyId =
                    backStackEntry.arguments
                        ?.getString(Routes.JOURNEY_ID_ARGUMENT)
                        ?: return@composable

                val readOnly =
                    backStackEntry.arguments
                        ?.getBoolean(Routes.READ_ONLY_ARGUMENT)
                        ?: false

                val journeyReviewViewModel:
                        JourneyReviewViewModel = viewModel(
                    key = "$journeyId-$readOnly",
                    factory = ViewModelFactory {
                        JourneyReviewViewModel(
                            journeyRepository =
                                mockJourneyRepository,
                            journeyId = journeyId,
                        )
                    },
                )

                JourneyReviewScreen(
                    viewModel = journeyReviewViewModel,
                    readOnly = readOnly,
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }

            composable(Routes.MISSIONS) {
                MissionScreen()
            }

            composable(Routes.HISTORY) {
                HistoryScreen()
            }

            composable(Routes.JOURNEY_HISTORY) {
                val journeyHistoryViewModel:
                        JourneyHistoryViewModel = viewModel(
                    factory = ViewModelFactory {
                        JourneyHistoryViewModel(
                            journeyRepository =
                                mockJourneyRepository,

                            /*
                             * TODO(Profile/Auth):
                             * Replace this mock user ID with the signed-in
                             * user's ID from the authentication module.
                             */
                            userId = "mock_user",
                        )
                    },
                )

                JourneyHistoryScreen(
                    viewModel = journeyHistoryViewModel,
                    onBack = {
                        navController.popBackStack()
                    },
                    onJourneySelected = { journeyId ->
                        navController.navigate(
                            Routes.journeyHistoryDetail(journeyId),
                        )
                    },
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    onViewJourneyHistory = {
                        navController.navigate(
                            Routes.JOURNEY_HISTORY,
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onOpenLocationSettings = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        ).apply {
                            data = Uri.fromParts(
                                "package",
                                context.packageName,
                                null,
                            )
                        }

                        context.startActivity(intent)
                    },
                    onSignOut = {
                        /*
                         * TODO(Profile/Auth):
                         * Call the authentication module's sign-out function
                         * before navigating to LoginScreen.
                         */
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(navController.graph.id) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        }
                    },
                )
            }
        }
    }
}