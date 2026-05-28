package com.noctra.app.ui.routine.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noctra.app.data.model.Activity
import com.noctra.app.data.model.RoutineConfiguration
import com.noctra.app.data.repository.RoutineRepository
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * RoutineHomeViewModel
 *
 * Drives RoutineHomeFragment state. Determines:
 *   - Whether the user has a configured routine
 *   - The current routine window state (before / in window / completed)
 *   - The activity list preview for the home screen
 *   - The current streak count
 *
 * Routine Window Logic (from Noctra context doc):
 *   - Window OPENS  at: target_bedtime − total_routine_duration_minutes
 *   - Window CLOSES at: target_bedtime + 60 minutes
 *   - Outside window  → BeforeWindow (Edit Routine button)
 *   - Inside window   → InWindow (Begin Routine button)
 *   - Already done    → Completed (Routine Complete state)
 *
 * Window can cross midnight (e.g. bedtime 23:30 + 30min activities → window 23:00–00:30).
 * The isTimeInWindow() helper handles this rollover.
 */
class RoutineHomeViewModel(application: Application) : AndroidViewModel(application) {

    // ─── Repositories ────────────────────────────────────────────────────────

    private val routineRepository        = RoutineRepository()
    private val routineSessionRepository = RoutineSessionRepository()
    private val userProfileRepository    = UserProfileRepository()

    // ─── User ID ─────────────────────────────────────────────────────────────

    private val userId: String
        get() = UserSession.getUserId(getApplication())

    // ─── UI State ────────────────────────────────────────────────────────────

    sealed class RoutineHomeState {
        object Loading : RoutineHomeState()
        object NoRoutine : RoutineHomeState()

        data class BeforeWindow(
            val activities: List<Activity>,
            val totalDurationMinutes: Int,
            val targetBedtime: String,
            val routineStartTime: String,
            val currentStreak: Int
        ) : RoutineHomeState()

        data class InWindow(
            val activities: List<Activity>,
            val totalDurationMinutes: Int,
            val targetBedtime: String,
            val routineStartTime: String,
            val currentStreak: Int
        ) : RoutineHomeState()

        data class Completed(val currentStreak: Int) : RoutineHomeState()
        data class Error(val message: String) : RoutineHomeState()
    }

    private val _state = MutableStateFlow<RoutineHomeState>(RoutineHomeState.Loading)
    val state: StateFlow<RoutineHomeState> = _state.asStateFlow()

    // ─── Active Routine (cached for StartFragment navigation) ────────────────

    private var _activeRoutine: RoutineConfiguration? = null

    val activeRoutineConfigId: String?
        get() = _activeRoutine?.id

    // ─── Demo override ───────────────────────────────────────────────────────

    /**
     * If true, the window check is bypassed and InWindow is forced regardless
     * of real time. Useful for demoing the app at any time of day.
     *
     * Set this to false before release.
     */
    private val FORCE_IN_WINDOW_FOR_DEMO = true

    /**
     * If true, we ignore the database check for already completed sessions.
     * This allows the user to re-run the routine execution screens multiple
     * times in one day for demo purposes.
     */
    private var skipCompletionCheckForDemo = false

    // ─── Init ────────────────────────────────────────────────────────────────

    init {
        loadHomeState()
    }

    fun refresh() {
        loadHomeState()
    }

    fun forceResetForDemo() {
        skipCompletionCheckForDemo = true
        loadHomeState()
    }

    // ─── Core Load Logic ─────────────────────────────────────────────────────

    private fun loadHomeState() {
        viewModelScope.launch {
            _state.value = RoutineHomeState.Loading

            try {
                val profile = userProfileRepository.getOrCreateProfile(userId)
                val targetBedtimeRaw = profile.targetBedtime ?: "22:00:00"

                val activeRoutine = routineRepository.getActiveRoutine(userId)
                if (activeRoutine == null) {
                    _state.value = RoutineHomeState.NoRoutine
                    return@launch
                }
                _activeRoutine = activeRoutine

                val entries = routineRepository.parseActivitySequence(activeRoutine.activitySequence)
                val activities = routineRepository.hydrateActivitySequence(entries)
                val totalDuration = activeRoutine.totalDurationMinutes

                // Check tonight's completion BEFORE window logic — completion wins.
                val todayDate = routineSessionRepository.getTodayDateString()
                val alreadyCompleted = if (skipCompletionCheckForDemo) false else {
                    routineSessionRepository.hasCompletedSessionForDate(userId, todayDate)
                }
                val streak = routineSessionRepository.getCurrentStreak(userId)
                android.util.Log.d("StreakDebug", "HOME: reading as userId=$userId, got streak=$streak")

                if (alreadyCompleted) {
                    _state.value = RoutineHomeState.Completed(currentStreak = streak)
                    return@launch
                }

                // Compute the routine window.
                val targetBedtime = parseTime(targetBedtimeRaw)
                val windowOpen    = targetBedtime.minusMinutes(totalDuration.toLong())
                val windowClose   = targetBedtime.plusMinutes(60)

                val inWindow = FORCE_IN_WINDOW_FOR_DEMO ||
                        isTimeInWindow(LocalTime.now(), windowOpen, windowClose)

                _state.value = if (inWindow) {
                    RoutineHomeState.InWindow(
                        activities           = activities,
                        totalDurationMinutes = totalDuration,
                        targetBedtime        = formatTime(targetBedtime),
                        routineStartTime     = formatTime(windowOpen),
                        currentStreak        = streak
                    )
                } else {
                    RoutineHomeState.BeforeWindow(
                        activities           = activities,
                        totalDurationMinutes = totalDuration,
                        targetBedtime        = formatTime(targetBedtime),
                        routineStartTime     = formatTime(windowOpen),
                        currentStreak        = streak
                    )
                }

            } catch (e: Exception) {
                _state.value = RoutineHomeState.Error(
                    message = e.message ?: "Something went wrong loading your routine."
                )
            }
        }
    }

    // ─── Window check (handles midnight rollover) ────────────────────────────

    /**
     * Returns true if `now` falls within [open, close], correctly handling the
     * case where the window crosses midnight.
     *
     * Examples:
     *   open=21:30 close=23:00  → simple within-day check
     *   open=23:00 close=00:30  → crosses midnight; now must be ≥23:00 OR ≤00:30
     *   open=00:30 close=02:00  → simple within-day check (early-morning bedtime)
     */
    private fun isTimeInWindow(now: LocalTime, open: LocalTime, close: LocalTime): Boolean {
        return if (!open.isAfter(close)) {
            // No midnight crossing (open <= close)
            !now.isBefore(open) && !now.isAfter(close)
        } else {
            // Window crosses midnight (open > close)
            !now.isBefore(open) || !now.isAfter(close)
        }
    }

    // ─── Time formatting helpers ─────────────────────────────────────────────

    private fun parseTime(raw: String): LocalTime {
        return try {
            LocalTime.parse(raw, DateTimeFormatter.ofPattern("HH:mm:ss"))
        } catch (e: Exception) {
            LocalTime.parse(raw, DateTimeFormatter.ofPattern("HH:mm"))
        }
    }

    private fun formatTime(time: LocalTime): String {
        return time.format(DateTimeFormatter.ofPattern("h:mm a"))
    }
}