package com.noctra.app.ui.analytics

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noctra.app.data.model.RoutineSession
import com.noctra.app.data.model.SleepRecord
import com.noctra.app.data.model.UserProfile
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.data.repository.SleepRecordRepository
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.domain.usecase.BedtimeAdherenceCalculator
import com.noctra.app.domain.usecase.InsightGenerationUseCase
import com.noctra.app.utils.NetworkObserver
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.sqrt

// Window sizes and thresholds (file scope so AnalyticsUiState can use them).
/** §2.1 the 7-day detail block. */
const val DETAIL_DAYS = 7
/** §2.2 the 30-day trend window. */
const val TREND_DAYS = 30
/** §2.2 the trend stays gated until roughly two weeks of nights exist. */
const val TREND_UNLOCK_DAYS = 14
/** §2.2 trailing window for the weekly average line. */
private const val TREND_AVG_WINDOW = 7
/** §2.2 minimum scored nights in that window before the average draws. */
private const val TREND_AVG_MIN_SAMPLES = 4

/**
 * Data layer for the revamped analytics screen (ANALYTICS_SPEC.md).
 *
 * §5: all reads go through this ViewModel; no view queries anything directly.
 * §3: one batched load covers the whole screen, so every card renders from the
 * same snapshot and the insight can never disagree with the charts beside it.
 * §3: two clocks that never share state — [detailStart] moves a day, [trendStart]
 * moves a week, and neither disturbs the other.
 */
class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val sleepRepo = SleepRecordRepository()
    private val sessionRepo = RoutineSessionRepository()
    private val profileRepo = UserProfileRepository()
    private val insightUseCase = InsightGenerationUseCase()
    private val adherenceCalculator = BedtimeAdherenceCalculator()
    private val networkObserver = NetworkObserver(application)

    private val _state = MutableStateFlow(AnalyticsUiState())
    val state = _state.asStateFlow()

    // ─── Window bounds ────────────────────────────────────────────────────

    /** Last day covered by a window starting at [start]. */
    private fun detailEnd(start: LocalDate): LocalDate =
        start.plusDays((DETAIL_DAYS - 1).toLong())

    private fun trendEnd(start: LocalDate): LocalDate =
        start.plusDays((TREND_DAYS - 1).toLong())

    // ─── Navigation: two independent clocks ───────────────────────────────

    /** First day of the 7-day block. Moves ±1 day per tap (§3 small steps). */
    fun shiftDetail(days: Long) {
        val anchor = _state.value.firstDataDate
        val today = LocalDate.now()
        val current = _state.value.detailStart
        // A 7-day window may not run past today, and may not start before the
        // first recorded night.
        val next = current.plusDays(days)
        if (next.isBefore(anchor)) return
        if (detailEnd(next).isAfter(today)) return
        _state.value = _state.value.copy(detailStart = next)
        refresh()
    }

    /** First day of the 30-day trend. Moves ±7 days per tap. */
    fun shiftTrend(weeks: Long) {
        val anchor = _state.value.firstDataDate
        val today = LocalDate.now()
        val current = _state.value.trendStart
        val next = current.plusWeeks(weeks)
        if (next.isBefore(anchor)) return
        if (trendEnd(next).isAfter(today)) return
        _state.value = _state.value.copy(trendStart = next)
        refresh()
    }

    fun canShiftDetailBack(): Boolean = _state.value.detailStart > _state.value.firstDataDate
    fun canShiftDetailForward(): Boolean = detailEnd(_state.value.detailStart) < LocalDate.now()
    fun canShiftTrendBack(): Boolean = _state.value.trendStart > _state.value.firstDataDate
    fun canShiftTrendForward(): Boolean = trendEnd(_state.value.trendStart) < LocalDate.now()

    // ─── Lifecycle ────────────────────────────────────────────────────────

    fun load(context: Context) {
        if (_state.value.hasLoaded) {
            // §5: revisit-only data, reload on revisit covers it.
            refresh()
            return
        }
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            val userId = UserSession.getUserId(context) ?: return@launch
            if (!networkObserver.checkNow()) {
                _state.value = _state.value.copy(isOffline = true, isLoading = false)
                return@launch
            }
            try {
                val firstDate = resolveFirstDataDate(userId)
                val today = LocalDate.now()
                // Default both windows to the most recent stretch: latest 7 / 30
                // ending today, clamped to the first recorded night for young
                // accounts (which then get the spec's fixed first week).
                val detail = maxOf(firstDate, today.minusDays((DETAIL_DAYS - 1).toLong()))
                val trend = maxOf(firstDate, today.minusDays((TREND_DAYS - 1).toLong()))
                _state.value = _state.value.copy(
                    firstDataDate = firstDate,
                    detailStart = detail,
                    trendStart = trend
                )
                _state.value = _state.value.copy(hasLoaded = true)
                refresh()
            } catch (e: Exception) {
                Log.e(TAG, "Initial analytics load failed", e)
                _state.value = _state.value.copy(isLoading = false, isOffline = true)
            }
        }
    }

    fun retry(context: Context) {
        _state.value = _state.value.copy(isOffline = false, isLoading = true, hasLoaded = false)
        load(context)
    }

    /**
     * Re-fetches everything for the current windows. Both windows are refetched
     * together so the insight and the charts always agree, even though their
     * range controls are independent (§3).
     */
    private fun refresh() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val userId = UserSession.getUserId(context) ?: return@launch
            if (!networkObserver.checkNow()) {
                _state.value = _state.value.copy(isOffline = true, isLoading = false)
                return@launch
            }
            _state.value = _state.value.copy(isLoading = true)
            try {
                val s = _state.value
                val today = LocalDate.now()

                // Insight always trails 30 days from today, independent of both
                // windows (§2.3).
                val insightFrom = today.minusDays(29)

                coroutineScope {
                    val detailRecords = async {
                        sleepRepo.getRecordsInRange(
                            userId,
                            s.detailStart.toString(),
                            detailEnd(s.detailStart).toString()
                        )
                    }
                    val detailSessions = async {
                        sessionRepo.getSessionsInRange(
                            userId,
                            s.detailStart.toString(),
                            detailEnd(s.detailStart).toString()
                        )
                    }
                    val trendRecords = async {
                        sleepRepo.getRecordsInRange(
                            userId,
                            s.trendStart.toString(),
                            trendEnd(s.trendStart).toString()
                        )
                    }
                    val insightRecords = async {
                        sleepRepo.getRecordsInRange(userId, insightFrom.toString(), today.toString())
                    }
                    val insightSessions = async {
                        sessionRepo.getSessionsInRange(
                            userId, insightFrom.toString(), today.toString()
                        )
                    }
                    val lastNight = async { sleepRepo.getMostRecentRecord(userId) }
                    val profile = async { profileRepo.getOrCreateProfile(userId) }

                    val dr = detailRecords.await()
                    val ds = detailSessions.await()
                    val tr = trendRecords.await()
                    val ir = insightRecords.await()
                    val iss = insightSessions.await()
                    val ln = lastNight.await()
                    val p = profile.await()

                    _state.value = _state.value.copy(
                        isLoading = false,
                        isOffline = false,
                        targetBedtime = p.targetBedtime,
                        lastNightRecord = ln,
                        detailScores = buildScores(s.detailStart, dr, DETAIL_DAYS),
                        detailCompletion = buildCompletionStatuses(
                            s.detailStart, ds
                        ),
                        detailOnsets = buildOnsets(s.detailStart, dr, DETAIL_DAYS),
                        variabilitySd = standardDeviationMinutes(dr),
                        trendPoints = buildScores(s.trendStart, tr, TREND_DAYS),
                        trendAverages = trailingAverages(buildScores(s.trendStart, tr, TREND_DAYS)),
                        insightResult = insightUseCase.generate(ir, iss)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Analytics refresh failed", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    isOffline = !networkObserver.checkNow()
                )
            }
        }
    }

    // ─── Anchor resolution ────────────────────────────────────────────────

    /**
     * §3: "Week 1 starts at the first recorded data (earliest sleep_records or
     * routine_sessions date), not profile creation — the journey starts when
     * tracking starts. Falls back to profile created_at when no data exists yet."
     */
    private suspend fun resolveFirstDataDate(userId: String): LocalDate {
        val fromSleep = sleepRepo.getEarliestSessionDate(userId)
        val fromSessions = sessionRepo.getEarliestSessionDate(userId)
        val candidates = listOfNotNull(fromSleep, fromSessions)
            .mapNotNull { parseDate(it) }
        if (candidates.isNotEmpty()) return candidates.min()
        val profile = profileRepo.getOrCreateProfile(userId)
        return parseDate(profile.createdAt) ?: LocalDate.now()
    }

    // ─── Data shaping ─────────────────────────────────────────────────────

    /** One score per day, null where there is no record so the line breaks. */
    private fun buildScores(
        start: LocalDate,
        records: List<SleepRecord>,
        days: Int
    ): List<Int?> {
        val byDate = records.associateBy { it.sessionDate }
        return (0 until days).map { offset ->
            byDate[start.plusDays(offset.toLong()).toString()]?.compositeScore
        }
    }

    private fun buildOnsets(
        start: LocalDate,
        records: List<SleepRecord>,
        days: Int
    ): List<Instant?> {
        val byDate = records.associateBy { it.sessionDate }
        return (0 until days).map { offset ->
            val date = start.plusDays(offset.toLong())
            byDate[date.toString()]?.sleepOnsetTime?.let {
                runCatching { Instant.parse(it) }.getOrNull()
            }
        }
    }

    /**
     * §2.1: "a night with no record counts as missed"; PENDING stays yellow and
     * is never collapsed to pink. Every night from onboarding counts —
     * no exemptions.
     */
    private fun buildCompletionStatuses(
        start: LocalDate,
        sessions: List<RoutineSession>
    ): List<RoutineCompletionRowView.DayStatus> {
        val byDate = sessions.associateBy { it.sessionDate }
        val today = LocalDate.now()
        return (0 until DETAIL_DAYS).map { offset ->
            val date = start.plusDays(offset.toLong())
            val session = byDate[date.toString()]
            when {
                // §2.1: a night that has not happened yet is scaffolding, not a
                // missed night. Checked first so the future never falls through
                // to MISSED.
                date.isAfter(today) ->
                    RoutineCompletionRowView.DayStatus.UPCOMING
                session == null ->
                    RoutineCompletionRowView.DayStatus.MISSED
                session.status.equals("COMPLETED", ignoreCase = true) ->
                    RoutineCompletionRowView.DayStatus.COMPLETED
                session.status.equals("PENDING", ignoreCase = true) ->
                    RoutineCompletionRowView.DayStatus.PENDING
                else ->
                    RoutineCompletionRowView.DayStatus.MISSED
            }
        }
    }

    /**
     * §2.2: trailing-7 mean of scored nights inside the window. Gaps shrink the
     * divisor rather than zero-filling, and a window with fewer than
     * [TREND_AVG_MIN_SAMPLES] scored nights draws nothing.
     */
    private fun trailingAverages(points: List<Int?>): List<Double?> =
        points.indices.map { i ->
            val from = (i - TREND_AVG_WINDOW + 1).coerceAtLeast(0)
            val slice = points.subList(from, i + 1).filterNotNull()
            if (slice.size < TREND_AVG_MIN_SAMPLES) null else slice.average()
        }

    /**
     * §2.1 variability caption: standard deviation of the onsets currently on
     * screen, in whole minutes.
     */
    private fun standardDeviationMinutes(
        records: List<SleepRecord>
    ): Int? {
        val target = _state.value.targetBedtime?.let { parseTargetBedtime(it) }
        val minutes = records.mapNotNull { record ->
            val onset = record.sleepOnsetTime?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(record.sessionDate) }.getOrNull()
                ?: return@mapNotNull null
            deviationMinutes(date, target, onset)
        }
        if (minutes.size < 2) return null
        val mean = minutes.average()
        val variance = minutes.sumOf { (it - mean) * (it - mean) } / minutes.size
        return sqrt(variance).toInt()
    }

    private fun deviationMinutes(date: LocalDate, target: LocalTime?, onset: Instant): Double? {
        if (target == null) return null
        val targetInstant = if (target.hour >= 20) {
            date.atTime(target).atZone(ZoneId.systemDefault()).toInstant()
        } else {
            date.plusDays(1).atTime(target).atZone(ZoneId.systemDefault()).toInstant()
        }
        return (onset.epochSecond - targetInstant.epochSecond) / 60.0
    }

    // ─── Parsing helpers ──────────────────────────────────────────────────

    private fun parseDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
    }

    private fun parseTargetBedtime(stored: String?): LocalTime? {
        if (stored.isNullOrBlank()) return null
        return runCatching { LocalTime.parse(stored) }.getOrNull()
    }

    companion object {
        private const val TAG = "AnalyticsViewModel"
    }
}

// ─── State ────────────────────────────────────────────────────────────────

data class AnalyticsUiState(
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,

    /** First recorded night; both windows anchor here. */
    val firstDataDate: LocalDate = LocalDate.now(),

    // ── 7-day detail block (moves ±1 day) ──
    val detailStart: LocalDate = LocalDate.now(),
    val detailScores: List<Int?> = List(7) { null },
    val detailCompletion: List<RoutineCompletionRowView.DayStatus> =
        List(7) { RoutineCompletionRowView.DayStatus.MISSED },
    val detailOnsets: List<Instant?> = List(7) { null },
    val variabilitySd: Int? = null,

    // ── 30-day trend (moves ±7 days) ──
    val trendStart: LocalDate = LocalDate.now(),
    val trendPoints: List<Int?> = List(30) { null },
    val trendAverages: List<Double?> = List(30) { null },

    // ── Insight (always trails 30 days from today) ──
    val insightResult: InsightGenerationUseCase.Result? = null,

    // ── Shared / navigation ──
    val lastNightRecord: SleepRecord? = null,
    val targetBedtime: String? = null
) {
    /**
     * §2.2 gate: the trend hides behind a placeholder until roughly two weeks of
     * history exist. This is a question about total history, not about the
     * current window, so a user with three weeks of data always sees it.
     */
    val hasEnoughForTrend: Boolean
        get() = ChronoUnit.DAYS.between(firstDataDate, LocalDate.now()) >= TREND_UNLOCK_DAYS
}
