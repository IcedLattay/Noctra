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
     * Navigates to the correct activity Fragment based on the activity type
     * at the given index in the routine.
     */
    private fun navigateToActivity(index: Int) {
        val activity = routineViewModel.activities.getOrNull(index) ?: return

        val actionId = when (activity.label) {
            "Slow-Paced Breathing"  -> R.id.action_routineStartFragment_to_breathingActivityFragment
            "White/Pink Noise"      -> R.id.action_routineStartFragment_to_audioscapeActivityFragment
            "Gratitude Journaling"  -> R.id.action_routineStartFragment_to_gratitudeJournalingActivityFragment
            else                    -> R.id.action_routineStartFragment_to_genericTimerActivityFragment
        }

        findNavController().navigate(actionId)
    }
}
