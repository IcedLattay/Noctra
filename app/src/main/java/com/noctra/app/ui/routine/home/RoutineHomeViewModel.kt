package com.noctra.app.ui.routine.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noctra.app.data.model.Activity
import com.noctra.app.data.model.RoutineConfiguration
import com.noctra.app.data.repository.RoutineRepository
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.utils.NetworkObserver
import com.noctra.app.utils.DebugSettings
import com.noctra.app.data.utils.RoutinePersistenceHelper
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * RoutineHomeViewModel
 *
 * Drives RoutineHomeFragment state. Determines:
 *   - Whether the user has a configured routine
 *   - The current routine window state (before / in window / completed /
 *     resumable)
 *   - The activity list preview for the home screen
 *   - The current streak count
 *
 * Routine Window Logic (from Noctra context doc):
 *   - Window OPENS  at: target_bedtime − total_routine_duration_minutes
 *   - Window CLOSES at: target_bedtime + 60 minutes
 *   - Outside window  → BeforeWindow (Edit Routine button)
 *   - Inside window   → InWindow (Begin Routine button)
 *   - Already done    → Completed (Routine Complete state)
 *   - Session left mid-routine → Resumable (Resume Routine button) — NEW,
 *     Flag 26. Checked via RoutinePersistenceHelper.hasActiveSession(),
 *     same local cache the Resume Dialog (MainActivity) reads. This gives
 *     users a way back into an unfinished routine even if they dismissed
 *     the dialog with "Not Now" and didn't reopen the app to re-trigger it.
 *
 * Window can cross midnight (e.g. bedtime 23:30 + 30min activities → window 23:00–00:30).
 * The isTimeInWindow() helper handles this rollover.
 */
class RoutineHomeViewModel(application: Application) : AndroidViewModel(application) {

    // ─── Repositories ────────────────────────────────────────────────────────

    private val routineRepository        = RoutineRepository()
    private val routineSessionRepository = RoutineSessionRepository()
    private val userProfileRepository    = UserProfileRepository()
    private val networkObserver          = NetworkObserver(getApplication())

    // ─── User ID ─────────────────────────────────────────────────────────────

    private val userId: String?
        get() = UserSession.getUserId(getApplication())

    // ─── UI State ────────────────────────────────────────────────────────────

    sealed class RoutineHomeState {
        object Loading : RoutineHomeState()
        object Offline : RoutineHomeState()
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

        /**
         * NEW (Flag 26): there's an unfinished routine session cached
         * locally. resumeStepIndex is 0-based, matching what
         * RoutineViewModel.confirmResume() will actually resume at.
         */
        data class Resumable(
            val activities: List<Activity>,
            val totalDurationMinutes: Int,
            val currentStreak: Int,
            val resumeStepIndex: Int
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

    // ─── Init ────────────────────────────────────────────────────────────────

    init {
        loadHomeState()
        observeDebugSettings()
    }

    private fun observeDebugSettings() {
        viewModelScope.launch {
            DebugSettings.forceRoutineWindow.collectLatest { loadHomeState() }
        }
        viewModelScope.launch {
            DebugSettings.skipCompletionCheck.collectLatest { loadHomeState() }
        }
    }

    fun refresh() {
        loadHomeState()
    }

    fun retry() {
        _state.value = RoutineHomeState.Loading
        loadHomeState()
    }

    fun forceResetForDemo() {
        DebugSettings.setSkipCompletionCheck(true)
    }

    // ─── Core Load Logic ─────────────────────────────────────────────────────

    private fun loadHomeState() {
        viewModelScope.launch {
            if (!networkObserver.checkNow()) {
                _state.value = RoutineHomeState.Offline
                return@launch
            }
            _state.value = RoutineHomeState.Loading
            val userId = userId ?: return@launch

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

                // FLAG 26 — Resumable check. Runs before the completion/window
                // checks below: if there's a session cached locally, it can't
                // also be "already completed today" (the cache is cleared in
                // RoutineViewModel.completeSession()), so there's no conflict
                // to resolve — this just short-circuits straight to the
                // Resumable state.
                if (RoutinePersistenceHelper.hasActiveSession()) {
                    val streak = routineSessionRepository.getCurrentStreak(userId)
                    _state.value = RoutineHomeState.Resumable(
                        activities = activities,
                        totalDurationMinutes = totalDuration,
                        currentStreak = streak,
                        resumeStepIndex = RoutinePersistenceHelper.getCurrentStepIndex()
                    )
                    return@launch
                }

                // Check tonight's completion BEFORE window logic — completion wins.
                // "Tonight" runs to 3 AM (latest window closes then), so a
                // post-midnight open still sees the completed state.
                val todayDate = com.noctra.app.utils.RoutineWindowProvider
                    .resolveRoutineScreenDate().toString()
                val alreadyCompleted = if (DebugSettings.skipCompletionCheck.value) false else {
                    routineSessionRepository.hasCompletedSessionForDate(userId, todayDate)
                }
                val streak = routineSessionRepository.getCurrentStreak(userId)
                android.util.Log.d("StreakDebug", "HOME: reading as userId=$userId, got streak=$streak")

                if (alreadyCompleted) {
                    _state.value = RoutineHomeState.Completed(currentStreak = streak)
                    return@launch
                }

                // Compute the routine window.
                val inWindow = DebugSettings.forceRoutineWindow.value ||
                        com.noctra.app.utils.RoutineWindowProvider.isTimeInWindow(
                            now = LocalTime.now(),
                            targetBedtime = targetBedtimeRaw,
                            routineDurationMinutes = totalDuration
                        )

                _state.value = if (inWindow) {
                    val targetBedtimeParsed = parseTime(targetBedtimeRaw)
                    val windowOpen = targetBedtimeParsed.minusMinutes(totalDuration.toLong())
                    RoutineHomeState.InWindow(
                        activities           = activities,
                        totalDurationMinutes = totalDuration,
                        targetBedtime        = formatTime(targetBedtimeParsed),
                        routineStartTime     = formatTime(windowOpen),
                        currentStreak        = streak
                    )
                } else {
                    val targetBedtimeParsed = parseTime(targetBedtimeRaw)
                    val windowOpen = targetBedtimeParsed.minusMinutes(totalDuration.toLong())
                    RoutineHomeState.BeforeWindow(
                        activities           = activities,
                        totalDurationMinutes = totalDuration,
                        targetBedtime        = formatTime(targetBedtimeParsed),
                        routineStartTime     = formatTime(windowOpen),
                        currentStreak        = streak
                    )
                }

            } catch (e: Exception) {
                android.util.Log.e("RoutineHomeVM", "Load failed: ${e.javaClass.simpleName}: ${e.message}", e)
                if (!networkObserver.checkNow()) {
                    _state.value = RoutineHomeState.Offline
                } else {
                    _state.value = RoutineHomeState.Error(
                        message = e.message ?: "Something went wrong loading your routine."
                    )
                }
            }
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