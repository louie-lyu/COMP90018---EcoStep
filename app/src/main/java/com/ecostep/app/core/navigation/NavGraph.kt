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
import com.ecostep.app.ui.screens.SettingsScreen
import com.ecostep.app.ui.viewmodels.JourneyReviewViewModel
import androidx.compose.ui.platform.LocalContext
import com.ecostep.app.EcoStepApp
import com.ecostep.app.ui.viewmodels.HomeViewModel
import com.ecostep.app.ui.mock.MockHomeRouteDataSource
import com.ecostep.app.ui.mock.MockMissionRepository
import com.ecostep.app.ui.viewmodels.MissionViewModel

@Composable
fun EcoStepNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val application =
        LocalContext.current.applicationContext as EcoStepApp

    val appContainer = application.appContainer
    /*
     * TODO(Journeys/DI): Replace this shared MockJourneyRepository with
     * production journey persistence and tracking dependencies supplied
     * through AppContainer.
     *
     * MissionViewModel and JourneyReviewViewModel must continue to access
     * the same saved journey data so a journey created when a mission ends
     * can be loaded immediately by JourneyReviewScreen.
     */
    val mockJourneyRepository = remember {
        MockJourneyRepository()
    }

    /*
     * TODO(Routing/DI): Replace this mock source with the production
     * routing data source supplied through AppContainer.
     */
    val mockHomeRouteDataSource = remember {
        MockHomeRouteDataSource()
    }

    /*
     * Shared mission state for Home and Mission screens.
     *
     * TODO(Missions/DI): Replace MockMissionRepository with the
     * production Firebase-backed MissionRepository supplied through
     * AppContainer. Both screens must continue to receive the same
     * repository instance.
     */
    val missionRepository = remember {
        MockMissionRepository()
    }


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
                            missionRepository =
                                missionRepository,
                        )
                    },
                )

                HomeScreen(
                    viewModel = homeViewModel,
                    onStartMission = { missionId ->
                        homeViewModel.startMission(missionId)
                    },
                    onViewMission = { _ ->
                        navController.navigate(Routes.MISSIONS) {
                            launchSingleTop = true
                        }
                    },
                )
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
                                mockJourneyRepository,
                            journeyId = journeyId,
                        )
                    },
                )


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
                val missionViewModel: MissionViewModel = viewModel(
                    factory = ViewModelFactory {
                        MissionViewModel(
                            missionRepository = missionRepository,
                            missionJourneyRecorder = mockJourneyRepository,
                        )
                    },
                )

                MissionScreen(
                    missionViewModel = missionViewModel,
                    onStartMission = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(
                                navController.graph
                                    .findStartDestination()
                                    .id,
                            ) {
                                inclusive = false
                            }

                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenJourneyReview = { journeyId ->
                        navController.navigate(
                            Routes.journeyReview(journeyId),
                        )
                    },
                )
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