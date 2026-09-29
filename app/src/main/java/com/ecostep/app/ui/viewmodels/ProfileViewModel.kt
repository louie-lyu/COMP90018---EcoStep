package com.ecostep.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecostep.app.ui.mock.CarbonRankingEntry
import com.ecostep.app.ui.mock.FriendRequestStatus
import com.ecostep.app.ui.mock.FriendSearchResult
import com.ecostep.app.ui.mock.MockProfileDataSource
import com.ecostep.app.ui.mock.ProfileData
import com.ecostep.app.ui.mock.ProfileDataSource
import com.ecostep.app.ui.mock.RankingPeriod
import com.ecostep.app.ui.mock.RankingScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profileData: ProfileData? = null,
    val selectedRankingScope: RankingScope =
        RankingScope.FRIENDS,
    val selectedRankingPeriod: RankingPeriod =
        RankingPeriod.THIS_WEEK,
    val rankingEntries: List<CarbonRankingEntry> =
        emptyList(),
    val friendSearchQuery: String = "",
    val friendSearchResults: List<FriendSearchResult> =
        emptyList(),
    val isLoading: Boolean = true,
    val isRankingLoading: Boolean = false,
    val isSearchingFriends: Boolean = false,
    val friendRequestUserId: String? = null,
    val isSavingProfile: Boolean = false,
    val errorMessage: String? = null,
)

class ProfileViewModel(
    private val profileDataSource: ProfileDataSource =
        MockProfileDataSource(),
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(ProfileUiState())

    val uiState: StateFlow<ProfileUiState> =
        _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }

            try {
                val profileData =
                    profileDataSource.getProfileData()

                val rankingEntries =
                    profileDataSource.getCarbonRanking(
                        scope =
                            _uiState.value.selectedRankingScope,
                        period =
                            _uiState.value.selectedRankingPeriod,
                    )

                _uiState.update {
                    it.copy(
                        profileData = profileData,
                        rankingEntries = rankingEntries,
                        isLoading = false,
                        isRankingLoading = false,
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRankingLoading = false,
                        errorMessage =
                            exception.message
                                ?: "Unable to load profile.",
                    )
                }
            }
        }
    }

    fun selectRankingScope(
        scope: RankingScope,
    ) {
        if (
            scope ==
            _uiState.value.selectedRankingScope
        ) {
            return
        }

        _uiState.update {
            it.copy(selectedRankingScope = scope)
        }

        loadRanking()
    }

    fun selectRankingPeriod(
        period: RankingPeriod,
    ) {
        if (
            period ==
            _uiState.value.selectedRankingPeriod
        ) {
            return
        }

        _uiState.update {
            it.copy(selectedRankingPeriod = period)
        }

        loadRanking()
    }

    private fun loadRanking() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRankingLoading = true,
                    errorMessage = null,
                )
            }

            try {
                val currentState = _uiState.value

                val rankingEntries =
                    profileDataSource.getCarbonRanking(
                        scope =
                            currentState.selectedRankingScope,
                        period =
                            currentState.selectedRankingPeriod,
                    )

                _uiState.update {
                    it.copy(
                        rankingEntries = rankingEntries,
                        isRankingLoading = false,
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isRankingLoading = false,
                        errorMessage =
                            exception.message
                                ?: "Unable to load ranking.",
                    )
                }
            }
        }
    }

    fun updateFriendSearchQuery(
        query: String,
    ) {
        _uiState.update {
            it.copy(friendSearchQuery = query)
        }

        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    friendSearchResults = emptyList(),
                    isSearchingFriends = false,
                )
            }
        }
    }

    fun searchFriends() {
        val query =
            _uiState.value.friendSearchQuery.trim()

        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    friendSearchResults = emptyList(),
                    isSearchingFriends = false,
                )
            }

            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearchingFriends = true,
                    errorMessage = null,
                )
            }

            try {
                val results =
                    profileDataSource.searchUsers(query)

                _uiState.update {
                    it.copy(
                        friendSearchResults = results,
                        isSearchingFriends = false,
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isSearchingFriends = false,
                        errorMessage =
                            exception.message
                                ?: "Unable to search for users.",
                    )
                }
            }
        }
    }

    fun sendFriendRequest(
        userId: String,
    ) {
        val selectedUser =
            _uiState.value.friendSearchResults
                .firstOrNull {
                    it.userId == userId
                }
                ?: return

        if (
            selectedUser.requestStatus !=
            FriendRequestStatus.NONE
        ) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    friendRequestUserId = userId,
                    errorMessage = null,
                )
            }

            try {
                profileDataSource.sendFriendRequest(userId)

                _uiState.update { currentState ->
                    currentState.copy(
                        friendSearchResults =
                            currentState.friendSearchResults.map {
                                    result ->
                                if (result.userId == userId) {
                                    result.copy(
                                        requestStatus =
                                            FriendRequestStatus.REQUEST_SENT,
                                    )
                                } else {
                                    result
                                }
                            },
                        friendRequestUserId = null,
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        friendRequestUserId = null,
                        errorMessage =
                            exception.message
                                ?: "Unable to send friend request.",
                    )
                }
            }
        }
    }

    fun clearFriendSearch() {
        _uiState.update {
            it.copy(
                friendSearchQuery = "",
                friendSearchResults = emptyList(),
                isSearchingFriends = false,
                friendRequestUserId = null,
            )
        }
    }

    fun updateDisplayName(
        displayName: String,
    ) {
        val updatedName = displayName.trim()

        if (updatedName.isBlank()) {
            return
        }

        val currentData =
            _uiState.value.profileData ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSavingProfile = true,
                    errorMessage = null,
                )
            }

            try {
                profileDataSource.updateDisplayName(
                    displayName = updatedName,
                )

                _uiState.update {
                    it.copy(
                        profileData = currentData.copy(
                            user = currentData.user.copy(
                                displayName = updatedName,
                            ),
                        ),
                        rankingEntries =
                            it.rankingEntries.map { entry ->
                                if (entry.isCurrentUser) {
                                    entry.copy(
                                        displayName = updatedName,
                                    )
                                } else {
                                    entry
                                }
                            },
                        isSavingProfile = false,
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isSavingProfile = false,
                        errorMessage =
                            exception.message
                                ?: "Unable to update display name.",
                    )
                }
            }
        }
    }

    fun setRoutineLearningEnabled(
        enabled: Boolean,
    ) {
        updatePreferences(
            errorMessage =
                "Unable to update routine learning.",
            updateLocalState = { currentData ->
                currentData.copy(
                    preferences =
                        currentData.preferences.copy(
                            routineLearningEnabled = enabled,
                        ),
                )
            },
            persistChange = {
                profileDataSource.updateRoutineLearning(
                    enabled = enabled,
                )
            },
        )
    }

    fun setMissionNotificationsEnabled(
        enabled: Boolean,
    ) {
        updatePreferences(
            errorMessage =
                "Unable to update mission notifications.",
            updateLocalState = { currentData ->
                currentData.copy(
                    preferences =
                        currentData.preferences.copy(
                            missionNotificationsEnabled =
                                enabled,
                        ),
                )
            },
            persistChange = {
                profileDataSource.updateMissionNotifications(
                    enabled = enabled,
                )
            },
        )
    }

    fun setDefaultReminderMinutes(
        minutes: Int,
    ) {
        val validMinutes =
            minutes.coerceAtLeast(0)

        updatePreferences(
            errorMessage =
                "Unable to update reminder time.",
            updateLocalState = { currentData ->
                currentData.copy(
                    preferences =
                        currentData.preferences.copy(
                            defaultReminderMinutes =
                                validMinutes,
                        ),
                )
            },
            persistChange = {
                profileDataSource
                    .updateDefaultReminderMinutes(
                        minutes = validMinutes,
                    )
            },
        )
    }

    fun setAutomaticJourneyDetectionEnabled(
        enabled: Boolean,
    ) {
        updatePreferences(
            errorMessage =
                "Unable to update journey detection.",
            updateLocalState = { currentData ->
                currentData.copy(
                    preferences =
                        currentData.preferences.copy(
                            automaticJourneyDetectionEnabled =
                                enabled,
                        ),
                )
            },
            persistChange = {
                profileDataSource
                    .updateAutomaticJourneyDetection(
                        enabled = enabled,
                    )
            },
        )
    }

    fun setCommunityRankingEnabled(
        enabled: Boolean,
    ) {
        updatePreferences(
            errorMessage =
                "Unable to update ranking visibility.",
            updateLocalState = { currentData ->
                currentData.copy(
                    preferences =
                        currentData.preferences.copy(
                            communityRankingEnabled = enabled,
                        ),
                )
            },
            persistChange = {
                profileDataSource
                    .updateCommunityRankingParticipation(
                        enabled = enabled,
                    )
            },
        )
    }

    private fun updatePreferences(
        errorMessage: String,
        updateLocalState: (ProfileData) -> ProfileData,
        persistChange: suspend () -> Unit,
    ) {
        val previousData =
            _uiState.value.profileData ?: return

        _uiState.update {
            it.copy(
                profileData =
                    updateLocalState(previousData),
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            try {
                persistChange()
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        profileData = previousData,
                        errorMessage =
                            exception.message
                                ?: errorMessage,
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update {
            it.copy(errorMessage = null)
        }
    }
}