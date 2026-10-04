package com.ecostep.app.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ecostep.app.sensors.location.LocationTracker
import com.ecostep.app.sensors.ui.JourneyTrackingPanel
import com.ecostep.app.sensors.ui.hasFineLocationPermission
import com.ecostep.app.sensors.ui.rememberTrackingPermissionRequest
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
import com.ecostep.app.data.model.UserPreferences
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.ecostep.app.sensors.ui.TrackingRoute
import com.ecostep.app.sensors.ui.TrackingRoutes
import com.ecostep.app.sensors.ui.TrackingViewModel
import com.ecostep.app.ui.components.EcoStepBottomBar
import com.ecostep.app.ui.screens.RewardsScreen
import com.ecostep.app.ui.screens.HomeScreen
import com.ecostep.app.ui.screens.JourneyReviewScreen
import com.ecostep.app.ui.screens.MissionScreen
import com.ecostep.app.ui.screens.ProfileScreen
import com.ecostep.app.ui.screens.WeeklyInsightScreen
import com.ecostep.app.ui.viewmodels.JourneyReviewViewModel
import com.ecostep.app.ui.viewmodels.HomeViewModel
import com.ecostep.app.ui.viewmodels.WeeklyInsightViewModel
import com.ecostep.app.ui.adapters.RepositoryHomeRouteDataSource
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.ecostep.app.ui.screens.JourneyHistoryScreen
import com.ecostep.app.ui.viewmodels.JourneyHistoryViewModel
import com.ecostep.app.ui.viewmodels.RewardsViewModel
import com.ecostep.app.ui.viewmodels.MissionViewModel
import com.ecostep.app.ui.viewmodels.PlannedRouteViewModel
import com.ecostep.app.ui.viewmodels.routeSummary
import com.ecostep.app.ui.viewmodels.ProfileViewModel
import com.ecostep.app.ui.adapters.TransportModeLabels

@Composable
fun EcoStepNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
  
    val appContainerContext =
        LocalContext.current.applicationContext

    val appContainer =
        (appContainerContext as EcoStepApp).appContainer

    val placeNameResolver = appContainer.placeNameResolver

    val homeRouteDataSource = remember(appContainer, placeNameResolver) {
        RepositoryHomeRouteDataSource(
            externalDataRepository = appContainer.externalDataRepository,
            locate = placeNameResolver::locate,
            carbonCalculator = appContainer.carbonCalculator,
        )
    }

    val missionStore = appContainer.missionStore

    val trackingViewModelFactory = remember {
        ViewModelFactory {
            TrackingViewModel(
                context = appContainerContext,
                tracker = appContainer.journeyTracker,
                authRepository = appContainer.authRepository,
                journeyRepository = appContainer.journeyRepository,
                transportEvidenceProviderFactory =
                    appContainer::transportEvidenceProvider,
                activeMissionIdProvider = missionStore::activeMissionId,
                locationUpdates = {
                    LocationTracker(appContainerContext).locations()
                },
            )
        }
    }

    // Set when a mission is started; the map then starts recording automatically.
    var startTrackingOnMap by rememberSaveable { mutableStateOf(false) }

    val navBackStackEntry by
    navController.currentBackStackEntryAsState()

    val currentRoute =
        navBackStackEntry?.destination?.route

    val showBottomBar =
        currentRoute in setOf(
            Routes.HOME,
            Routes.MISSIONS,
            Routes.REWARDS,
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
            startDestination = Routes.LOGIN,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.LOGIN) {
                LoginRoute(
                    authRepository = appContainer.authRepository,
                    onLoginSuccess = {
                        appContainer.onSignedIn()
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
                missionStore.bind(appContainer.authRepository.currentUserId)

                val trackingViewModel: TrackingViewModel =
                    viewModel(factory = trackingViewModelFactory)

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
                missionStore.bind(appContainer.authRepository.currentUserId)

                val homeViewModel: HomeViewModel =
                    viewModel(
                        factory = ViewModelFactory {
                            HomeViewModel(
                                externalDataRepository =
                                    appContainer
                                        .externalDataRepository,
                                homeRouteDataSource =
                                    homeRouteDataSource,
                                missionRepository =
                                    missionStore,
                                preferences = appContainer.profileRepository
                                    .observeProfile()
                                    .map { it?.preferences ?: UserPreferences() },
                            )
                        },
                    )

                val trackingViewModel: TrackingViewModel =
                    viewModel(factory = trackingViewModelFactory)
                val tracking by trackingViewModel.tracking.collectAsState()
                val trackingUi by trackingViewModel.ui.collectAsState()
                val liveLocation by trackingViewModel.currentLocation.collectAsState()
                val missionState by missionStore.state.collectAsState()
                val plannedRouteViewModel: PlannedRouteViewModel =
                    viewModel(
                        factory = ViewModelFactory {
                            PlannedRouteViewModel(
                                routeLookup = appContainer.externalDataRepository::getRouteOptions,
                                locate = placeNameResolver::locate,
                            )
                        },
                    )
                val plannedRoute by plannedRouteViewModel.uiState.collectAsState()
                val homeUi by homeViewModel.uiState.collectAsState()

                // Weather follows the device; the ViewModel ignores small moves. The mission
                // route is planned again if the user turns out to be away from it.
                LaunchedEffect(liveLocation) {
                    liveLocation?.let { location ->
                        appContainer.lastKnownLocation.value = location
                        homeViewModel.updateCurrentLocation(location)
                        plannedRouteViewModel.onLocation(location)
                    }
                }

                val requestTrackingPermissions =
                    rememberTrackingPermissionRequest(
                        onGranted = {
                            trackingViewModel.startLocationUpdates()
                            trackingViewModel.start()
                            appContainer.autoJourneyDetection.refresh()
                        },
                        onDenied = {
                            trackingViewModel.showPermissionMessage(
                                "Precise location is needed to record the journey.",
                            )
                        },
                    )

                LaunchedEffect(Unit) {
                    if (hasFineLocationPermission(context)) {
                        trackingViewModel.startLocationUpdates()
                    }
                }

                // A mission was just started: begin recording straight away.
                LaunchedEffect(startTrackingOnMap) {
                    if (startTrackingOnMap) {
                        startTrackingOnMap = false
                        requestTrackingPermissions()
                    }
                }

                LaunchedEffect(trackingUi.savedJourneyId) {
                    trackingUi.savedJourneyId?.let { journeyId ->
                        trackingViewModel.onNavigated()
                        homeViewModel.clearFreeJourney()
                        navController.navigate(Routes.journeyReview(journeyId))
                    }
                }

                val activeMission = missionState.activeMission

                // A route confirmed with "Show route" outside a mission: recorded as a free
                // journey with no linked mission. The real GPS trace, not this plan, is saved.
                val freeJourneyRoute = homeUi.selectedRouteOption?.route
                    ?.takeIf { homeUi.isDirectionsConfirmed && activeMission == null }

                val startRecording: () -> Unit = {
                    trackingViewModel.clearMessage()
                    requestTrackingPermissions()
                }

                // Plan the route once per active mission, from the first live position.
                LaunchedEffect(activeMission?.mission?.missionId, liveLocation != null) {
                    val mission = activeMission
                    val from = liveLocation
                    if (mission == null) {
                        plannedRouteViewModel.clear()
                    } else if (from != null) {
                        plannedRouteViewModel.plan(
                            key = mission.mission.missionId,
                            destinationName = mission.destination,
                            from = from,
                            mode = TransportModeLabels.parse(mission.mission.transportLabel),
                        )
                    }
                }

                val showTrackingPanel =
                    activeMission != null || freeJourneyRoute != null ||
                        tracking.isRecording || trackingUi.isSaving

                HomeScreen(
                    viewModel = homeViewModel,
                    onStartJourney = { _ -> startRecording() },
                    onStartMission = { missionId ->
                        homeViewModel.startMission(missionId)
                        startTrackingOnMap = true
                    },
                    onViewMission = { _ ->
                        navController.navigate(Routes.MISSIONS) {
                            launchSingleTop = true
                        }
                    },
                    liveLocation = liveLocation,
                    plannedRoute = freeJourneyRoute?.path ?: plannedRoute.path,
                    routeStart = freeJourneyRoute?.path?.firstOrNull() ?: plannedRoute.start,
                    routeDestination = freeJourneyRoute?.path?.lastOrNull()
                        ?: plannedRoute.destination,
                    recordedPath = tracking.path,
                    trackingPanel = if (showTrackingPanel) {
                        {
                            JourneyTrackingPanel(
                                missionTitle = activeMission?.mission?.routeTitle,
                                routeSummary = when {
                                    freeJourneyRoute != null -> routeSummary(freeJourneyRoute)
                                    plannedRoute.isLoading -> "Planning route..."
                                    else -> plannedRoute.summary
                                },
                                isRecording = tracking.isRecording,
                                isSaving = trackingUi.isSaving,
                                distanceMeters = tracking.distanceMeters,
                                elapsedSeconds = tracking.elapsedSeconds,
                                message = trackingUi.message ?: tracking.sensorError,
                                onStart = startRecording,
                                onEnd = trackingViewModel::stop,
                                onAbort = {
                                    trackingViewModel.discard()
                                    missionStore.endActiveMission()
                                    homeViewModel.clearFreeJourney()
                                },
                            )
                        }
                    } else {
                        null
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

                val journeyReviewViewModel: JourneyReviewViewModel =
                    viewModel(
                        key = "$journeyId-$readOnly",
                        factory = ViewModelFactory {
                            JourneyReviewViewModel(
                                journeyRepository = appContainer.journeyRepository,
                                journeyId = journeyId,
                                placeNameResolver = placeNameResolver::resolve,
                                carbonCalculator = appContainer.carbonCalculator,
                                onJourneyConfirmed = { journey ->
                                    journey.linkedMissionId?.let { missionId ->
                                        missionStore.completeOccurrence(
                                            missionId = missionId,
                                            journeyId = journey.journeyId,
                                        )
                                    }
                                    // Runs beyond this screen and never blocks or fails the
                                    // confirmation; the suggestion appears on Missions.
                                    appContainer.applicationScope.launch {
                                        appContainer.missionGenerationCoordinator
                                            .onJourneyConfirmed(journey)
                                    }
                                },
                            )
                        },
                    )

                JourneyReviewScreen(
                    viewModel = journeyReviewViewModel,
                    readOnly = readOnly,
                    onBackToMap = {
                        navController.navigate(Routes.HOME) {
                            launchSingleTop = true
                        }
                    },
                    onBack = {
                        if (readOnly) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(Routes.HOME) {
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }

            composable(Routes.MISSIONS) {
                missionStore.bind(appContainer.authRepository.currentUserId)

                val missionViewModel: MissionViewModel =
                    viewModel(
                        factory = ViewModelFactory {
                            MissionViewModel(
                                missionRepository = missionStore,
                                routeEstimator = appContainer.missionRouteEstimator::estimate,
                            )
                        },
                    )

                MissionScreen(
                    missionViewModel = missionViewModel,
                    onStartMission = {
                        // The map records the journey; it is linked to the active mission
                        // and completes it once confirmed on the review screen.
                        startTrackingOnMap = true
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
                    onOpenWeeklyInsight = {
                        navController.navigate(
                            Routes.WEEKLY_INSIGHT,
                        )
                    },
                )
            }

            composable(Routes.WEEKLY_INSIGHT) {
                val weeklyInsightViewModel: WeeklyInsightViewModel =
                    viewModel(
                        factory = ViewModelFactory {
                            WeeklyInsightViewModel(
                                coaching = appContainer.aiModule.weeklyCoach::generate,
                                dataSource =
                                    appContainer.weeklyInsightDataSource(),
                            )
                        },
                    )

                WeeklyInsightScreen(
                    viewModel = weeklyInsightViewModel,
                )
            }
            
            composable(Routes.REWARDS) {
                val rewardsViewModel: RewardsViewModel = viewModel(
                    factory = ViewModelFactory {
                        RewardsViewModel(
                            rewardsDataSource =
                                appContainer.rewardsDataSource(),
                        )
                    },
                )

                RewardsScreen(
                    viewModel = rewardsViewModel,
                )
            }

            composable(Routes.JOURNEY_HISTORY) {
                val journeyHistoryViewModel:
                        JourneyHistoryViewModel = viewModel(
                    factory = ViewModelFactory {
                        JourneyHistoryViewModel(
                            journeyRepository =
                                appContainer.journeyRepository,
                            currentUserId =
                                appContainer.authRepository.currentUserId,
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
                val profileViewModel: ProfileViewModel =
                    viewModel(
                        factory = ViewModelFactory {
                            ProfileViewModel(
                                profileDataSource =
                                    appContainer.profileDataSource(),
                            )
                        },
                    )

                ProfileScreen(
                    viewModel = profileViewModel,
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
                    onPermissionsChanged =
                        appContainer.autoJourneyDetection::refresh,
                    onSignOut = {
                        appContainer.onSigningOut()
                        appContainer.authRepository.signOut()
                        missionStore.bind(null)
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
