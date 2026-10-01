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
import com.ecostep.app.ui.mock.MockJourneyRepository
import com.ecostep.app.ui.screens.RewardsScreen
import com.ecostep.app.ui.mock.MockMissionRepository
import com.ecostep.app.ui.screens.HomeScreen
import com.ecostep.app.ui.screens.JourneyReviewScreen
import com.ecostep.app.ui.screens.MissionScreen
import com.ecostep.app.ui.screens.SettingsScreen
import com.ecostep.app.ui.viewmodels.HomeViewModel
import com.ecostep.app.ui.mock.MockHomeRouteDataSource
import com.ecostep.app.ui.mock.MockHomeMissionDataSource
import com.ecostep.app.ui.viewmodels.RewardsViewModel
import com.ecostep.app.ui.viewmodels.JourneyReviewViewModel
import com.ecostep.app.ui.viewmodels.MissionViewModel

@Composable
fun EcoStepNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val appContainerContext =
        LocalContext.current.applicationContext

    val appContainer =
        (appContainerContext as EcoStepApp).appContainer

    val placeNameResolver = remember {
        PlaceNameResolver(appContainerContext)
    }

    val mockJourneyRepository = remember {
        MockJourneyRepository()
    }

    val mockHomeRouteDataSource = remember {
        MockHomeRouteDataSource()
    }

    val missionRepository = remember {
        MockMissionRepository()
    }

    val navBackStackEntry by
    navController.currentBackStackEntryAsState()

    val currentRoute =
        navBackStackEntry?.destination?.route

    val showBottomBar =
        currentRoute != null &&
                currentRoute != Routes.LOGIN &&
                currentRoute != TrackingRoutes.TRACKING

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
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(TrackingRoutes.TRACKING) {
                val trackingViewModel: TrackingViewModel =
                    viewModel(
                        factory = ViewModelFactory {
                            TrackingViewModel(
                                context = appContainerContext,
                                tracker =
                                    appContainer.journeyTracker,
                                authRepository =
                                    appContainer.authRepository,
                                journeyRepository =
                                    appContainer.journeyRepository,
                            )
                        },
                    )

                TrackingRoute(
                    viewModel = trackingViewModel,
                    onJourneySaved = { journeyId ->
                        navController.navigate(
                            Routes.journeyReview(journeyId),
                        )
                    },
                )
            }

            composable(Routes.HOME) {
                val homeViewModel: HomeViewModel =
                    viewModel(
                        factory = ViewModelFactory {
                            HomeViewModel(
                                externalDataRepository =
                                    appContainer
                                        .externalDataRepository,
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
                    navArgument(
                        Routes.JOURNEY_ID_ARGUMENT,
                    ) {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                val journeyId =
                    backStackEntry.arguments
                        ?.getString(
                            Routes.JOURNEY_ID_ARGUMENT,
                        )
                        ?: return@composable

                val journeyReviewViewModel:
                        JourneyReviewViewModel =
                    viewModel(
                        key = journeyId,
                        factory = ViewModelFactory {
                            JourneyReviewViewModel(
                                journeyRepository =
                                    mockJourneyRepository,
                                journeyId = journeyId,
                                placeNameResolver =
                                    placeNameResolver::resolve,
                            )
                        },
                    )

                JourneyReviewScreen(
                    viewModel = journeyReviewViewModel,
                    onBackToMap = {
                        navController.navigate(
                            Routes.HOME,
                        ) {
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
                            missionJourneyRecorder =
                                mockJourneyRepository,
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

            composable(Routes.REWARDS) {
                val rewardsViewModel: RewardsViewModel = viewModel(
                    factory = ViewModelFactory {
                        RewardsViewModel()
                    },
                )

                RewardsScreen(
                    viewModel = rewardsViewModel,
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}
