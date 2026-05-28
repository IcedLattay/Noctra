package com.noctra.app.ui.routine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noctra.app.data.model.Activity
import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.RoutineRepository
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
    private val rewardLedgerRepository    = RewardLedgerRepository()
    private val routineRepository         = RoutineRepository()
    private val rewardCalculationUseCase = RewardCalculationUseCase(
        rewardRepository = rewardLedgerRepository,
        routineSessionRepository = routineSessionRepository
    )

    private val userId: String get() = UserSession.getUserId(getApplication())

    // ─── Session Setup ────────────────────────────────────────────────────────

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    var activities: List<Activity> = emptyList(); private set
    var routineConfigId: String = ""; private set
    var currentStreak: Int = 0; private set

    fun setupSession(activities: List<Activity>, routineConfigId: String, currentStreak: Int) {
        this.activities = activities
        this.routineConfigId = routineConfigId
        this.currentStreak = currentStreak
        _isInitialized.value = true
        _sessionState.value = SessionState.Ready
    }

    /**
     * Called by RoutineStartFragment. If the VM wasn't initialized by the
     * Home screen (e.g. deep link), it fetches the active routine from DB.
     */
    fun initializeIfNecessary() {
        if (_isInitialized.value) return

        viewModelScope.launch {
            try {
                val activeRoutine = routineRepository.getActiveRoutine(userId)
                if (activeRoutine != null) {
                    val entries = routineRepository.parseActivitySequence(activeRoutine.activitySequence)
                    val activities = routineRepository.hydrateActivitySequence(entries)
                    val streak = routineSessionRepository.getCurrentStreak(userId)

                    setupSession(activities, activeRoutine.id, streak)
                }
            } catch (e: Exception) {
                android.util.Log.e("RoutineViewModel", "Initialization failed", e)
            }
        }
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
                android.util.Log.d("StreakDebug", "SESSION STARTED id=$activeSessionId")
            } catch (e: Exception) {
                android.util.Log.e("StreakDebug", "START SESSION FAILED", e)
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
            // Timer ended. 
            // If it's NOT the last step, auto-transition to next.
            // If it IS the last step, we stay here until the user taps "Complete Routine".
            if (!isLastStep) {
                onActivityComplete()
            }
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

            android.util.Log.d("StreakDebug", "COMPLETING sessionId=$activeSessionId")

            try {
                // 1. Update the Reward Ledger (tokens, XP, streaks) via UseCase
                val reward = rewardCalculationUseCase.execute(
                    userId = userId,
                    sessionId = activeSessionId ?: "",
                    completedOnTime = true
                )
                _rewardResult.value = reward

                // 2. Mark the Routine Session itself as complete in DB
                activeSessionId?.let { sessionId ->
                    routineSessionRepository.completeSession(
                        sessionId = sessionId,
                        completionTimestamp = completionTimestamp,
                        streakAtCompletion = reward.newStreak,
                        multiplierApplied = reward.multiplierApplied,
                        tokensEarned = reward.tokensEarned,
                        xpEarned = reward.xpEarned
                    )
                    android.util.Log.d("StreakDebug", "COMPLETE SESSION WROTE OK")
                }
            } catch (e: Exception) {
                android.util.Log.e("StreakDebug", "COMPLETE SESSION FAILED", e)
                // Fallback to calculation only if ledger update fails so UI doesn't break
                _rewardResult.value = rewardCalculationUseCase.calculate(currentStreak)
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