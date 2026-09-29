package com.ecostep.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.model.WeatherData
import com.ecostep.app.data.repository.ExternalDataRepository
import com.ecostep.app.ui.mock.HomeRouteDataSource
import com.ecostep.app.ui.mock.HomeRouteOption
import com.ecostep.app.ui.mock.MissionDay
import com.ecostep.app.ui.mock.MissionPageItem
import com.ecostep.app.ui.mock.MissionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

/*
 * TODO(Location): Replace this fixed Melbourne coordinate with the
 * signed-in user's live location from the location-tracking module.
 * Permission denial and unavailable-location states must also be handled.
 */
private val MELBOURNE_LOCATION = GeoPoint(
    latitude = -37.8136,
    longitude = 144.9631,
)

/**
 * Mission information displayed by the Home screen.
 *
 * This remains a Home-specific UI model so HomeScreen does not
 * depend on the Mission screen's presentation structure.
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

enum class JourneyTrackingState {
    READY,
    IN_PROGRESS,
}

data class HomeUiState(
    val startLocation: String = "Current location",
    val destination: String = "",
    val isRoutePlannerVisible: Boolean = false,

    // TODO(Location): Replace this with live location data.
    val currentLocation: GeoPoint = MELBOURNE_LOCATION,

    val weather: WeatherData? = null,
    val isWeatherLoading: Boolean = false,
    val weatherErrorMessage: String? = null,

    val routeOptions: List<HomeRouteOption> = emptyList(),
    val selectedMode: TransportMode? = null,
    val isRouteLoading: Boolean = false,
    val routeErrorMessage: String? = null,
    val isDirectionsConfirmed: Boolean = false,

    val journeyTrackingState: JourneyTrackingState =
        JourneyTrackingState.READY,

    val upcomingMission: UpcomingMissionUi? = null,

    // TODO(Settings): Replace these values with saved user settings.
    val missionRemindersEnabled: Boolean = true,
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
    private val missionRepository: MissionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())

    val uiState: StateFlow<HomeUiState> =
        _uiState.asStateFlow()

    /*
 * Keeps a dismissed Home reminder hidden without removing its recurring
 * mission from MissionScreen.
 *
 * TODO(Missions): Persist dismissal by mission occurrence, using both
 * mission ID and scheduled start time. Using only the mission ID in this
 * prototype can keep future occurrences of the same mission hidden until
 * the ViewModel is recreated.
 */
    private var dismissedReminderMissionId: String? = null

    private var loadedActiveMissionId: String? = null

    init {
        loadWeather()
        startMissionClock()
        observeMissions()
    }

    private fun observeMissions() {
        viewModelScope.launch {
            missionRepository.state.collect { repositoryState ->
                val activeMission = repositoryState.activeMission

                if (activeMission != null) {
                    showActiveMission(activeMission)
                } else {
                    val missionWasActive =
                        loadedActiveMissionId != null

                    loadedActiveMissionId = null

                    val nextMission =
                        repositoryState.upcomingMissions
                            .mapNotNull { mission ->
                                mission.toUpcomingMissionUi()
                            }
                            .minByOrNull { mission ->
                                mission.startTimeMillis
                            }

                    val visibleMission =
                        if (
                            nextMission?.missionId ==
                            dismissedReminderMissionId
                        ) {
                            null
                        } else {
                            nextMission
                        }

                    _uiState.update { currentState ->
                        if (missionWasActive) {
                            currentState.copy(
                                startLocation = "Current location",
                                destination = "",
                                upcomingMission = visibleMission,
                                routeOptions = emptyList(),
                                selectedMode = null,
                                isRoutePlannerVisible = false,
                                isDirectionsConfirmed = false,
                                journeyTrackingState =
                                    JourneyTrackingState.READY,
                                routeErrorMessage = null,
                            )
                        } else {
                            currentState.copy(
                                upcomingMission = visibleMission,
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun showActiveMission(
        item: MissionPageItem,
    ) {
        if (loadedActiveMissionId == item.mission.missionId) {
            return
        }

        loadedActiveMissionId = item.mission.missionId
        dismissedReminderMissionId = item.mission.missionId

        /*
         * TODO(Routing): Request route options using item.startLocation and
         * item.destination. The current mock source returns fixed routes.
         *
         * TODO(Error handling): Expose a route error when production route
         * loading fails instead of silently returning an empty list.
         */
        val routeOptions =
            try {
                homeRouteDataSource.getRouteOptions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }

        val selectedMode =
            transportModeFromLabel(
                item.mission.transportLabel,
            )

        /*
         * Keep HomeScreen's active-mission impact values consistent with
         * the mission selected by the user.
         *
         * TODO(Algorithm): Replace these mission estimates with live values
         * calculated from verified journey progress.
         */
        val activeMissionRouteOptions =
            routeOptions.map { option ->
                if (option.route.mode == selectedMode) {
                    option.copy(
                        estimatedCarbonSavedKg =
                            item.mission.estimatedCarbonSavedKg,
                        estimatedEcoPoints =
                            item.mission.estimatedEcoPoints,
                    )
                } else {
                    option
                }
            }

        _uiState.update { currentState ->
            currentState.copy(
                startLocation = item.startLocation,
                destination = item.destination,
                upcomingMission = null,
                routeOptions = activeMissionRouteOptions,
                selectedMode = selectedMode,
                isRoutePlannerVisible = false,
                isDirectionsConfirmed = true,
                journeyTrackingState =
                    JourneyTrackingState.IN_PROGRESS,
                routeErrorMessage = null,
            )
        }
    }

    fun startMission(missionId: String) {
        /*
         * Updating the shared repository automatically updates both
         * HomeScreen and MissionScreen.
         *
         * TODO(Tracking): Ask the production tracking coordinator to start
         * sensor and location tracking for this mission. MissionRepository
         * should continue to manage mission state rather than sensors directly.
         */
        missionRepository.startMission(missionId)
    }

    fun dismissUpcomingMission() {
        dismissedReminderMissionId =
            _uiState.value.upcomingMission?.missionId

        _uiState.update { currentState ->
            currentState.copy(
                upcomingMission = null,
            )
        }

        // The recurring mission remains available on MissionScreen.
    }

    fun updateStartLocation(startLocation: String) {
        _uiState.update { currentState ->
            currentState.copy(
                startLocation = startLocation,
                routeOptions = emptyList(),
                selectedMode = null,
                routeErrorMessage = null,
                isDirectionsConfirmed = false,
            )
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
                /*
                 * TODO(Routing): Pass the selected start location and destination
                 * to the production route data source.
                 */
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
                        isRoutePlannerVisible =
                            routeOptions.isNotEmpty(),
                        isRouteLoading = false,
                        routeErrorMessage =
                            if (routeOptions.isEmpty()) {
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
                isRoutePlannerVisible = false,
                journeyTrackingState =
                    JourneyTrackingState.READY,
            )
        }
    }

    private fun startMissionClock() {
        viewModelScope.launch {
            while (true) {
                _uiState.update { currentState ->
                    currentState.copy(
                        currentTimeMillis =
                            System.currentTimeMillis(),
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

    /**
     * Calculates the nearest future occurrence of a recurring mission.
     *
     * TODO(Missions): Move this scheduling logic into the production
     * MissionRepository when its persistent implementation is available.
     */
    private fun MissionPageItem.toUpcomingMissionUi():
            UpcomingMissionUi? {
        val nextStartTime =
            calculateNextOccurrenceMillis(
                repeatDays = repeatDays,
                scheduledHour = scheduledHour,
                scheduledMinute = scheduledMinute,
            ) ?: return null

        return UpcomingMissionUi(
            missionId = mission.missionId,
            routeTitle = mission.routeTitle,
            transportLabel = mission.transportLabel,
            startTimeMillis = nextStartTime,

            // TODO(Missions/Routing): Replace the fixed one-hour duration
            // with the estimated duration of the selected route.
            endTimeMillis = nextStartTime + 60 * 60_000L,
            estimatedCarbonSavedKg =
                mission.estimatedCarbonSavedKg,
            estimatedEcoPoints =
                mission.estimatedEcoPoints,
        )
    }

    private fun calculateNextOccurrenceMillis(
        repeatDays: Set<MissionDay>,
        scheduledHour: Int,
        scheduledMinute: Int,
    ): Long? {
        if (repeatDays.isEmpty()) {
            return null
        }

        val now = Calendar.getInstance()
        var nearestOccurrence: Long? = null

        repeatDays.forEach { missionDay ->
            val occurrence =
                Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, missionDay.calendarDay)
                    set(Calendar.HOUR_OF_DAY, scheduledHour)
                    set(Calendar.MINUTE, scheduledMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)

                    if (timeInMillis <= now.timeInMillis) {
                        add(Calendar.WEEK_OF_YEAR, 1)
                    }
                }.timeInMillis

            if (
                nearestOccurrence == null ||
                occurrence < nearestOccurrence!!
            ) {
                nearestOccurrence = occurrence
            }
        }

        return nearestOccurrence
    }

    private val MissionDay.calendarDay: Int
        get() = when (this) {
            MissionDay.MONDAY -> Calendar.MONDAY
            MissionDay.TUESDAY -> Calendar.TUESDAY
            MissionDay.WEDNESDAY -> Calendar.WEDNESDAY
            MissionDay.THURSDAY -> Calendar.THURSDAY
            MissionDay.FRIDAY -> Calendar.FRIDAY
            MissionDay.SATURDAY -> Calendar.SATURDAY
            MissionDay.SUNDAY -> Calendar.SUNDAY
        }

    /*
     * TODO(Missions): Store TransportMode directly in the production
     * mission model and remove this string-to-enum conversion.
     */
    private fun transportModeFromLabel(
        label: String,
    ): TransportMode? =
        when (label.trim().lowercase()) {
            "walking", "walk" ->
                TransportMode.WALKING

            "cycling", "cycle", "bike", "bicycle" ->
                TransportMode.CYCLING

            "public transport", "public_transport" ->
                TransportMode.PUBLIC_TRANSPORT

            "car", "driving" ->
                TransportMode.CAR

            else -> null
        }
}