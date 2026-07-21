// com/noctra/app/ui/routine/onboarding/OnboardingViewModel.kt
package com.noctra.app.ui.routine.onboarding

import androidx.lifecycle.ViewModel
import com.noctra.app.data.model.Activity
import com.noctra.app.data.model.RoutineActivityEntry
import com.noctra.app.data.repository.RoutineRepository
import com.noctra.app.data.repository.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OnboardingViewModel : ViewModel() {

    companion object {
        // Locked-in rule: user selects between 2 and 5 activities during
        // onboarding (and via the same edit flow later). Resolves a
        // documented conflict between the SRS (2-5) and the SDD (exactly 3)
        // in favor of the SRS range.
        const val MIN_SELECTABLE_ACTIVITIES = 2
        const val MAX_SELECTABLE_ACTIVITIES = 5
    }

    // Step 1
    private val _targetBedtime = MutableStateFlow("22:00") // default 10:00 PM
    val targetBedtime: StateFlow<String> = _targetBedtime.asStateFlow()

    // Step 2
    private val _selectedActivities = MutableStateFlow<List<Activity>>(emptyList())
    val selectedActivities: StateFlow<List<Activity>> = _selectedActivities.asStateFlow()

    // Step 3 — ordered sequence (may differ from selectedActivities order)
    private val _orderedActivities = MutableStateFlow<List<Activity>>(emptyList())
    val orderedActivities: StateFlow<List<Activity>> = _orderedActivities.asStateFlow()

    fun setBedtime(hhmm: String) {
        _targetBedtime.value = hhmm
    }

    fun toggleActivity(activity: Activity) {
        val current = _selectedActivities.value.toMutableList()
        if (current.any { it.activityId == activity.activityId }) {
            current.removeAll { it.activityId == activity.activityId }
        } else {
            if (current.size < MAX_SELECTABLE_ACTIVITIES) current.add(activity)
        }
        _selectedActivities.value = current
    }

    fun isActivitySelected(activity: Activity): Boolean =
        _selectedActivities.value.any { it.activityId == activity.activityId }

    /** True once the user has selected a valid number of activities (2-5 inclusive). */
    fun isSelectionValid(): Boolean =
        _selectedActivities.value.size in MIN_SELECTABLE_ACTIVITIES..MAX_SELECTABLE_ACTIVITIES

    fun confirmSelectionAndProceed() {
        _orderedActivities.value = _selectedActivities.value.toList()
    }

    fun reorderActivities(from: Int, to: Int) {
        val list = _orderedActivities.value.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        _orderedActivities.value = list
    }

    fun removeActivityAt(index: Int) {
        val newOrdered = _orderedActivities.value.toMutableList()
        val removed = newOrdered.removeAt(index)
        _orderedActivities.value = newOrdered

        // Remove from selected separately by ID, not by replacing with ordered list
        _selectedActivities.value = _selectedActivities.value
            .filter { it.activityId != removed.activityId }
    }

    fun getActivitySequence(): List<RoutineActivityEntry> =
        _orderedActivities.value.mapIndexed { index, activity ->
            RoutineActivityEntry(activityId = activity.activityId, order = index + 1)
        }

    fun loadExistingRoutine(activities: List<Activity>, bedtime: String) {
        _targetBedtime.value = bedtime
        _selectedActivities.value = activities.toList()
        _orderedActivities.value = activities.toList()
    }

    fun getTotalDurationMinutes(): Int =
        _orderedActivities.value.sumOf { it.defaultDurationMinutes }

    fun getEstimatedStartTime(): String {
        val parts = _targetBedtime.value.split(":")
        val bedtimeHour = parts[0].toInt()
        val bedtimeMin = parts[1].toInt()
        val totalMinutes = bedtimeHour * 60 + bedtimeMin - getTotalDurationMinutes()
        val h = (totalMinutes / 60).mod(24)
        val m = totalMinutes.mod(60)
        return "%02d:%02d".format(h, m)
    }
}