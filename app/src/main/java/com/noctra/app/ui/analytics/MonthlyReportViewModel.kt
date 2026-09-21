package com.noctra.app.ui.analytics

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noctra.app.data.model.RoutineSession
import com.noctra.app.data.model.SleepRecord
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.data.repository.SleepRecordRepository
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.domain.usecase.BedtimeAdherenceCalculator
import com.noctra.app.domain.usecase.MonthlyInsightGenerationUseCase
import com.noctra.app.utils.NetworkObserver
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class MonthlyReportViewModel(application: Application) : AndroidViewModel(application) {

    private val sleepRepo = SleepRecordRepository()
    private val sessionRepo = RoutineSessionRepository()
    private val profileRepo = UserProfileRepository()
    private val monthlyInsightUseCase = MonthlyInsightGenerationUseCase()
    private val adherenceCalculator = BedtimeAdherenceCalculator()
    private val networkObserver = NetworkObserver(application)

    private val _state = MutableStateFlow(MonthlyReportUiState())
    val state = _state.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState = _exportState.asStateFlow()

    fun load(context: Context, yearMonth: YearMonth) {
        viewModelScope.launch {
            val userId = UserSession.getUserId(context) ?: return@launch
            if (!networkObserver.checkNow()) {
                _state.value = _state.value.copy(isOffline = true)
                return@launch
            }

            try {
                val monthStart = yearMonth.atDay(1)
                val monthEnd = yearMonth.atEndOfMonth()

                // Fetch all data for the month
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

                // Compute monthly bedtime adherence
                val adherenceRate = computeMonthlyAdherence(
                    monthStart, monthEnd, targetBedtime, sleepRecords
                )

                // Compute per-week breakdown
                val weeklyBreakdown = computeWeeklyBreakdown(
                    yearMonth, targetBedtime, sleepRecords, sessions
                )

                // Average sleep quality score
                val scoredRecords = sleepRecords.filter { it.compositeScore != null }
                val avgScore = if (scoredRecords.isNotEmpty()) {
                    scoredRecords.mapNotNull { it.compositeScore }.average()
                } else null

                // Best and worst nights
                val bestNight = scoredRecords.maxByOrNull { it.compositeScore!! }
                val worstNight = scoredRecords.minByOrNull { it.compositeScore!! }

                // Month insight (Week 1 vs Week 4)
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
                val insightText = when (insightResult) {
                    is MonthlyInsightGenerationUseCase.Result.Insight -> insightResult.message
                    is MonthlyInsightGenerationUseCase.Result.InsufficientData -> insightResult.message
                }

                // Total routines completed in the month
                val totalCompleted = sessions.count { it.status == "COMPLETED" }
                val totalDaysInMonth = monthEnd.dayOfMonth

                _state.value = MonthlyReportUiState(
                    yearMonth = yearMonth,
                    monthLabel = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    bedtimeAdherenceRate = adherenceRate,
                    avgSleepQualityScore = avgScore,
                    totalRoutinesCompleted = totalCompleted,
                    totalDaysInMonth = totalDaysInMonth,
                    weeklyBreakdown = weeklyBreakdown,
                    bestNightScore = bestNight?.compositeScore,
                    bestNightDate = bestNight?.sessionDate,
                    worstNightScore = worstNight?.compositeScore,
                    worstNightDate = worstNight?.sessionDate,
                    monthInsight = insightText,
                    sleepRecords = sleepRecords,
                    sessions = sessions,
                    targetBedtime = targetBedtime,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e("MonthlyReportVM", "Failed to load monthly report", e)
                if (!networkObserver.checkNow()) {
                    _state.value = _state.value.copy(isOffline = true)
                }
            }
        }
    }

    private fun computeMonthlyAdherence(
        monthStart: LocalDate,
        monthEnd: LocalDate,
        targetBedtime: String?,
        sleepRecords: List<SleepRecord>
    ): Double {
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

        return if (totalDays > 0) (adherentDays * 100.0 / totalDays) else 0.0
    }

    private fun computeWeeklyBreakdown(
        yearMonth: YearMonth,
        targetBedtime: String?,
        sleepRecords: List<SleepRecord>,
        sessions: List<RoutineSession>
    ): List<WeeklyBreakdownData> {
        val targetTime = try {
            targetBedtime?.let { java.time.LocalTime.parse(it) }
        } catch (e: Exception) { null }

        val monthStart = yearMonth.atDay(1)
        val monthEnd = yearMonth.atEndOfMonth()

        // Define 4 weeks
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

            // Compute BAR for this week
            var totalDays = 0
            var adherentDays = 0
            var current = weekStart
            val byDate = weekSleepRecords.associateBy { it.sessionDate }
            while (!current.isAfter(weekEnd)) {
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

            val barRate = if (totalDays > 0) (adherentDays * 100.0 / totalDays) else 0.0
            val completedCount = weekSessions.count { it.status == "COMPLETED" }
            val avgScore = weekSleepRecords
                .mapNotNull { it.compositeScore }
                .takeIf { it.isNotEmpty() }
                ?.average()

            WeeklyBreakdownData(
                weekNumber = weekNum,
                barRate = barRate,
                routinesCompleted = completedCount,
                daysInWeek = totalDays,
                avgSleepScore = avgScore
            )
        }
    }

    fun exportReport(context: Context) {
        val currentState = _state.value
        if (currentState.isLoading || currentState.sleepRecords.isEmpty()) return

        viewModelScope.launch {
            _exportState.value = ExportState.Loading
            try {
                val userId = UserSession.getUserId(context) ?: throw Exception("User not logged in")
                val yearMonth = currentState.yearMonth

                val reportModel = MonthlyReportModel(
                    userId = userId,
                    month = yearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM")),
                    bedtimeAdherenceRate = currentState.bedtimeAdherenceRate,
                    totalRoutinesCompleted = currentState.totalRoutinesCompleted,
                    totalDaysInMonth = currentState.totalDaysInMonth,
                    avgSleepQualityScore = currentState.avgSleepQualityScore ?: 0.0,
                    weeklyBreakdown = currentState.weeklyBreakdown.map {
                        WeeklyBreakdownModel(
                            weekNumber = it.weekNumber,
                            barRate = it.barRate,
                            routinesCompleted = it.routinesCompleted,
                            daysInWeek = it.daysInWeek,
                            avgSleepScore = it.avgSleepScore
                        )
                    },
                    bestNightScore = currentState.bestNightScore,
                    bestNightDate = currentState.bestNightDate,
                    worstNightScore = currentState.worstNightScore,
                    worstNightDate = currentState.worstNightDate,
                    monthInsight = currentState.monthInsight,
                    exportedAt = Instant.now().toString()
                )

                val jsonString = Json.encodeToString(reportModel)
                val fileName = "noctra_monthly_${yearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))}.json"

                // Save to app's files directory
                val file = File(context.filesDir, fileName)
                file.writeText(jsonString)

                _exportState.value = ExportState.Success(
                    url = file.absolutePath
                )
            } catch (e: Exception) {
                Log.e("MonthlyReportVM", "Export failed", e)
                _exportState.value = ExportState.Error(e.message ?: "Export failed")
            }
        }
    }

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }
}

data class MonthlyReportUiState(
    val yearMonth: YearMonth = YearMonth.now(),
    val monthLabel: String = YearMonth.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")),
    val bedtimeAdherenceRate: Double = 0.0,
    val avgSleepQualityScore: Double? = null,
    val totalRoutinesCompleted: Int = 0,
    val totalDaysInMonth: Int = 30,
    val weeklyBreakdown: List<WeeklyBreakdownData> = emptyList(),
    val bestNightScore: Int? = null,
    val bestNightDate: String? = null,
    val worstNightScore: Int? = null,
    val worstNightDate: String? = null,
    val monthInsight: String? = null,
    val sleepRecords: List<SleepRecord> = emptyList(),
    val sessions: List<RoutineSession> = emptyList(),
    val targetBedtime: String? = null,
    val isLoading: Boolean = true,
    val isOffline: Boolean = false
)

data class WeeklyBreakdownData(
    val weekNumber: Int,
    val barRate: Double,
    val routinesCompleted: Int,
    val daysInWeek: Int,
    val avgSleepScore: Double?
)

sealed class ExportState {
    object Idle : ExportState()
    object Loading : ExportState()
    data class Success(val url: String) : ExportState()
    data class Error(val message: String) : ExportState()
}
