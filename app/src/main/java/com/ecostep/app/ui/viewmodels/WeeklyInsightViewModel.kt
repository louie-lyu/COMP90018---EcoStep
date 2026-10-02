package com.ecostep.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.ecostep.app.algorithm.WeeklyCoach
import com.ecostep.app.algorithm.WeeklyCoachReport
import com.ecostep.app.ui.mock.WeeklyInsightDataSource
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WeeklyInsightUiState(
    val report: WeeklyCoachReport? = null,
)

class WeeklyInsightViewModel(
    private val weeklyCoach: WeeklyCoach,
    private val dataSource: WeeklyInsightDataSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeeklyInsightUiState())
    val uiState: StateFlow<WeeklyInsightUiState> = _uiState.asStateFlow()

    init {
        loadReport()
    }

    private fun loadReport() {
        val (weekStartMillis, weekEndMillis) = currentWeekBounds()

        val report = weeklyCoach.generate(
            results = dataSource.getMissionResults(),
            weekStartMillis = weekStartMillis,
            weekEndMillis = weekEndMillis,
        )

        _uiState.update {
            it.copy(report = report)
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
