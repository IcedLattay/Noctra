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

class RoutineViewModel(application: Application) : AndroidViewModel(application) {

    private val routineSessionRepository = RoutineSessionRepository()
    private val rewardCalculationUseCase = RewardCalculationUseCase()

    private val userId: String get() = UserSession.getUserId(getApplication())

    // ─── Session Setup ────────────────────────────────────────────────────────

    var activities: List<Activity> = emptyList(); private set
    var routineConfigId: String = ""; private set
    var currentStreak: Int = 0; private set

    fun setupSession(activities: List<Activity>, routineConfigId: String, currentStreak: Int) {
        this.activities = activities
        this.routineConfigId = routineConfigId
        this.currentStreak = currentStreak
        _sessionState.value = SessionState.Ready
    }

    sealed class SessionState {
        object Ready : SessionState()
        object InProgress : SessionState()
        object Completed : SessionState()
        object Exited : SessionState()
    }

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Ready)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    // ─── Step Tracking ────────────────────────────────────────────────────────

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    val currentActivity: Activity? get() = activities.getOrNull(_currentStepIndex.value)
    val isLastStep: Boolean get() = _currentStepIndex.value == activities.size - 1

    // ─── Timers ───────────────────────────────────────────────────────────────

    private val SESSION_DURATION_SECONDS = 60 * 60
    private val _sessionSecondsRemaining = MutableStateFlow(SESSION_DURATION_SECONDS)
    val sessionSecondsRemaining: StateFlow<Int> = _sessionSecondsRemaining.asStateFlow()
    private var sessionTimerJob: Job? = null

    // For demo/testing: Force all activity execution timers to 15 seconds
    private val DEMO_ACTIVITY_DURATION_SECONDS = 15

    private val _activitySecondsRemaining = MutableStateFlow(0)
    val activitySecondsRemaining: StateFlow<Int> = _activitySecondsRemaining.asStateFlow()
    private var activityTimerJob: Job? = null

    // ─── Navigation Events ────────────────────────────────────────────────────

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>(extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    sealed class NavigationEvent {
        data class GoToActivity(val index: Int) : NavigationEvent()
        data class GoToTransition(val nextIndex: Int) : NavigationEvent()
        object GoToCompletion : NavigationEvent()
        object GoToHome : NavigationEvent()
    }

    // ─── Reward Result ────────────────────────────────────────────────────────

    private val _rewardResult = MutableStateFlow<RewardCalculationUseCase.RewardResult?>(null)
    val rewardResult: StateFlow<RewardCalculationUseCase.RewardResult?> = _rewardResult.asStateFlow()

    // ─── Session Tracking ─────────────────────────────────────────────────────

    private var activeSessionId: String? = null
    private var sessionStartTimestamp: String = ""

    // ─── Public Lifecycle ─────────────────────────────────────────────────────

    fun startSession() {
        viewModelScope.launch {
            _sessionState.value = SessionState.InProgress
            _currentStepIndex.value = 0

            val now = LocalDateTime.now()
            sessionStartTimestamp = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val todayDate = LocalDate.now().toString()

            try {
                val session = routineSessionRepository.startSession(
                    userId = userId,
                    routineConfigId = routineConfigId,
                    sessionDate = todayDate,
                    startTimestamp = sessionStartTimestamp
                )
                activeSessionId = session.id
            } catch (e: Exception) {
                activeSessionId = null
            }

            // Pre-populate display so the first activity shows the right time before it starts ticking
            _activitySecondsRemaining.value = DEMO_ACTIVITY_DURATION_SECONDS

            startSessionTimer()
            _navigationEvent.emit(NavigationEvent.GoToActivity(0))
        }
    }

    /**
     * Called by the active activity fragment when it's ready to begin
     * (e.g. after Breathing's 15s pre-countdown, or immediately for Audio/Journaling).
     * VM owns the countdown — fragments observe `activitySecondsRemaining` for display.
     */
    fun startCurrentActivityTimer() {
        if (currentActivity == null) return
        activityTimerJob?.cancel()
        _activitySecondsRemaining.value = DEMO_ACTIVITY_DURATION_SECONDS
        activityTimerJob = viewModelScope.launch {
            while (_activitySecondsRemaining.value > 0) {
                delay(1000)
                _activitySecondsRemaining.value--
            }
            // Timer ended — auto-complete this activity.
            // (Per SDD, last activity should require a manual Complete Routine tap; for MVP
            // we auto-complete to keep the flow working without that button.)
            onActivityComplete()
        }
    }

    fun onActivityComplete() {
        viewModelScope.launch {
            activityTimerJob?.cancel()
            if (isLastStep) {
                completeSession()
            } else {
                val nextIndex = _currentStepIndex.value + 1
                _currentStepIndex.value = nextIndex
                _navigationEvent.emit(NavigationEvent.GoToTransition(nextIndex))
            }
        }
    }

    /**
     * Called by TimesUpTransitionFragment after its 5-second countdown.
     * Emits GoToActivity so the transition fragment navigates to the next activity.
     */
    fun onTransitionComplete() {
        viewModelScope.launch {
            currentActivity?.let {
                _activitySecondsRemaining.value = DEMO_ACTIVITY_DURATION_SECONDS
            }
            _navigationEvent.emit(NavigationEvent.GoToActivity(_currentStepIndex.value))
        }
    }

    fun onCompleteRoutineTapped() = onActivityComplete()

    fun onExitConfirmed() {
        viewModelScope.launch {
            cancelAllTimers()
            _sessionState.value = SessionState.Exited
            _navigationEvent.emit(NavigationEvent.GoToHome)
        }
    }

    fun reset() {
        cancelAllTimers()
        _currentStepIndex.value = 0
        _sessionSecondsRemaining.value = SESSION_DURATION_SECONDS
        _activitySecondsRemaining.value = 0
        _sessionState.value = SessionState.Ready
        _rewardResult.value = null
        activeSessionId = null
        sessionStartTimestamp = ""
    }

    // ─── Private ──────────────────────────────────────────────────────────────

    private fun completeSession() {
        viewModelScope.launch {
            cancelAllTimers()
            _sessionState.value = SessionState.Completed

            val completionTimestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val reward = rewardCalculationUseCase.calculate(currentStreak)
            _rewardResult.value = reward

            activeSessionId?.let { sessionId ->
                try {
                    routineSessionRepository.completeSession(
                        sessionId = sessionId,
                        completionTimestamp = completionTimestamp,
                        streakAtCompletion = currentStreak + 1,
                        multiplierApplied = reward.multiplierApplied,
                        tokensEarned = reward.tokensEarned,
                        xpEarned = reward.xpEarned
                    )
                } catch (e: Exception) { /* TODO: queue retry */ }
            }

            _navigationEvent.emit(NavigationEvent.GoToCompletion)
        }
    }

    private fun startSessionTimer() {
        sessionTimerJob?.cancel()
        sessionTimerJob = viewModelScope.launch {
            while (_sessionSecondsRemaining.value > 0) {
                delay(1000)
                _sessionSecondsRemaining.value--
            }
            cancelAllTimers()
        }
    }

    private fun cancelAllTimers() {
        sessionTimerJob?.cancel()
        activityTimerJob?.cancel()
        sessionTimerJob = null
        activityTimerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelAllTimers()
    }
}