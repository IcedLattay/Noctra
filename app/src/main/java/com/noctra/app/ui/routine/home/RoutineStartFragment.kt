package com.noctra.app.ui.routine.home

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
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
 * Now a 3-panel sequence shown before routine execution begins:
 *
 *   1. Wind Down panel — ambient confirmation screen. Tapping "Start Activity"
 *      advances to the loading beat.
 *   2. Loading panel — pulsing lightbulb glow for ~2s, then auto-advances.
 *   3. Ready panel — the original "Ready for Tonight's Routine?" summary with
 *      the activity list and "Start Routine" button. Unchanged behavior.
 *
 * Implemented as panels within this one fragment rather than new navigation
 * destinations, matching the pattern used by every activity fragment (which
 * swap a pre-countdown panel for an active panel internally). This avoids
 * touching nav_graph.xml and MainActivity's bottom-nav hiding logic.
 *
 * File location: com/noctra/app/ui/routine/home/RoutineStartFragment.kt
 */
class RoutineStartFragment : Fragment() {

    // ─── ViewModel ────────────────────────────────────────────────────────────

    private val routineViewModel: RoutineViewModel by activityViewModels()

    // ─── Panels ───────────────────────────────────────────────────────────────

    private lateinit var windDownPanel: ConstraintLayout
    private lateinit var loadingPanel: ConstraintLayout
    private lateinit var readyPanel: ConstraintLayout

    // ─── Views ────────────────────────────────────────────────────────────────

    private lateinit var btnStartActivity: Button
    private lateinit var viewBulbGlow: View
    private lateinit var ivLightbulb: View

    private lateinit var btnExit: ImageButton
    private lateinit var tvSubtitle: TextView
    private lateinit var rvActivitySteps: RecyclerView
    private lateinit var btnStartRoutine: Button

    // ─── Adapter ─────────────────────────────────────────────────────────────

    private lateinit var stepAdapter: RoutineStepAdapter

    // ─── Animation / timing ───────────────────────────────────────────────────

    private var glowAnimator: ObjectAnimator? = null
    private var bulbAnimator: ObjectAnimator? = null

    companion object {
        /** How long the lightbulb loading beat holds before showing the Ready panel. */
        private const val LOADING_DURATION_MS = 2_000L
    }

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

        // If opened via deep link, VM might be empty. Fetch data if needed.
        routineViewModel.initializeIfNecessary()

        showWindDownPanel()
        observeState()
        setupClickListeners()
        observeNavigationEvents()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                routineViewModel.isInitialized.collect { ready ->
                    // Populates the Ready panel's list/subtitle. Safe to run
                    // while that panel is still hidden — it'll be correct by
                    // the time the user reaches it.
                    if (ready) populateRoutineData()
                }
            }
        }
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private fun bindViews(view: View) {
        windDownPanel   = view.findViewById(R.id.windDownPanel)
        loadingPanel    = view.findViewById(R.id.loadingPanel)
        readyPanel      = view.findViewById(R.id.readyPanel)

        btnStartActivity = view.findViewById(R.id.btn_start_activity)
        viewBulbGlow     = view.findViewById(R.id.view_bulb_glow)
        ivLightbulb      = view.findViewById(R.id.iv_lightbulb)

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

    // ─── Panel sequence ───────────────────────────────────────────────────────

    private fun showWindDownPanel() {
        windDownPanel.visibility = View.VISIBLE
        loadingPanel.visibility = View.GONE
        readyPanel.visibility = View.GONE
    }

    private fun showLoadingPanel() {
        windDownPanel.visibility = View.GONE
        loadingPanel.visibility = View.VISIBLE
        readyPanel.visibility = View.GONE

        startBulbAnimation()

        // Hold the beat, then reveal the Ready panel.
        loadingPanel.postDelayed({
            // Guard: the user may have navigated away during the delay.
            if (!isAdded || view == null) return@postDelayed
            stopBulbAnimation()
            showReadyPanel()
        }, LOADING_DURATION_MS)
    }

    private fun showReadyPanel() {
        windDownPanel.visibility = View.GONE
        loadingPanel.visibility = View.GONE
        readyPanel.visibility = View.VISIBLE
    }

    /** Soft pulse on the glow plus a gentle breathe on the bulb itself. */
    private fun startBulbAnimation() {
        stopBulbAnimation()

        glowAnimator = ObjectAnimator.ofFloat(viewBulbGlow, View.ALPHA, 0.45f, 1f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }

        bulbAnimator = ObjectAnimator.ofFloat(ivLightbulb, View.SCALE_X, 0.94f, 1.04f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { ivLightbulb.scaleY = ivLightbulb.scaleX }
            start()
        }
    }

    private fun stopBulbAnimation() {
        glowAnimator?.cancel()
        glowAnimator = null
        bulbAnimator?.cancel()
        bulbAnimator = null
    }

    private fun setupClickListeners() {
        // Wind Down → loading beat → Ready panel
        btnStartActivity.setOnClickListener {
            btnStartActivity.isEnabled = false // prevent double tap
            showLoadingPanel()
        }

        // X button — go back to home. If we deep-linked here, popBackStack might go to onboarding,
        // so we navigate explicitly to routineHomeFragment.
        btnExit.setOnClickListener {
            findNavController().navigate(R.id.action_global_routineHomeFragment)
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
     * activity_library.label strings seeded in Supabase.
     *
     * Keep this in sync with TimesUpTransitionFragment.navigateToActivity().
     */
    private fun navigateToActivity(index: Int) {
        val activity = routineViewModel.activities.getOrNull(index) ?: return

        val actionId = when (activity.label) {
            "Bedtime To-Do List Writing",
            "Reading",
            "White/Pink Noise",
            "Warm Shower",
            "Mindfulness",                    // nature-scenery ambient variant
            "Low-Stimulus Audio Listening"    // waveform variant (not seeded in DB yet)
                -> R.id.action_routineStartFragment_to_audioscapeActivityFragment

            "Slow-Paced Breathing"
                -> R.id.action_routineStartFragment_to_breathingActivityFragment

            "Gratitude Journaling"
                -> R.id.action_routineStartFragment_to_gratitudeJournalingActivityFragment

            "Progressive Muscle Relaxation",
            "Bedtime Stretching"
                -> R.id.action_routineStartFragment_to_genericTimerActivityFragment

            else -> {
                android.util.Log.w(
                    "RoutineStartFragment",
                    "No shape mapping for activity label '${activity.label}', falling back to genericTimerActivityFragment"
                )
                R.id.action_routineStartFragment_to_genericTimerActivityFragment
            }
        }

        findNavController().navigate(actionId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopBulbAnimation()
    }
}