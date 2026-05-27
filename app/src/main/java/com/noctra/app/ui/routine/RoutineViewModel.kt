package com.noctra.app.ui.routine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noctra.app.data.model.Activity
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.domain.usecase.RewardCalculationUseCase
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * RoutineViewModel
 *
 * The core execution engine for the nightly routine flow.
 * Scoped to MainActivity via activityViewModels() — shared across:
 *   - RoutineStartFragment
 *   - BreathingActivityFragment
 *   - AudioscapeActivityFragment
 *   - GratitudeJournalingActivityFragment
 *   - GenericTimerActivityFragment
 *   - RoutineCompletionOverlayFragment
 *
 * Responsibilities:
 *   1. Holds the activity list and tracks the current step
 *   2. Manages the 60-minute background session timer
 *   3. Manages the per-activity countdown timer
 *   4. Auto-transitions between activities when the timer expires
 *   5. Handles manual completion via "Complete Routine" button
 *   6. Handles exit via "Exit Routine" button
 *   7. Saves the session to Supabase on completion
 *   8. Calculates and exposes rewards for the completion overlay
 */
class RoutineViewModel(application: Application) : AndroidViewModel(application) {

    // ─── Dependencies ─────────────────────────────────────────────────────────

    private val routineSessionRepository = RoutineSessionRepository()
    private val rewardCalculationUseCase = RewardCalculationUseCase()

    private val userId: String
        get() = UserSession.getUserId(getApplication())

    // ─── Session Setup (set before starting) ─────────────────────────────────

    /** Set by RoutineHomeFragment before navigating to RoutineStartFragment. */
    var activities: List<Activity> = emptyList()
        private set

    var routineConfigId: String = ""
        private set

    var currentStreak: Int = 0
        private set

    /**
     * Called by RoutineHomeFragment after it resolves the active routine.
     * Must be called before the user taps Start.
     */
    fun setupSession(
        activities: List<Activity>,
        routineConfigId: String,
        currentStreak: Int
    ) {
        this.activities      = activities
        this.routineConfigId = routineConfigId
        this.currentStreak   = currentStreak
        _sessionState.value  = SessionState.Ready
    }

    // ─── Session State ────────────────────────────────────────────────────────

    sealed class SessionState {
        /** setupSession() called; waiting for user to tap Start. */
        object Ready : SessionState()

        /** User tapped Start; timers running. */
        object InProgress : SessionState()

        /** User tapped Complete Routine or fell asleep detection. */
        object Completed : SessionState()

        /** User tapped Exit Routine and confirmed. */
        object Exited : SessionState()
    }

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Ready)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    // ─── Step Tracking ────────────────────────────────────────────────────────

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    /** Convenience — the Activity object for the current step. */
    val currentActivity: Activity?
        get() = activities.getOrNull(_currentStepIndex.value)

    val isLastStep: Boolean
        get() = _currentStepIndex.value == activities.size - 1

    // ─── Session Timer (60-minute background cap) ─────────────────────────────

    private val SESSION_DURATION_SECONDS = 60 * 60 // 60 minutes

    private val _sessionSecondsRemaining = MutableStateFlow(SESSION_DURATION_SECONDS)
    val sessionSecondsRemaining: StateFlow<Int> = _sessionSecondsRemaining.asStateFlow()

    private var sessionTimerJob: Job? = null

    // ─── Activity Timer (per-activity countdown) ──────────────────────────────

    private val _activitySecondsRemaining = MutableStateFlow(0)
    val activitySecondsRemaining: StateFlow<Int> = _activitySecondsRemaining.asStateFlow()

    private var activityTimerJob: Job? = null

    // ─── Navigation Events (one-shot) ─────────────────────────────────────────

    /**
     * Emits the index to navigate to next.
     * Observed by the host or individual fragments to trigger navigation.
     */
    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    sealed class NavigationEvent {
        /** Navigate to the activity screen at the given index. */
        data class GoToActivity(val index: Int) : NavigationEvent()

        /** All activities done — navigate to completion overlay. */
        object GoToCompletion : NavigationEvent()

        /** User exited — navigate back to Routine Home. */
        object GoToHome : NavigationEvent()

        /** Play the transition chime between activities. */
        object PlayChime : NavigationEvent()
    }

    // ─── Reward Result ────────────────────────────────────────────────────────

    private val _rewardResult = MutableStateFlow<RewardCalculationUseCase.RewardResult?>(null)
    val rewardResult: StateFlow<RewardCalculationUseCase.RewardResult?> = _rewardResult.asStateFlow()

    // ─── Session Tracking ─────────────────────────────────────────────────────

    private var activeSessionId: String? = null
    private var sessionStartTimestamp: String = ""

    // ─── Public: Session Lifecycle ────────────────────────────────────────────

    /**
     * Called when the user taps "Start" on RoutineStartFragment.
     * Creates the session row in Supabase and starts both timers.
     */
    fun startSession() {
        viewModelScope.launch {
            _sessionState.value = SessionState.InProgress
            _currentStepIndex.value = 0

            val now = LocalDateTime.now()
            sessionStartTimestamp = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val todayDate = LocalDate.now().toString()

            // Create session row in Supabase
            try {
                val session = routineSessionRepository.startSession(
                    userId          = userId,
                    routineConfigId = routineConfigId,
                    sessionDate     = todayDate,
                    startTimestamp  = sessionStartTimestamp
                )
                activeSessionId = session.id
            } catch (e: Exception) {
                // Non-fatal — session will still run locally; retry on completion
                activeSessionId = null
            }

            // Start both timers
            startSessionTimer()
            startActivityTimer(activities[0].defaultDurationMinutes * 60)

            // Navigate to first activity
            _navigationEvent.emit(NavigationEvent.GoToActivity(0))
        }
    }

    /**
     * Called when the current activity's timer expires (auto-transition)
     * OR when the user completes the last activity manually.
     */
    fun onActivityComplete() {
        viewModelScope.launch {
            activityTimerJob?.cancel()

            if (isLastStep) {
                // Final activity — go to completion
                completeSession()
            } else {
                // Advance to next activity
                val nextIndex = _currentStepIndex.value + 1
                _currentStepIndex.value = nextIndex

                // Emit chime event then navigate
                _navigationEvent.emit(NavigationEvent.PlayChime)
                delay(800) // brief pause for chime to play

                val nextActivity = activities[nextIndex]
                startActivityTimer(nextActivity.defaultDurationMinutes * 60)
                _navigationEvent.emit(NavigationEvent.GoToActivity(nextIndex))
            }
        }
    }

    /**
     * Called when the user taps "Complete Routine" on the final activity screen.
     * Same as onActivityComplete() when on the last step — exposed separately
     * for clarity in the Fragment.
     */
    fun onCompleteRoutineTapped() {
        onActivityComplete()
    }

    /**
     * Called when the user confirms "Exit Routine" from the dialog.
     * Cancels all timers. Does NOT save a completed session.
     * Streak and tokens are NOT awarded.
     */
    fun onExitConfirmed() {
        viewModelScope.launch {
            cancelAllTimers()
            _sessionState.value = SessionState.Exited
            _navigationEvent.emit(NavigationEvent.GoToHome)
        }
    }

    /**
     * Resets the ViewModel for a fresh session.
     * Called after the completion overlay finishes or the user returns home.
     */
    fun reset() {
        cancelAllTimers()
        _currentStepIndex.value       = 0
        _sessionSecondsRemaining.value = SESSION_DURATION_SECONDS
        _activitySecondsRemaining.value = 0
        _sessionState.value           = SessionState.Ready
        _rewardResult.value           = null
        activeSessionId               = null
        sessionStartTimestamp         = ""
    }

    // ─── Private: Completion ──────────────────────────────────────────────────

    private fun completeSession() {
        viewModelScope.launch {
            cancelAllTimers()
            _sessionState.value = SessionState.Completed

            val completionTimestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

            // Calculate rewards
            val reward = rewardCalculationUseCase.calculate(currentStreak)
            _rewardResult.value = reward

            // Save to Supabase
            activeSessionId?.let { sessionId ->
                try {
                    routineSessionRepository.completeSession(
                        sessionId            = sessionId,
                        completionTimestamp  = completionTimestamp,
                        streakAtCompletion   = currentStreak + 1,
                        multiplierApplied    = reward.multiplierApplied,
                        tokensEarned         = reward.tokensEarned,
                        xpEarned             = reward.xpEarned
                    )
                } catch (e: Exception) {
                    // TODO: Queue for retry if offline
                }
            }

            _navigationEvent.emit(NavigationEvent.GoToCompletion)
        }
    }

    // ─── Private: Timers ──────────────────────────────────────────────────────

    /**
     * Starts the 60-minute background session timer.
     * When it reaches zero, the session is treated as timed-out —
     * MorningSyncWorker will check sleep onset in the morning.
     */
    private fun startSessionTimer() {
        sessionTimerJob?.cancel()
        sessionTimerJob = viewModelScope.launch {
            while (_sessionSecondsRemaining.value > 0) {
                delay(1000)
                _sessionSecondsRemaining.value--
            }
            // 60-min cap reached — mark session as timed out
            // MorningSyncWorker handles the rest in the morning
            cancelAllTimers()
        }
    }

    /**
     * Starts the per-activity countdown timer.
     * When it reaches zero, onActivityComplete() is called automatically.
     *
     * @param durationSeconds Duration for the current activity in seconds.
     */
    private fun startActivityTimer(durationSeconds: Int) {
        activityTimerJob?.cancel()
        _activitySecondsRemaining.value = durationSeconds
        activityTimerJob = viewModelScope.launch {
            while (_activitySecondsRemaining.value > 0) {
                delay(1000)
                _activitySecondsRemaining.value--
            }
            // Timer expired — auto-transition
            onActivityComplete()
        }
    }

    private fun cancelAllTimers() {
        sessionTimerJob?.cancel()
        activityTimerJob?.cancel()
        sessionTimerJob  = null
        activityTimerJob = null
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        cancelAllTimers()
    }
}