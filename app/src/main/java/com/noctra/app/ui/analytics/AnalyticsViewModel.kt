package com.noctra.app.ui.analytics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noctra.app.data.model.RoutineSession
import com.noctra.app.data.model.SleepRecord
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.data.repository.SleepRecordRepository
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.domain.usecase.InsightGenerationUseCase
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class AnalyticsViewModel : ViewModel() {

    private val sleepRepo = SleepRecordRepository()
    private val sessionRepo = RoutineSessionRepository()
    private val profileRepo = UserProfileRepository()
    private val insightUseCase = InsightGenerationUseCase()

    private val _state = MutableStateFlow(AnalyticsUiState())
    val state = _state.asStateFlow()

    fun load(context: Context) {
        viewModelScope.launch {
            val userId = UserSession.getUserId(context)
            val currentWeekStart = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            loadWeek(userId, currentWeekStart)
        }
    }

    fun previousWeek(context: Context) {
        val current = _state.value.weekStart
        val newStart = current.minusWeeks(1)
        viewModelScope.launch {
            loadWeek(UserSession.getUserId(context), newStart)
        }
    }

    fun nextWeek(context: Context) {
        val current = _state.value.weekStart
        val newStart = current.plusWeeks(1)

        // Don't allow navigating into the future
        val currentWeekStart = LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (newStart > currentWeekStart) return

        viewModelScope.launch {
            loadWeek(UserSession.getUserId(context), newStart)
        }
    }

    private suspend fun loadWeek(userId: String, weekStart: LocalDate) {
        try {
            val weekEnd = weekStart.plusDays(6)

            // Last Night: fetch the most recent record regardless of week shown
            val lastNight = sleepRepo.getMostRecentRecord(userId)

            // Week data
            val sleepRecords = sleepRepo.getRecordsInRange(
                userId,
                startDate = weekStart.toString(),
                endDate = weekEnd.toString()
            )
            val sessions = sessionRepo.getSessionsInRange(
                userId,
                startDate = weekStart.toString(),
                endDate = weekEnd.toString()
            )

            // Target bedtime from profile
            val profile = profileRepo.getOrCreateProfile(userId)
            val targetBedtime = profile.targetBedtime

            // Generate Insight
            val insight = insightUseCase.generate(sleepRecords, sessions)

            _state.value = AnalyticsUiState(
                weekStart = weekStart,
                weekRangeLabel = formatWeekRange(weekStart, weekEnd),
                lastNightRecord = lastNight,
                weekSleepRecords = sleepRecords,
                weekSessions = sessions,
                targetBedtime = targetBedtime,
                insightResult = insight,
                canGoForward = weekStart < LocalDate.now()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            )
        } catch (e: Exception) {
            android.util.Log.e("AnalyticsViewModel", "Failed to load week", e)
        }
    }

    private fun formatWeekRange(start: LocalDate, end: LocalDate): String {
        val startFmt = DateTimeFormatter.ofPattern("MMM d")
        val endFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
        return "${start.format(startFmt)} - ${end.format(endFmt)}"
    }
}

data class AnalyticsUiState(
    val weekStart: LocalDate = LocalDate.now()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val weekRangeLabel: String = "",
    val lastNightRecord: SleepRecord? = null,
    val weekSleepRecords: List<SleepRecord> = emptyList(),
    val weekSessions: List<RoutineSession> = emptyList(),
    val targetBedtime: String? = null,
    val insightResult: InsightGenerationUseCase.Result? = null,
    val canGoForward: Boolean = false
)
