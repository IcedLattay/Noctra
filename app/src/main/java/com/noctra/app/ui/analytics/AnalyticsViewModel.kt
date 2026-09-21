package com.noctra.app.ui.analytics

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noctra.app.data.model.RoutineSession
import com.noctra.app.data.model.SleepRecord
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.data.repository.SleepRecordRepository
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.domain.usecase.BedtimeAdherenceCalculator
import com.noctra.app.domain.usecase.InsightGenerationUseCase
import com.noctra.app.domain.usecase.MonthlyInsightGenerationUseCase
import com.noctra.app.utils.NetworkObserver
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val sleepRepo = SleepRecordRepository()
    private val sessionRepo = RoutineSessionRepository()
    private val profileRepo = UserProfileRepository()
    private val insightUseCase = InsightGenerationUseCase()
    private val monthlyInsightUseCase = MonthlyInsightGenerationUseCase()
    private val adherenceCalculator = BedtimeAdherenceCalculator()
    private val networkObserver = NetworkObserver(application)

    private val _state = MutableStateFlow(AnalyticsUiState())
    val state = _state.asStateFlow()

    // ─── View Mode ────────────────────────────────────────────────

    fun setViewMode(mode: AnalyticsViewMode) {
        _state.value = _state.value.copy(viewMode = mode)
        // Reload data for the new mode
        val current = _state.value
        if (mode == AnalyticsViewMode.WEEKLY) {
            // Reload weekly data
            viewModelScope.launch {
                loadWeek(UserSession.getUserId(getApplication()), current.weekStart)
            }
        } else {
            // Reload monthly data
            viewModelScope.launch {
                loadMonth(UserSession.getUserId(getApplication()), current.selectedMonth)
            }
        }
    }

    // ─── Weekly Navigation ────────────────────────────────────────

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
        val currentWeekStart = LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (newStart > currentWeekStart) return
        viewModelScope.launch {
            loadWeek(UserSession.getUserId(context), newStart)
        }
    }

    // ─── Monthly Navigation ───────────────────────────────────────

    fun previousMonth(context: Context) {
        val current = _state.value.selectedMonth
        val newMonth = current.minusMonths(1)
        viewModelScope.launch {
            loadMonth(UserSession.getUserId(context), newMonth)
        }
    }

    fun nextMonth(context: Context) {
        val current = _state.value.selectedMonth
        val newMonth = current.plusMonths(1)
        if (newMonth > YearMonth.now()) return
        viewModelScope.launch {
            loadMonth(UserSession.getUserId(context), newMonth)
        }
    }

    // ─── Load Data ────────────────────────────────────────────────

    fun load(context: Context) {
        viewModelScope.launch {
            val userId = UserSession.getUserId(context)
            val mode = _state.value.viewMode
            if (mode == AnalyticsViewMode.WEEKLY) {
                val currentWeekStart = LocalDate.now()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                loadWeek(userId, currentWeekStart)
            } else {
                loadMonth(userId, YearMonth.now())
            }
        }
    }

    private suspend fun loadWeek(userId: String?, weekStart: LocalDate) {
        if (userId == null) return
        if (!networkObserver.checkNow()) {
            _state.value = _state.value.copy(isOffline = true)
            return
        }
        try {
            val weekEnd = weekStart.plusDays(6)

            val lastNight = sleepRepo.getMostRecentRecord(userId)
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
            val profile = profileRepo.getOrCreateProfile(userId)
            val targetBedtime = profile.targetBedtime
            val insight = insightUseCase.generate(sleepRecords, sessions)

            _state.value = _state.value.copy(
                weekStart = weekStart,
                weekRangeLabel = formatWeekRange(weekStart, weekEnd),
                lastNightRecord = lastNight,
                weekSleepRecords = sleepRecords,
                weekSessions = sessions,
                targetBedtime = targetBedtime,
                insightResult = insight,
                canGoForward = weekStart < LocalDate.now()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                isOffline = false,
                // Monthly data
                monthSleepRecords = _state.value.monthSleepRecords,
                monthSessions = _state.value.monthSessions,
                monthRangeLabel = _state.value.monthRangeLabel,
                monthlyInsightResult = _state.value.monthlyInsightResult,
                canGoForwardMonth = _state.value.canGoForwardMonth,
                weeklyBreakdownData = _state.value.weeklyBreakdownData,
                monthlyCompletionRate = _state.value.monthlyCompletionRate,
                monthlyTotalCompleted = _state.value.monthlyTotalCompleted,
                monthlyTotalDays = _state.value.monthlyTotalDays
            )
        } catch (e: Exception) {
            android.util.Log.e("AnalyticsViewModel", "Failed to load week", e)
            if (!networkObserver.checkNow()) {
                _state.value = _state.value.copy(isOffline = true)
            }
        }
    }

    private suspend fun loadMonth(userId: String?, yearMonth: YearMonth) {
        if (userId == null) return
        if (!networkObserver.checkNow()) {
            _state.value = _state.value.copy(isOffline = true)
            return
        }
        try {
            val monthStart = yearMonth.atDay(1)
            val monthEnd = yearMonth.atEndOfMonth()

            val sleepRecords = sleepRepo.getRecordsInRange(
                userId,
                startDate = monthStart.toString(),
                endDate = monthEnd.toString()
            )
            val sessions = sessionRepo.getSessionsInRange(
                userId,
                startDate = monthStart.toString(),
                endDate = monthEnd.toString()
            )
            val profile = profileRepo.getOrCreateProfile(userId)
            val targetBedtime = profile.targetBedtime

            // Monthly adherence
            val targetTime = try {
                targetBedtime?.let { java.time.LocalTime.parse(it) }
            } catch (e: Exception) { null }

            val byDate = sleepRecords.associateBy { it.sessionDate }
            var totalDays = 0
            var adherentDays = 0
            var current = monthStart
            while (!current.isAfter(monthEnd)) {
                totalDays++
                val record = byDate[current.toString()]
                val adherence = adherenceCalculator.classify(
                    current, targetTime, record?.sleepOnsetTime
                )
                if (adherence.adherence == BedtimeAdherenceCalculator.Adherence.ADHERENT) {
                    adherentDays++
                }
                current = current.plusDays(1)
            }
            val monthlyAdherenceRate = if (totalDays > 0) (adherentDays * 100.0 / totalDays) else 0.0

            // Per-week breakdown
            val weeklyBreakdown = computeWeeklyBreakdown(
                yearMonth, targetTime, sleepRecords, sessions
            )

            // Monthly completion
            val totalCompleted = sessions.count { it.status == "COMPLETED" }

            // Monthly insight (Week 1 vs Week 4)
            val week1Start = yearMonth.atDay(1)
            val week1End = yearMonth.atDay(7)
            val week4Start = yearMonth.atDay(22)
            val week4End = yearMonth.atDay(28).coerceAtMost(monthEnd)

            val week1Sleep = sleepRecords.filter {
                val date = LocalDate.parse(it.sessionDate)
                !date.isBefore(week1Start) && !date.isAfter(week1End)
            }
            val week1Sessions = sessions.filter {
                val date = LocalDate.parse(it.sessionDate)
                !date.isBefore(week1Start) && !date.isAfter(week1End)
            }
            val week4Sleep = sleepRecords.filter {
                val date = LocalDate.parse(it.sessionDate)
                !date.isBefore(week4Start) && !date.isAfter(week4End)
            }
            val week4Sessions = sessions.filter {
                val date = LocalDate.parse(it.sessionDate)
                !date.isBefore(week4Start) && !date.isAfter(week4End)
            }

            val insightResult = monthlyInsightUseCase.generate(
                MonthlyInsightGenerationUseCase.WeekData(week1Sleep, week1Sessions),
                MonthlyInsightGenerationUseCase.WeekData(week4Sleep, week4Sessions)
            )

            val monthLabel = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))

            _state.value = _state.value.copy(
                selectedMonth = yearMonth,
                monthRangeLabel = monthLabel,
                monthSleepRecords = sleepRecords,
                monthSessions = sessions,
                monthlyInsightResult = insightResult,
                canGoForwardMonth = yearMonth < YearMonth.now(),
                weeklyBreakdownData = weeklyBreakdown,
                monthlyCompletionRate = monthlyAdherenceRate,
                monthlyTotalCompleted = totalCompleted,
                monthlyTotalDays = totalDays,
                targetBedtime = targetBedtime,
                isOffline = false,
                // Keep weekly data intact
                weekStart = _state.value.weekStart,
                weekRangeLabel = _state.value.weekRangeLabel,
                lastNightRecord = _state.value.lastNightRecord,
                weekSleepRecords = _state.value.weekSleepRecords,
                weekSessions = _state.value.weekSessions,
                insightResult = _state.value.insightResult,
                canGoForward = _state.value.canGoForward
            )
        } catch (e: Exception) {
            android.util.Log.e("AnalyticsViewModel", "Failed to load month", e)
            if (!networkObserver.checkNow()) {
                _state.value = _state.value.copy(isOffline = true)
            }
        }
    }

    private fun computeWeeklyBreakdown(
        yearMonth: YearMonth,
        targetBedtime: java.time.LocalTime?,
        sleepRecords: List<SleepRecord>,
        sessions: List<RoutineSession>
    ): List<MonthlyWeeklyBreakdownData> {
        val monthStart = yearMonth.atDay(1)
        val monthEnd = yearMonth.atEndOfMonth()

        val weeks = listOf(
            1 to (monthStart to yearMonth.atDay(7).coerceAtMost(monthEnd)),
            2 to (yearMonth.atDay(8).coerceAtMost(monthEnd) to yearMonth.atDay(14).coerceAtMost(monthEnd)),
            3 to (yearMonth.atDay(15).coerceAtMost(monthEnd) to yearMonth.atDay(21).coerceAtMost(monthEnd)),
            4 to (yearMonth.atDay(22).coerceAtMost(monthEnd) to monthEnd)
        )

        return weeks.map { (weekNum, range) ->
            val (weekStart, weekEnd) = range
            val weekSleepRecords = sleepRecords.filter {
                val date = LocalDate.parse(it.sessionDate)
                !date.isBefore(weekStart) && !date.isAfter(weekEnd)
            }
            val weekSessions = sessions.filter {
                val date = LocalDate.parse(it.sessionDate)
                !date.isBefore(weekStart) && !date.isAfter(weekEnd)
            }

            val byDate = weekSleepRecords.associateBy { it.sessionDate }
            var totalDays = 0
            var adherentDays = 0
            var current = weekStart
            while (!current.isAfter(weekEnd)) {
                totalDays++
                val record = byDate[current.toString()]
                val adherence = adherenceCalculator.classify(
                    current, targetBedtime, record?.sleepOnsetTime
                )
                if (adherence.adherence == BedtimeAdherenceCalculator.Adherence.ADHERENT) {
                    adherentDays++
                }
                current = current.plusDays(1)
            }

            val barRate = if (totalDays > 0) (adherentDays * 100.0 / totalDays) else 0.0
            val completedCount = weekSessions.count { it.status == "COMPLETED" }
            val avgScore = weekSleepRecords
                .mapNotNull { it.compositeScore }
                .takeIf { it.isNotEmpty() }
                ?.average()

            MonthlyWeeklyBreakdownData(
                weekNumber = weekNum,
                barRate = barRate,
                routinesCompleted = completedCount,
                daysInWeek = totalDays,
                avgSleepScore = avgScore
            )
        }
    }

    private fun formatWeekRange(start: LocalDate, end: LocalDate): String {
        val startFmt = DateTimeFormatter.ofPattern("MMM d")
        val endFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
        return "${start.format(startFmt)} - ${end.format(endFmt)}"
    }

    fun retry(context: Context) {
        _state.value = _state.value.copy(isOffline = false)
        load(context)
    }
}

data class AnalyticsUiState(
    // View mode
    val viewMode: AnalyticsViewMode = AnalyticsViewMode.WEEKLY,

    // ─── Weekly data ──────────────────────────────────────────
    val weekStart: LocalDate = LocalDate.now()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val weekRangeLabel: String = "",
    val lastNightRecord: SleepRecord? = null,
    val weekSleepRecords: List<SleepRecord> = emptyList(),
    val weekSessions: List<RoutineSession> = emptyList(),
    val targetBedtime: String? = null,
    val insightResult: InsightGenerationUseCase.Result? = null,
    val canGoForward: Boolean = false,

    // ─── Monthly data ─────────────────────────────────────────
    val selectedMonth: YearMonth = YearMonth.now(),
    val monthRangeLabel: String = "",
    val monthSleepRecords: List<SleepRecord> = emptyList(),
    val monthSessions: List<RoutineSession> = emptyList(),
    val monthlyInsightResult: MonthlyInsightGenerationUseCase.Result? = null,
    val canGoForwardMonth: Boolean = false,
    val weeklyBreakdownData: List<MonthlyWeeklyBreakdownData> = emptyList(),
    val monthlyCompletionRate: Double = 0.0,
    val monthlyTotalCompleted: Int = 0,
    val monthlyTotalDays: Int = 30,

    // Shared
    val isOffline: Boolean = false
)

data class MonthlyWeeklyBreakdownData(
    val weekNumber: Int,
    val barRate: Double,
    val routinesCompleted: Int,
    val daysInWeek: Int,
    val avgSleepScore: Double?
)
