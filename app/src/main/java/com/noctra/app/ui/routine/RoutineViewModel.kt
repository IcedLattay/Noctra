package com.noctra.app.ui.routine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noctra.app.data.model.Activity
import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.RoutineRepository
import com.noctra.app.data.repository.RoutineSessionRepository
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.data.utils.RoutinePersistenceHelper
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class RoutineViewModel(application: Application) : AndroidViewModel(application) {

    private val routineSessionRepository = RoutineSessionRepository()
    private val rewardLedgerRepository    = RewardLedgerRepository()
    private val routineRepository         = RoutineRepository()
    private val userProfileRepository    = UserProfileRepository()

    private val rewardCalculationUseCase = RewardCalculationUseCase(
        rewardRepository = rewardLedgerRepository,
        routineSessionRepository = routineSessionRepository
    )

    private val userId: String? get() = UserSession.getUserId(getApplication())

    // ─── Session Setup ────────────────────────────────────────────────────────

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isWindowExpired = MutableStateFlow(false)
    val isWindowExpired: StateFlow<Boolean> = _isWindowExpired.asStateFlow()

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
                val userId = userId ?: return@launch
                val activeRoutine = routineRepository.getActiveRoutine(userId)
                val profile = userProfileRepository.getOrCreateProfile(userId)
                val targetBedtimeRaw = profile.targetBedtime ?: "22:00:00"

                if (activeRoutine != null) {
                    val entries = routineRepository.parseActivitySequence(activeRoutine.activitySequence)
                    val activities = routineRepository.hydrateActivitySequence(entries)
                    val streak = routineSessionRepository.getCurrentStreak(userId)

                    // Validate window before completing setup
                    val inWindow = com.noctra.app.utils.RoutineWindowProvider.isTimeInWindow(
                        now = LocalTime.now(),
                        targetBedtime = targetBedtimeRaw,
                        routineDurationMinutes = activeRoutine.totalDurationMinutes
                    )
                    _isWindowExpired.value = !inWindow && !com.noctra.app.utils.DebugSettings.forceRoutineWindow.value

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

    // Fallback only. Callers that know their own real duration (e.g. the
    // Stepper shape, which sums its own sub-step durations) should pass it
    // explicitly to startCurrentActivityTimer(). Callers that don't pass
    // anything still get this 15s demo value, unchanged from before.
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
        if (_isWindowExpired.value) {
            android.util.Log.e("RoutineViewModel", "Attempted to start session outside of window.")
            return
        }
        viewModelScope.launch {
            val userId = userId ?: return@launch
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

            // Local resume cache — updated regardless of whether the Supabase
            // insert above succeeded, so the user can still resume a
            // just-started routine locally even if that insert failed
            // (e.g. no connectivity). NOTE: if activeSessionId is null here,
            // the cached session has no known remote ID — checkRecoveryState()
            // will need to account for that case when it's built.
            RoutinePersistenceHelper.setActiveSessionId(activeSessionId)
            RoutinePersistenceHelper.setCurrentStepIndex(0)
            RoutinePersistenceHelper.setLastActivityTimestamp(System.currentTimeMillis())

            // Pre-populate display so the first activity shows the right time before it starts ticking
            _activitySecondsRemaining.value = DEMO_ACTIVITY_DURATION_SECONDS

            startSessionTimer()
            _navigationEvent.emit(NavigationEvent.GoToActivity(0))
        }
    }

    /**
     * Called by the active activity fragment when it's ready to begin
     * (e.g. after the 15s pre-countdown shared by every activity shape).
     * VM owns the countdown — fragments observe `activitySecondsRemaining` for display.
     *
     * @param durationSeconds Real duration for this activity's main timer.
     *   Defaults to the 15s demo value if the caller doesn't know its own
     *   duration. The Stepper shape (Progressive Muscle Relaxation, Bedtime
     *   Stretching) MUST pass its own computed total (sum of all step
     *   action+rest durations) here — otherwise the VM will fire
     *   GoToTransition at 15s regardless of how many steps remain.
     */
    fun startCurrentActivityTimer(durationSeconds: Int = DEMO_ACTIVITY_DURATION_SECONDS) {
        if (currentActivity == null) return
        activityTimerJob?.cancel()
        _activitySecondsRemaining.value = durationSeconds
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

                // Local resume cache — keep step index and last-activity time
                // current so a mid-routine app kill/reopen can recover here.
                RoutinePersistenceHelper.setCurrentStepIndex(nextIndex)
                RoutinePersistenceHelper.setLastActivityTimestamp(System.currentTimeMillis())

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

    // ─── Resume Logic (Recovery) ────────────────────────────────────────────

    sealed class RecoveryState {
        object None : RecoveryState()
        data class Resumable(
            val stepIndex: Int,
            val totalSteps: Int,
            val awayMinutes: Long
        ) : RecoveryState()
    }

    private val _recoveryState = MutableStateFlow<RecoveryState>(RecoveryState.None)
    val recoveryState: StateFlow<RecoveryState> = _recoveryState.asStateFlow()

    private var recoveryCheckPerformed = false
    private var pendingResumeStepIndex = 0

    private val FIVE_MINUTES_MILLIS = 5L * 60 * 1000
    private val SIXTY_MINUTES_MILLIS = 60L * 60 * 1000

    /**
     * checkRecoveryState() — The Resume Logic (task 5/10).
     *
     * Intended to be called once by MainActivity (task 7) each time it's
     * safe to check — e.g. from onResume(). Guarded so it only ever does
     * real work once per live VM instance:
     *   - If the VM survived (app was merely paused, not killed), this
     *     naturally no-ops on repeat calls — matching the spec's "Scenario
     *     1: seamless, no dialog" for a short screen-timeout-style pause.
     *   - If the VM was recreated (app process was killed), this is the
     *     first real call, and does the actual gap calculation.
     *
     * Does NOT navigate or mutate session state directly — only exposes
     * `recoveryState` so the caller can decide whether to show the Resume
     * Dialog (task 6). Actually resuming happens in confirmResume(),
     * called only after the user explicitly taps "Resume Routine."
     */
    fun checkRecoveryState() {
        if (recoveryCheckPerformed) return
        if (_sessionState.value == SessionState.InProgress) {
            // VM already has a live in-memory session — definitely not a
            // fresh process, nothing to recover.
            recoveryCheckPerformed = true
            return
        }
        recoveryCheckPerformed = true

        if (!RoutinePersistenceHelper.hasActiveSession()) return

        val lastActivity = RoutinePersistenceHelper.getLastActivityTimestamp()
        if (lastActivity == 0L) {
            // Cache says a session is active but has no valid timestamp —
            // shouldn't normally happen, but fail safe rather than divide
            // by an unknown gap.
            RoutinePersistenceHelper.clear()
            return
        }

        val gapMillis = System.currentTimeMillis() - lastActivity

        when {
            gapMillis > SIXTY_MINUTES_MILLIS -> {
                // Safety Net already expired while the app was closed —
                // same abandonment cleanup as the live 60-minute in-app
                // timer (onSafetyNetExpired(), task 10).
                viewModelScope.launch { onSafetyNetExpired() }
            }
            gapMillis < FIVE_MINUTES_MILLIS -> {
                // Quick recovery, app was killed: resume at the exact step.
                pendingResumeStepIndex = RoutinePersistenceHelper.getCurrentStepIndex()
                offerResume(gapMillis)
            }
            else -> {
                // 5m–60m gap: resume is offered, but always restarts at
                // Activity 1 (index 0), per spec.
                pendingResumeStepIndex = 0
                offerResume(gapMillis)
            }
        }
    }

    private fun offerResume(gapMillis: Long) {
        val awayMinutes = (gapMillis / 60000L).coerceAtLeast(1)
        _recoveryState.value = RecoveryState.Resumable(
            stepIndex = pendingResumeStepIndex,
            totalSteps = 3, // LOCKED DECISION: routines are always exactly 3 activities
            awayMinutes = awayMinutes
        )
    }

    /**
     * Called by MainActivity (task 7) when the user taps "Resume Routine"
     * on the dialog (task 6). Re-hydrates the routine's activities and
     * resumes at the step decided by checkRecoveryState(), then emits
     * GoToActivity so the host can navigate straight to the right
     * activity fragment.
     *
     * FLAG: re-hydrates via routineRepository.getActiveRoutine(userId) —
     * the user's CURRENT active routine — since no "fetch routine config
     * by ID" method was available to look up the exact historical config
     * the original session referenced. In practice these are almost
     * always the same routine; this only matters if the user edited their
     * routine (Edit Routine) in the gap between leaving and returning.
     *
     * Also resolves Flag 1: if RoutinePersistenceHelper's cached session
     * ID is null (original startSession() insert failed offline), this
     * still works — it doesn't depend on the session ID to find which
     * routine to resume.
     */
    fun confirmResume() {
        viewModelScope.launch {
            try {
                val userId = userId ?: return@launch
                val activeRoutine = routineRepository.getActiveRoutine(userId)
                if (activeRoutine == null) {
                    // Nothing to resume into — clear stale cache rather
                    // than leaving the app in a confusing half-state.
                    RoutinePersistenceHelper.clear()
                    _recoveryState.value = RecoveryState.None
                    return@launch
                }

                val entries = routineRepository.parseActivitySequence(activeRoutine.activitySequence)
                val hydratedActivities = routineRepository.hydrateActivitySequence(entries)
                val streak = routineSessionRepository.getCurrentStreak(userId)

                setupSession(hydratedActivities, activeRoutine.id, streak)
                activeSessionId = RoutinePersistenceHelper.getActiveSessionId() // may be null — see Flag 1
                _currentStepIndex.value = pendingResumeStepIndex.coerceIn(0, hydratedActivities.size - 1)
                _sessionState.value = SessionState.InProgress
                _recoveryState.value = RecoveryState.None

                RoutinePersistenceHelper.setActiveSessionId(activeSessionId)
                RoutinePersistenceHelper.setCurrentStepIndex(_currentStepIndex.value)
                RoutinePersistenceHelper.setLastActivityTimestamp(System.currentTimeMillis())

                startSessionTimer()
                _navigationEvent.emit(NavigationEvent.GoToActivity(_currentStepIndex.value))
            } catch (e: Exception) {
                android.util.Log.e("StreakDebug", "confirmResume failed", e)
            }
        }
    }

    /**
     * Called by MainActivity (task 7/8) when the user taps "Not Now" on
     * the dialog. Per task 8: does NOT touch the local cache — the
     * session stays resumable via a Passive Resume button on the Routines
     * tab. Only clears the in-memory "offer" state so the dialog doesn't
     * try to reappear later this same app session.
     */
    fun declineResume() {
        _recoveryState.value = RecoveryState.None
    }

    // ─── Private ──────────────────────────────────────────────────────────────

    private fun completeSession() {
        viewModelScope.launch {
            val userId = userId ?: return@launch
            cancelAllTimers()
            _sessionState.value = SessionState.Completed

            // Routine finished normally — clear the local resume cache so a
            // completed session can never be mistaken for a resumable one
            // (e.g. by a future Resume Dialog check).
            RoutinePersistenceHelper.clear()

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
            // Safety Net Watchdog: the 60-minute session timer has expired
            // without the user finishing or explicitly resuming. Mark the
            // session as abandoned (both remotely and in the local cache)
            // rather than leaving it silently stuck as "in progress."
            onSafetyNetExpired()
            cancelAllTimers()
        }
    }

    /**
     * Safety Net Watchdog (Cleanup & Enforcements task 10/10).
     * Called when the 60-minute session timer hits zero. Marks the session
     * ABANDONED_PENDING_DIAGNOSIS in Supabase and clears the local resume
     * cache, so no future Resume Dialog check can offer to resume a session
     * whose Safety Net has already expired.
     *
     * NOTE: if activeSessionId is null (e.g. the original startSession()
     * insert failed offline — see Flag 1), there's nothing to mark remotely;
     * we still clear the local cache so the app doesn't keep treating this
     * as a resumable session indefinitely.
     */
    private fun onSafetyNetExpired() {
        viewModelScope.launch {
            activeSessionId?.let { sessionId ->
                try {
                    routineSessionRepository.markSessionAsAbandoned(sessionId)
                    android.util.Log.d("StreakDebug", "SAFETY NET EXPIRED — session $sessionId marked abandoned")
                } catch (e: Exception) {
                    android.util.Log.e("StreakDebug", "Failed to mark session abandoned", e)
                }
            }
            RoutinePersistenceHelper.clear()
            _sessionState.value = SessionState.Exited
            _navigationEvent.emit(NavigationEvent.GoToHome)
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