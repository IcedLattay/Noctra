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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.noctra.app.R
import com.noctra.app.ui.routine.RoutineViewModel
import kotlinx.coroutines.launch

/**
 * RoutineHomeFragment
 *
 * The Routine tab's root screen. Renders one of:
 *   - Loading    → (skeleton, optional)
 *   - NoRoutine  → empty state, prompts onboarding completion
 *   - BeforeWindow → activity list + "Edit Routine" button + window hint
 *   - InWindow   → activity list + "Begin Routine" button
 *   - Resumable  → activity list + "Resume Routine" button — NEW, Flag 26
 *   - Completed  → "Routine Complete!" celebration card
 *
 * File location: com/noctra/app/ui/routine/home/RoutineHomeFragment.kt
 */
class RoutineHomeFragment : Fragment() {

    // ─── ViewModels ──────────────────────────────────────────────────────────

    private val homeViewModel: RoutineHomeViewModel by viewModels()
    private val routineViewModel: RoutineViewModel by activityViewModels()

    // ─── Views ───────────────────────────────────────────────────────────────

    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvWindowHint: TextView
    private lateinit var tvStreakCount: TextView
    private lateinit var cardsContainer: LinearLayout
    private lateinit var btnBeginRoutine: Button
    private lateinit var btnEditRoutine: Button
    private lateinit var btnResumeRoutine: Button
    private lateinit var layoutCompleted: LinearLayout
    private lateinit var layoutNoRoutine: LinearLayout

    // ─── Lifecycle ───────────────────────────────────────────────────────────

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
        setupListeners()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        homeViewModel.refresh()
    }

    // ─── Setup ───────────────────────────────────────────────────────────────

    private fun bindViews(view: View) {
        tvTitle           = view.findViewById(R.id.tv_routine_title)
        tvSubtitle        = view.findViewById(R.id.tv_routine_subtitle)
        tvWindowHint      = view.findViewById(R.id.tv_window_hint)
        tvStreakCount     = view.findViewById(R.id.tv_streak_count)
        cardsContainer  = view.findViewById(R.id.cards_container)
        btnBeginRoutine   = view.findViewById(R.id.btn_begin_routine)
        btnEditRoutine    = view.findViewById(R.id.btn_edit_routine)
        btnResumeRoutine  = view.findViewById(R.id.btn_resume_routine)
        layoutCompleted   = view.findViewById(R.id.layout_completed_state)
        layoutNoRoutine   = view.findViewById(R.id.layout_no_routine_state)
    }

    private fun setupListeners() {
    }

    // ─── State Observation ───────────────────────────────────────────────────

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
        val noInternetView = view?.findViewById<View>(R.id.noInternetView)

        if (state is RoutineHomeViewModel.RoutineHomeState.Offline) {
            noInternetView?.visibility = View.VISIBLE
            tvTitle.visibility = View.GONE
            tvSubtitle.visibility = View.GONE
            view?.findViewById<View>(R.id.ll_streak_badge)?.visibility = View.GONE
            layoutCompleted.visibility = View.GONE
            layoutNoRoutine.visibility = View.GONE
            btnBeginRoutine.visibility = View.GONE
            btnEditRoutine.visibility = View.GONE
            cardsContainer.visibility = View.GONE
            tvWindowHint.visibility = View.GONE
            setupRetryButton()
            return
        } else {
            noInternetView?.visibility = View.GONE
            tvTitle.visibility = View.VISIBLE
            tvSubtitle.visibility = View.VISIBLE
            view?.findViewById<View>(R.id.ll_streak_badge)?.visibility = View.VISIBLE
        }

        // Reset all conditional UI first.
        layoutCompleted.visibility = View.GONE
        layoutNoRoutine.visibility = View.GONE
        btnBeginRoutine.visibility = View.GONE
        btnEditRoutine.visibility = View.GONE
        btnResumeRoutine.visibility = View.GONE
        cardsContainer.visibility = View.GONE
        tvWindowHint.visibility = View.GONE

        when (state) {
            is RoutineHomeViewModel.RoutineHomeState.Loading -> {
                // TODO: optional shimmer skeleton
            }

            is RoutineHomeViewModel.RoutineHomeState.NoRoutine -> {
                layoutNoRoutine.visibility = View.VISIBLE
            }

            is RoutineHomeViewModel.RoutineHomeState.BeforeWindow -> {
                tvSubtitle.text = "${state.activities.size} activities • ${state.totalDurationMinutes} minutes total"
                tvStreakCount.text = "${state.currentStreak} day streak"
                tvWindowHint.text = "Routine opens at ${state.routineStartTime}"
                tvWindowHint.visibility = View.VISIBLE

                renderCards(state.activities)
                cardsContainer.visibility = View.VISIBLE

                btnEditRoutine.visibility = View.VISIBLE
                btnEditRoutine.setOnClickListener {
                    navigateToEditRoutine()
                }
            }

            is RoutineHomeViewModel.RoutineHomeState.InWindow -> {
                tvSubtitle.text = "${state.activities.size} activities • ${state.totalDurationMinutes} minutes total"
                tvStreakCount.text = "${state.currentStreak} day streak"

                renderCards(state.activities)
                cardsContainer.visibility = View.VISIBLE

                btnBeginRoutine.visibility = View.VISIBLE
                btnBeginRoutine.setOnClickListener {
                    routineViewModel.setupSession(
                        activities      = state.activities,
                        routineConfigId = homeViewModel.activeRoutineConfigId ?: "",
                        currentStreak   = state.currentStreak
                    )
                    findNavController().navigate(
                        R.id.action_routineHomeFragment_to_routineStartFragment
                    )
                }
            }

            is RoutineHomeViewModel.RoutineHomeState.Resumable -> {
                // NEW — Flag 26 (task 8's other half). Passive resume path:
                // the Resume Dialog (MainActivity) is the "active" path that
                // pops up automatically; this button is the fallback for a
                // user who dismissed that dialog with "Not Now" and only
                // later navigates to the Routine tab.
                tvSubtitle.text = "${state.activities.size} activities • ${state.totalDurationMinutes} minutes total"
                tvStreakCount.text = "${state.currentStreak} day streak"

                renderCards(state.activities)
                cardsContainer.visibility = View.VISIBLE

                btnResumeRoutine.visibility = View.VISIBLE
                btnResumeRoutine.text = "Resume Routine (Step ${state.resumeStepIndex + 1} of ${state.activities.size})"
                btnResumeRoutine.setOnClickListener {
                    // FLAG: relies on routineViewModel.confirmResume(), which
                    // resumes using whatever step index MainActivity's
                    // checkRecoveryState() already decided earlier this app
                    // session (exact step vs restart-at-Activity-1, per the
                    // 5/60-minute gap rule). This assumes checkRecoveryState()
                    // has run at least once since app launch — true in
                    // virtually every real scenario, since MainActivity calls
                    // it on every onResume(), and this screen can only be
                    // reached after MainActivity has already resumed once.
                    routineViewModel.confirmResume()
                    findNavController().navigate(
                        R.id.action_routineHomeFragment_to_routineStartFragment
                    )
                }
            }

            is RoutineHomeViewModel.RoutineHomeState.Completed -> {
                tvSubtitle.text = "All done for tonight"
                tvStreakCount.text = "${state.currentStreak} day streak"
                layoutCompleted.visibility = View.VISIBLE
            }

            is RoutineHomeViewModel.RoutineHomeState.Error -> {
                tvSubtitle.text = state.message
            }

            is RoutineHomeViewModel.RoutineHomeState.Offline -> {
                // Already handled above
            }
        }
    }

    // ─── Navigation ──────────────────────────────────────────────────────────

    private fun setupRetryButton() {
        val noInternetView = view?.findViewById<View>(R.id.noInternetView) ?: return
        val btnRetry = noInternetView.findViewById<android.widget.ImageButton>(R.id.btnRetry)
        val progressRetry = noInternetView.findViewById<android.widget.ProgressBar>(R.id.progressRetry)
        val tvRetry = noInternetView.findViewById<TextView>(R.id.tvRetry)

        btnRetry.setOnClickListener {
            btnRetry.visibility = View.GONE
            progressRetry.visibility = View.VISIBLE
            tvRetry.text = "retrying..."
            homeViewModel.retry()
        }
    }

    /**
     * Navigates into the edit flow. Reuses ActivityLibraryFragment +
     * RoutineSequencingFragment from onboarding, passing editMode = true so
     * those fragments know to preload existing data and re-save on completion
     * instead of inserting a new config.
     * Save happens in RoutineSequencingFragment (Session 5 fix).
     */
    /**
     * No adapter: always ~3 cards, inflated directly with equal weights so
     * they share the container height natively. Long-press opens info.
     */
    private fun renderCards(activities: List<com.noctra.app.data.model.Activity>) {
        cardsContainer.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        activities.forEachIndexed { index, activity ->
            val card = inflater.inflate(R.layout.item_activity_card_home, cardsContainer, false)
            card.findViewById<TextView>(R.id.tv_step_number).text = "${index + 1}"
            card.findViewById<TextView>(R.id.tv_activity_label).text = activity.label
            card.findViewById<TextView>(R.id.tv_activity_duration).text =
                "${activity.defaultDurationMinutes} min"
            val desc = card.findViewById<TextView>(R.id.tv_activity_description)
            if (activity.description.isNotBlank()) {
                desc.text = activity.description
                desc.visibility = View.VISIBLE
            } else {
                desc.visibility = View.GONE
            }
            com.noctra.app.utils.ActivityIllustrations.load(
                card.findViewById(R.id.iv_activity_icon), activity.label
            )
            card.setOnLongClickListener {
                ActivityInfoDialogFragment.show(childFragmentManager, activity, index + 1)
                true
            }
            card.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply {
                // XML margins are dropped when params are replaced: re-apply
                // in px so the gutters actually exist.
                val d = resources.displayMetrics.density
                setMargins((4 * d).toInt(), (10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt())
            }
            cardsContainer.addView(card)
        }
    }

    private fun navigateToEditRoutine() {
        val args = Bundle().apply { putBoolean("editMode", true) }
        findNavController().navigate(
            R.id.action_routineHomeFragment_to_editRoutine,
            args
        )
    }
}