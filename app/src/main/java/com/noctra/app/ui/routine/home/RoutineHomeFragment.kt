package com.noctra.app.ui.routine.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.noctra.app.R
import com.noctra.app.ui.routine.RoutineViewModel
import kotlinx.coroutines.launch

/**
 * RoutineHomeFragment
 *
 * The Routine tab's root screen. Displays the user's activity card grid,
 * current streak, and either a "Begin Routine" button (inside window)
 * or a read-only preview (outside window / completed).
 *
 * File location: com/noctra/app/ui/routine/home/RoutineHomeFragment.kt
 */
class RoutineHomeFragment : Fragment() {

    // ─── ViewModels ───────────────────────────────────────────────────────────

    // Scoped to this Fragment only
    private val homeViewModel: RoutineHomeViewModel by viewModels()

    // Scoped to MainActivity — shared with all execution Fragments
    private val routineViewModel: RoutineViewModel by activityViewModels()

    // ─── Views ────────────────────────────────────────────────────────────────

    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvStreakCount: TextView
    private lateinit var rvActivityCards: RecyclerView
    private lateinit var btnBeginRoutine: Button
    private lateinit var layoutCompleted: LinearLayout   // shown when routine done tonight
    private lateinit var layoutNoRoutine: LinearLayout   // shown when no routine configured

    // ─── Adapter ─────────────────────────────────────────────────────────────

    private lateinit var activityCardAdapter: ActivityCardAdapter

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_routine_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupRecyclerView()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        // Refresh every time the user returns to this screen
        // (e.g. after editing routine, after completing a session)
        homeViewModel.refresh()
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private fun bindViews(view: View) {
        tvTitle          = view.findViewById(R.id.tv_routine_title)
        tvSubtitle       = view.findViewById(R.id.tv_routine_subtitle)
        tvStreakCount    = view.findViewById(R.id.tv_streak_count)
        rvActivityCards  = view.findViewById(R.id.rv_activity_cards)
        btnBeginRoutine  = view.findViewById(R.id.btn_begin_routine)
        layoutCompleted  = view.findViewById(R.id.layout_completed_state)
        layoutNoRoutine  = view.findViewById(R.id.layout_no_routine_state)
    }

    private fun setupRecyclerView() {
        activityCardAdapter = ActivityCardAdapter()

        // 2-column grid to match Figma design
        val gridLayoutManager = GridLayoutManager(requireContext(), 2)

        // Make the 3rd card (last item if odd count) span full width
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val itemCount = activityCardAdapter.itemCount
                // If odd number of items and this is the last one → full width
                return if (itemCount % 2 != 0 && position == itemCount - 1) 2 else 1
            }
        }

        rvActivityCards.apply {
            layoutManager = gridLayoutManager
            adapter = activityCardAdapter
            isNestedScrollingEnabled = false
        }
    }

    // ─── State Observation ────────────────────────────────────────────────────

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.state.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: RoutineHomeViewModel.RoutineHomeState) {
        // Hide all conditional layouts first
        layoutCompleted.visibility = View.GONE
        layoutNoRoutine.visibility = View.GONE
        btnBeginRoutine.visibility = View.GONE
        rvActivityCards.visibility = View.GONE

        when (state) {
            is RoutineHomeViewModel.RoutineHomeState.Loading -> {
                // TODO: show shimmer skeleton if desired
            }

            is RoutineHomeViewModel.RoutineHomeState.NoRoutine -> {
                layoutNoRoutine.visibility = View.VISIBLE
            }

            is RoutineHomeViewModel.RoutineHomeState.BeforeWindow -> {
                tvSubtitle.text = "${state.activities.size} activities • ${state.totalDurationMinutes} minutes total"
                tvStreakCount.text = "${state.currentStreak} day streak"
                activityCardAdapter.submitList(state.activities)
                rvActivityCards.visibility = View.VISIBLE
                // Outside window — show grid but no Start button
                // Edit Routine button handles this separately via menu/toolbar
            }

            is RoutineHomeViewModel.RoutineHomeState.InWindow -> {
                tvSubtitle.text = "${state.activities.size} activities • ${state.totalDurationMinutes} minutes total"
                tvStreakCount.text = "${state.currentStreak} day streak"
                activityCardAdapter.submitList(state.activities)
                rvActivityCards.visibility = View.VISIBLE
                btnBeginRoutine.visibility = View.VISIBLE

                // Pass data to RoutineViewModel before navigating
                btnBeginRoutine.setOnClickListener {
                    routineViewModel.setupSession(
                        activities      = state.activities,
                        routineConfigId = homeViewModel.activeRoutineConfigId ?: "",
                        currentStreak   = state.currentStreak
                    )
                    findNavController().navigate(R.id.action_routineHomeFragment_to_routineStartFragment)
                }
            }

            is RoutineHomeViewModel.RoutineHomeState.Completed -> {
                tvStreakCount.text = "${state.currentStreak} day streak"
                layoutCompleted.visibility = View.VISIBLE
            }

            is RoutineHomeViewModel.RoutineHomeState.Error -> {
                tvSubtitle.text = state.message
            }
        }
    }
}