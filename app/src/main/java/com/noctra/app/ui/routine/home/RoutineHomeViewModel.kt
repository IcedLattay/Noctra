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
import java.time.LocalDate
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
 *   - Outside window  → show activity list + Edit Routine button
 *   - Inside window   → show Start My Routine button
 *   - Already done    → show Routine Complete state
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

    /**
     * The overall screen state exposed to RoutineHomeFragment.
     */
    sealed class RoutineHomeState {
        /** Still loading data — show shimmer / skeleton. */
        object Loading : RoutineHomeState()

        /** User has not completed onboarding — no routine configured yet. */
        object NoRoutine : RoutineHomeState()

        /** Routine exists; outside the routine window — show preview + Edit button. */
        data class BeforeWindow(
            val activities: List<Activity>,
            val totalDurationMinutes: Int,
            val targetBedtime: String,       // e.g. "10:30 PM"
            val routineStartTime: String,    // e.g. "10:00 PM"
            val currentStreak: Int
        ) : RoutineHomeState()

        /** Inside the routine window — show Start My Routine button. */
        data class InWindow(
            val activities: List<Activity>,
            val totalDurationMinutes: Int,
            val targetBedtime: String,
            val routineStartTime: String,
            val currentStreak: Int
        ) : RoutineHomeState()

        /** User already completed tonight's routine. */
        data class Completed(
            val currentStreak: Int
        ) : RoutineHomeState()

        /** Something went wrong loading data. */
        data class Error(val message: String) : RoutineHomeState()
    }

    private val _state = MutableStateFlow<RoutineHomeState>(RoutineHomeState.Loading)
    val state: StateFlow<RoutineHomeState> = _state.asStateFlow()

    // ─── Active Routine (cached for StartFragment navigation) ────────────────

    private var _activeRoutine: RoutineConfiguration? = null

    /** Exposes the active routine config ID for RoutineStartFragment. */
    val activeRoutineConfigId: String?
        get() = _activeRoutine?.id

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        loadHomeState()
    }

    // ─── Public ──────────────────────────────────────────────────────────────

    /**
     * Called by RoutineHomeFragment on resume to refresh state.
     * Handles cases where user returns from editing their routine or completing it.
     */
    fun refresh() {
        loadHomeState()
    }

    // ─── Core Load Logic ─────────────────────────────────────────────────────

    private fun loadHomeState() {
        viewModelScope.launch {
            _state.value = RoutineHomeState.Loading

            try {
                // 1. Get user profile for target_bedtime (default to 11 PM for demo)
                val profile = userProfileRepository.getOrCreateProfile(userId)
                val targetBedtimeRaw = profile.targetBedtime ?: "23:00:00"

                // 2. FOR DEMO: Load only implemented activities from the library
                val allActivities = routineRepository.getActivityLibrary()
                val activities = allActivities.filter { 
                    val type = it.activityType.lowercase()
                    type == "breathing" || type == "audio" || type == "audioscape" || type == "journaling"
                }
                val totalDuration = activities.sumOf { it.defaultDurationMinutes }

                // 3. Check if already completed tonight
                val todayDate = routineSessionRepository.getTodayDateString()
                val alreadyCompleted = routineSessionRepository
                    .hasCompletedSessionForDate(userId, todayDate)

                if (alreadyCompleted) {
                    val streak = routineSessionRepository.getCurrentStreak(userId)
                    _state.value = RoutineHomeState.Completed(currentStreak = streak)
                    return@launch
                }

                // 4. Compute routine window
                val targetBedtime = parseTime(targetBedtimeRaw)
                val windowOpen    = targetBedtime.minusMinutes(totalDuration.toLong())

                val streak = routineSessionRepository.getCurrentStreak(userId)

                val targetBedtimeFormatted  = formatTime(targetBedtime)
                val routineStartFormatted   = formatTime(windowOpen)

                // For demo/testing: Always show activities and always enable the "Begin" button.
                _state.value = RoutineHomeState.InWindow(
                    activities           = activities,
                    totalDurationMinutes = totalDuration,
                    targetBedtime        = targetBedtimeFormatted,
                    routineStartTime     = routineStartFormatted,
                    currentStreak        = streak
                )

            } catch (e: Exception) {
                _state.value = RoutineHomeState.Error(
                    message = e.message ?: "Something went wrong loading your routine."
                )
            }
        }
    }

    // ─── Time Helpers ─────────────────────────────────────────────────────────

    /**
     * Parses a time string from Supabase (stored as "HH:mm:ss" or "HH:mm")
     * into a LocalTime object.
     */
    private fun parseTime(raw: String): LocalTime {
        return try {
            LocalTime.parse(raw, DateTimeFormatter.ofPattern("HH:mm:ss"))
        } catch (e: Exception) {
            LocalTime.parse(raw, DateTimeFormatter.ofPattern("HH:mm"))
        }
    }

    /**
     * Formats a LocalTime into a human-readable 12-hour string for display.
     * e.g. 22:30 → "10:30 PM"
     */
    private fun formatTime(time: LocalTime): String {
        return time.format(DateTimeFormatter.ofPattern("h:mm a"))
    }
}