package com.noctra.app.ui.routine.home

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.noctra.app.R
import com.noctra.app.ui.routine.RoutineViewModel
import kotlinx.coroutines.launch

/**
 * RoutineStartFragment
 *
 * The "Ready for Tonight's Routine?" screen shown before execution begins.
 * Displays the routine summary (activity list + total duration) and hosts
 * the "Start Routine" button.
 *
 * Navigation:
 *   - Receives data via RoutineViewModel (already set up by RoutineHomeFragment)
 *   - "Start Routine" → calls RoutineViewModel.startSession() → auto-navigates to first activity
 *   - X button → navigates back to RoutineHomeFragment (no confirmation — routine not started yet)
 *
 * File location: com/noctra/app/ui/routine/home/RoutineStartFragment.kt
 */
class RoutineStartFragment : Fragment() {

    // ─── ViewModel ────────────────────────────────────────────────────────────

    private val routineViewModel: RoutineViewModel by activityViewModels()

    // ─── Views ────────────────────────────────────────────────────────────────

    private lateinit var btnExit: ImageButton
    private lateinit var tvSubtitle: TextView
    private lateinit var rvActivitySteps: RecyclerView
    private lateinit var btnStartRoutine: Button

    // ─── Adapter ─────────────────────────────────────────────────────────────

    private lateinit var stepAdapter: RoutineStepAdapter

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_routine_start, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupRecyclerView()
        populateRoutineData()
        setupClickListeners()
        observeNavigationEvents()
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private fun bindViews(view: View) {
        btnExit         = view.findViewById(R.id.btn_exit)
        tvSubtitle      = view.findViewById(R.id.tv_start_subtitle)
        rvActivitySteps = view.findViewById(R.id.rv_activity_steps)
        btnStartRoutine = view.findViewById(R.id.btn_start_routine)
    }

    private fun setupRecyclerView() {
        stepAdapter = RoutineStepAdapter()
        rvActivitySteps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = stepAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun populateRoutineData() {
        val activities = routineViewModel.activities
        val totalMinutes = activities.sumOf { it.defaultDurationMinutes }

        tvSubtitle.text = "${activities.size} activities • $totalMinutes minutes total"
        stepAdapter.submitList(activities)
    }

    private fun setupClickListeners() {
        // X button — back to home, no confirmation (routine not started yet)
        btnExit.setOnClickListener {
            findNavController().popBackStack()
        }

        // Start Routine — create session and begin execution
        btnStartRoutine.setOnClickListener {
            btnStartRoutine.isEnabled = false // prevent double tap
            routineViewModel.startSession()
        }
    }

    // ─── Navigation Events ────────────────────────────────────────────────────

    private fun observeNavigationEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                routineViewModel.navigationEvent.collect { event ->
                    when (event) {
                        is RoutineViewModel.NavigationEvent.GoToActivity -> {
                            navigateToActivity(event.index)
                        }
                        is RoutineViewModel.NavigationEvent.GoToHome -> {
                            findNavController().navigate(

                                R.id.action_global_routineHomeFragment
                            )
                        }
                        else -> { /* other events handled by execution fragments */ }
                    }
                }
            }
        }
    }

    /**
     * Navigates to the correct activity Fragment based on the activity's
     * label at the given index in the routine. Labels match the exact
     * `activity_library.label` strings seeded in Supabase — see
     * ACTIVITY_MIGRATION_REFERENCE for the full label -> shape mapping.
     *
     * All 9 seeded activities route to one of the 4 existing destinations,
     * each repurposed to a shared shape:
     *   audioscapeActivityFragment       -> Simple Timer shape
     *   breathingActivityFragment        -> Breathing Circle shape
     *   gratitudeJournalingActivityFragment -> Text Input shape
     *   genericTimerActivityFragment     -> Stepper shape
     *
     * Low-Stimulus Audio Listening is intentionally omitted — it isn't
     * seeded in activity_library yet, so it will never appear in
     * routineViewModel.activities in the first place.
     */
    private fun navigateToActivity(index: Int) {
        val activity = routineViewModel.activities.getOrNull(index) ?: return

        val actionId = when (activity.label) {
            "Bedtime To-Do List Writing",
            "Reading",
            "White/Pink Noise",
            "Warm Shower"
                -> R.id.action_routineStartFragment_to_audioscapeActivityFragment

            "Slow-Paced Breathing",
            "Mindfulness"
                -> R.id.action_routineStartFragment_to_breathingActivityFragment

            "Gratitude Journaling"
                -> R.id.action_routineStartFragment_to_gratitudeJournalingActivityFragment

            "Progressive Muscle Relaxation",
            "Bedtime Stretching"
                -> R.id.action_routineStartFragment_to_genericTimerActivityFragment

            else -> {
                // Unknown label — shouldn't happen with the 9 seeded activities,
                // but fall back rather than crash if the DB gains a new row
                // this dispatch table hasn't been updated for yet.
                android.util.Log.w(
                    "RoutineStartFragment",
                    "No shape mapping for activity label '${activity.label}', falling back to genericTimerActivityFragment"
                )
                R.id.action_routineStartFragment_to_genericTimerActivityFragment
            }
        }

        findNavController().navigate(actionId)
    }
}