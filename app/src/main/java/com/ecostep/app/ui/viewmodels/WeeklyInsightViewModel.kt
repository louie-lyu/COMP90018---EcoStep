package com.ecostep.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecostep.app.algorithm.WeeklyCoach
import com.ecostep.app.algorithm.WeeklyCoachReport
import com.ecostep.app.algorithm.WeeklyCoachingResult
import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.ui.mock.WeeklyInsightDataSource
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeeklyInsightUiState(
    /** Locally calculated statistics. */
    val report: WeeklyCoachReport? = null,
    /** "This week" text: AI-personalised, or the local summary. */
    val insight: String? = null,
    /** "Coaching tip" text: AI-personalised, or the local suggestion. */
    val action: String? = null,
    val usedAi: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

class WeeklyInsightViewModel(
    /**
     * Produces statistics plus coaching text, e.g. DefaultAiWeeklyCoach.generate, which
     * falls back to local text itself when the AI is unavailable.
     */
    private val coaching: suspend (
        results: List<MissionResult>,
        weekStartMillis: Long,
        weekEndMillis: Long,
    ) -> WeeklyCoachingResult,
    private val dataSource: WeeklyInsightDataSource,
) : ViewModel() {

    /** Local statistics and text only, without the AI. */
    constructor(
        weeklyCoach: WeeklyCoach,
        dataSource: WeeklyInsightDataSource,
    ) : this(
        coaching = { results, weekStartMillis, weekEndMillis ->
            val report = weeklyCoach.generate(results, weekStartMillis, weekEndMillis)
            WeeklyCoachingResult(
                report = report,
                insight = report.summary,
                action = report.fallbackMessage,
                usedAi = false,
            )
        },
        dataSource = dataSource,
    )

    private val _uiState = MutableStateFlow(WeeklyInsightUiState())
    val uiState: StateFlow<WeeklyInsightUiState> = _uiState.asStateFlow()

    init {
        loadReport()
    }

    private fun loadReport() {
        val (weekStartMillis, weekEndMillis) = currentWeekBounds()

        viewModelScope.launch {
            // Only a data-source failure is an error; AI problems already fell back to local text.
            val results = try {
                dataSource.getMissionResults(weekStartMillis)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Unable to load this week's missions.",
                    )
                }
                return@launch
            }

            try {
                val coachingResult = coaching(results, weekStartMillis, weekEndMillis)

                _uiState.update {
                    it.copy(
                        report = coachingResult.report,
                        insight = coachingResult.insight,
                        action = coachingResult.action,
                        usedAi = coachingResult.usedAi,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Unable to prepare this week's insight.",
                    )
                }
            }
        }
    }

    private fun currentWeekBounds(): Pair<Long, Long> {
        val now = Calendar.getInstance()

        val weekStart = now.clone() as Calendar
        weekStart.firstDayOfWeek = Calendar.MONDAY
        weekStart.set(
            Calendar.DAY_OF_WEEK,
            Calendar.MONDAY,
        )
        weekStart.set(Calendar.HOUR_OF_DAY, 0)
        weekStart.set(Calendar.MINUTE, 0)
        weekStart.set(Calendar.SECOND, 0)
        weekStart.set(Calendar.MILLISECOND, 0)

        if (weekStart.after(now)) {
            weekStart.add(Calendar.WEEK_OF_YEAR, -1)
        }

        return weekStart.timeInMillis to now.timeInMillis
    }
}
