package com.ecostep.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.model.WeatherData
import com.ecostep.app.data.repository.ExternalDataRepository
import com.ecostep.app.ui.mock.HomeRouteDataSource
import com.ecostep.app.ui.mock.HomeRouteOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.ecostep.app.ui.mock.HomeMissionDataSource

/*
 * Temporary location used until the location module provides
 * the user's live location.
 */
private val MELBOURNE_LOCATION = GeoPoint(
    latitude = -37.8136,
    longitude = 144.9631,
)

/**
 * Mission information displayed by the Home screen.
 *
 * This is a UI-specific model so HomeScreen does not depend directly
 * on the mission module's internal data structure.
 */
data class UpcomingMissionUi(
    val missionId: String,
    val routeTitle: String,
    val transportLabel: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val estimatedCarbonSavedKg: Double,
    val estimatedEcoPoints: Int,
)

data class HomeUiState(
    val destination: String = "",
    val currentLocation: GeoPoint = MELBOURNE_LOCATION,
    val weather: WeatherData? = null,
    val isWeatherLoading: Boolean = false,
    val weatherErrorMessage: String? = null,
    val routeOptions: List<HomeRouteOption> = emptyList(),
    val selectedMode: TransportMode? = null,
    val isRouteLoading: Boolean = false,
    val routeErrorMessage: String? = null,
    val isDirectionsConfirmed: Boolean = false,
    // TODO(Missions): Populate this from the mission data source
    //// when the mission module exposes scheduled mission data.
    val upcomingMission: UpcomingMissionUi? = null,

    // TODO(Settings): Replace these defaults with the user's saved
    // mission notification preferences from persistent settings.
    val missionRemindersEnabled: Boolean = false,
    val missionReminderLeadMinutes: Int = 15,
    val currentTimeMillis: Long = System.currentTimeMillis(),
) {
    val selectedRouteOption: HomeRouteOption?
        get() = routeOptions.firstOrNull { option ->
            option.route.mode == selectedMode
        }

    val isRouteCardVisible: Boolean
        get() = routeOptions.isNotEmpty()

    val shouldShowMissionCard: Boolean
        get() {
            val mission = upcomingMission ?: return false

            val reminderLeadMillis =
                missionReminderLeadMinutes
                    .coerceAtLeast(0) * 60_000L

            val reminderStartsAtMillis =
                mission.startTimeMillis - reminderLeadMillis

            return missionRemindersEnabled &&
                    currentTimeMillis >= reminderStartsAtMillis &&
                    currentTimeMillis < mission.endTimeMillis
        }
}

class HomeViewModel(
    private val externalDataRepository: ExternalDataRepository,
    private val homeRouteDataSource: HomeRouteDataSource,
    private val homeMissionDataSource: HomeMissionDataSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())

    val uiState: StateFlow<HomeUiState> =
        _uiState.asStateFlow()

    init {
        loadWeather()
        startMissionClock()
        loadUpcomingMission()
    }

    private fun loadUpcomingMission() {
        viewModelScope.launch {
            // TODO(Missions): Add error handling when this is connected
            // to the production mission data source.
            val currentTimeMillis = System.currentTimeMillis()
            val mission =
                homeMissionDataSource.getUpcomingMission(
                    currentTimeMillis = currentTimeMillis,
                )

            _uiState.update { currentState ->
                currentState.copy(
                    upcomingMission = mission,

                    // TODO(Settings): Replace these temporary values with
                    // the user's saved reminder preferences.
                    missionRemindersEnabled = true,
                    missionReminderLeadMinutes = 15,
                )
            }
        }
    }

    fun updateDestination(destination: String) {
        _uiState.update { currentState ->
            currentState.copy(
                destination = destination,
                routeOptions = emptyList(),
                selectedMode = null,
                routeErrorMessage = null,
                isDirectionsConfirmed = false,
            )
        }
    }

    fun findRoutes() {
        if (_uiState.value.destination.isBlank()) {
            _uiState.update { currentState ->
                currentState.copy(
                    routeErrorMessage =
                        "Please enter a destination first.",
                )
            }

            return
        }

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    isRouteLoading = true,
                    routeErrorMessage = null,
                    isDirectionsConfirmed = false,
                )
            }

            try {
                val routeOptions =
                    homeRouteDataSource.getRouteOptions()

                val defaultMode =
                    routeOptions.firstOrNull { option ->
                        option.route.mode ==
                                TransportMode.PUBLIC_TRANSPORT
                    }?.route?.mode
                        ?: routeOptions.firstOrNull()?.route?.mode

                _uiState.update { currentState ->
                    currentState.copy(
                        routeOptions = routeOptions,
                        selectedMode = defaultMode,
                        isRouteLoading = false,
                        routeErrorMessage = if (
                            routeOptions.isEmpty()
                        ) {
                            "No route options were found."
                        } else {
                            null
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(
                        isRouteLoading = false,
                        routeErrorMessage =
                            "Route options are currently unavailable.",
                    )
                }
            }
        }
    }

    fun selectTransportMode(mode: TransportMode) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedMode = mode,
                isDirectionsConfirmed = false,
            )
        }
    }

    fun confirmDirections() {
        if (_uiState.value.selectedRouteOption == null) {
            return
        }

        _uiState.update { currentState ->
            currentState.copy(
                isDirectionsConfirmed = true,
            )
        }
    }

    // Updates the in-app Mission card while HomeScreen is active.
    // TODO(Notifications): Background reminders must be handled separately
    // by the notification scheduling implementation.
    private fun startMissionClock() {
        viewModelScope.launch {
            while (true) {
                _uiState.update { currentState ->
                    currentState.copy(
                        currentTimeMillis = System.currentTimeMillis(),
                    )
                }

                delay(60_000L)
            }
        }
    }

    fun loadWeather() {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    isWeatherLoading = true,
                    weatherErrorMessage = null,
                )
            }

            try {
                val weather =
                    externalDataRepository.getWeather(
                        _uiState.value.currentLocation,
                    )

                _uiState.update { currentState ->
                    currentState.copy(
                        weather = weather,
                        isWeatherLoading = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(
                        isWeatherLoading = false,
                        weatherErrorMessage =
                            "Weather is currently unavailable.",
                    )
                }
            }
        }
    }
}