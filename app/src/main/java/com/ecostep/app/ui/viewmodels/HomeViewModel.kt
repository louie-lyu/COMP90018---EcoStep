package com.ecostep.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.PublicTransportInfo
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.model.WeatherData
import com.ecostep.app.data.repository.ExternalDataRepository
import com.ecostep.app.ui.mock.CURRENT_LOCATION_LABEL
import com.ecostep.app.ui.mock.HomeRouteDataSource
import com.ecostep.app.ui.mock.HomeRouteException
import com.ecostep.app.ui.mock.HomeRouteOption
import com.ecostep.app.ui.mock.HomeRouteQuery
import com.ecostep.app.ui.mock.MissionDay
import com.ecostep.app.ui.mock.MissionPageItem
import com.ecostep.app.ui.mock.MissionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.ecostep.app.data.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

/*
 * Fallback position used until the first live location arrives, and when
 * location permission is denied or in previews.
 */
private val MELBOURNE_LOCATION = GeoPoint(
    latitude = -37.8136,
    longitude = 144.9631,
)

/*
 * Weather is refreshed only after moving roughly a kilometre, so live GPS
 * samples do not each trigger a network request.
 */
private const val WEATHER_REFRESH_DEGREES = 0.01

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
    val canStartToday: Boolean = true,
)

enum class JourneyTrackingState {
    READY,
    IN_PROGRESS,
}

data class HomeUiState(
    val startLocation: String = CURRENT_LOCATION_LABEL,
    val destination: String = "",
    val isRoutePlannerVisible: Boolean = false,

    /** Live device position, or the Melbourne fallback until one arrives. */
    val currentLocation: GeoPoint = MELBOURNE_LOCATION,

    val weather: WeatherData? = null,
    val isWeatherLoading: Boolean = false,
    val weatherErrorMessage: String? = null,

    val routeOptions: List<HomeRouteOption> = emptyList(),
    val selectedMode: TransportMode? = null,
    val isRouteLoading: Boolean = false,
    val routeErrorMessage: String? = null,
    val isDirectionsConfirmed: Boolean = false,

    /** Next departures for the searched trip, earliest first (at most three). */
    val publicTransportOptions: List<PublicTransportInfo> = emptyList(),
    val publicTransportErrorMessage: String? = null,

    val journeyTrackingState: JourneyTrackingState =
        JourneyTrackingState.READY,

    val upcomingMission: UpcomingMissionUi? = null,

    /** From the user's saved preferences once they load. */
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
    /** Saved reminder settings; the mission card follows them. */
    private val preferences: Flow<UserPreferences> = emptyFlow(),
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

    /** Position the current weather was requested for; null until a live location arrives. */
    private var weatherLocation: GeoPoint? = null

    private var weatherJob: Job? = null

    /** Device position from the location tracker; null until the first fix. */
    private var liveLocation: GeoPoint? = null

    private var searchJob: Job? = null

    init {
        loadWeather()
        startMissionClock()
        observeMissions()
        observePreferences()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferences
                .catch { /* Keep the defaults when the profile cannot be read. */ }
                .collect { saved ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            missionRemindersEnabled = saved.missionNotificationsEnabled,
                            missionReminderLeadMinutes = saved.defaultReminderMinutes,
                        )
                    }
                }
        }
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
                            currentState.withoutPlannedRoute().copy(
                                upcomingMission = visibleMission,
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

        // The map's planned route comes from PlannedRouteViewModel; these options only feed
        // the impact chips, so a failed lookup simply leaves them empty.
        val routeOptions =
            try {
                homeRouteDataSource.getRouteOptions(
                    routeQuery(
                        startText = item.startLocation,
                        destinationText = item.destination,
                    ),
                ).routes
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
        searchJob?.cancel()
        _uiState.update { currentState ->
            currentState.copy(
                startLocation = startLocation,
                routeOptions = emptyList(),
                selectedMode = null,
                isRouteLoading = false,
                routeErrorMessage = null,
                isDirectionsConfirmed = false,
                publicTransportOptions = emptyList(),
                publicTransportErrorMessage = null,
            )
        }
    }

    fun updateDestination(destination: String) {
        searchJob?.cancel()
        _uiState.update { currentState ->
            currentState.copy(
                destination = destination,
                routeOptions = emptyList(),
                selectedMode = null,
                isRouteLoading = false,
                routeErrorMessage = null,
                isDirectionsConfirmed = false,
                publicTransportOptions = emptyList(),
                publicTransportErrorMessage = null,
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

        // Only the latest search may update the screen.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    isRouteLoading = true,
                    routeErrorMessage = null,
                    isDirectionsConfirmed = false,
                    publicTransportOptions = emptyList(),
                    publicTransportErrorMessage = null,
                )
            }

            try {
                val state = _uiState.value
                val result = homeRouteDataSource.getRouteOptions(
                    routeQuery(
                        startText = state.startLocation,
                        destinationText = state.destination,
                    ),
                )
                val routeOptions = result.routes

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
                        publicTransportOptions = result.publicTransportOptions,
                        publicTransportErrorMessage = when {
                            result.publicTransportUnavailable ->
                                "Public transport times are unavailable right now."
                            result.publicTransportOptions.isEmpty() ->
                                "No upcoming public transport departures found."
                            else -> null
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (problem: HomeRouteException) {
                _uiState.update { currentState ->
                    currentState.copy(
                        isRouteLoading = false,
                        routeErrorMessage = problem.message,
                    )
                }
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

    /**
     * Uses the device's live position for weather. Safe to call for every GPS sample:
     * weather reloads on the first live fix and then only after a meaningful move.
     */
    fun updateCurrentLocation(location: GeoPoint) {
        liveLocation = location
        _uiState.update { currentState ->
            currentState.copy(currentLocation = location)
        }

        val previous = weatherLocation
        val movedFar = previous == null ||
            abs(previous.latitude - location.latitude) > WEATHER_REFRESH_DEGREES ||
            abs(previous.longitude - location.longitude) > WEATHER_REFRESH_DEGREES

        if (movedFar) {
            weatherLocation = location
            loadWeather()
        }
    }

    /**
     * Clears a confirmed free-journey route once it was recorded or aborted. Active
     * missions keep their route; it is reset when the mission ends.
     */
    fun clearFreeJourney() {
        if (loadedActiveMissionId != null) {
            return
        }

        searchJob?.cancel()
        _uiState.update { currentState ->
            currentState.withoutPlannedRoute()
        }
    }

    private fun routeQuery(startText: String, destinationText: String) =
        HomeRouteQuery(
            startText = startText,
            destinationText = destinationText,
            currentLocation = liveLocation ?: _uiState.value.currentLocation,
            isCurrentLocationLive = liveLocation != null,
        )

    private fun HomeUiState.withoutPlannedRoute(): HomeUiState =
        copy(
            startLocation = CURRENT_LOCATION_LABEL,
            destination = "",
            routeOptions = emptyList(),
            selectedMode = null,
            isRoutePlannerVisible = false,
            isDirectionsConfirmed = false,
            isRouteLoading = false,
            journeyTrackingState = JourneyTrackingState.READY,
            routeErrorMessage = null,
            publicTransportOptions = emptyList(),
            publicTransportErrorMessage = null,
        )

    fun loadWeather() {
        // A newer position supersedes any request still in flight.
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
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
                // Today's occurrence is already done or skipped: remind about the next one.
                excludeToday = completedToday || skippedToday,
            ) ?: return null

        return UpcomingMissionUi(
            missionId = mission.missionId,
            routeTitle = mission.routeTitle,
            transportLabel = mission.transportLabel,
            startTimeMillis = nextStartTime,
            canStartToday = dueToday && !completedToday && !skippedToday,

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
        excludeToday: Boolean = false,
    ): Long? {
        if (repeatDays.isEmpty()) {
            return null
        }

        val now = Calendar.getInstance()
        val endOfToday = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }
        val notBefore = if (excludeToday) endOfToday else now
        var nearestOccurrence: Long? = null

        repeatDays.forEach { missionDay ->
            val occurrence =
                Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, missionDay.calendarDay)
                    set(Calendar.HOUR_OF_DAY, scheduledHour)
                    set(Calendar.MINUTE, scheduledMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)

                    if (timeInMillis <= notBefore.timeInMillis) {
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
