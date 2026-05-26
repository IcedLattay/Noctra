package com.noctra.app.ui.routine.execution.activities

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.noctra.app.R
import com.noctra.app.data.model.Activity
import com.noctra.app.databinding.FragmentBreathingActivityBinding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * BreathingActivityFragment
 *
 * MVP Activity #1 — Slow-Paced Breathing
 *
 * Two distinct visual phases:
 *   1. PRE_COUNTDOWN  — Shleepy body + instruction + 15s green timer
 *   2. BREATHING      — Animated expanding/contracting circle + Inhale/Exhale text + main timer
 *
 * Breathing pattern (4-7-8 simplified to inhale/exhale for visual clarity):
 *   Inhale  →  4 seconds  (circle expands from 0.5x to 1.0x)
 *   Hold    →  4 seconds  (circle stays at 1.0x, label shows "Hold")
 *   Exhale  →  8 seconds  (circle contracts from 1.0x to 0.5x)
 *   Repeat until main timer ends.
 *
 * Fragment Result: fires RESULT_COMPLETE when activity finishes.
 * RoutineExecutionFragment listens and advances to the next step.
 */
class BreathingActivityFragment : Fragment() {

    // ── ViewBinding ───────────────────────────────────────────────────────────

    private var _binding: FragmentBreathingActivityBinding? = null
    private val binding get() = _binding!!

    // ── State ─────────────────────────────────────────────────────────────────

    private enum class ActivityState {
        PRE_COUNTDOWN, BREATHING, WARNING, TIMES_UP, COMPLETE
    }

    private enum class BreathPhase { INHALE, HOLD, EXHALE }

    private var state = ActivityState.PRE_COUNTDOWN
    private var currentPhase = BreathPhase.INHALE

    // ── Timers & Handlers ─────────────────────────────────────────────────────

    private var preCountdownTimer: CountDownTimer? = null
    private var mainTimer: CountDownTimer? = null
    private val breathingHandler = Handler(Looper.getMainLooper())
    private var breathingRunnable: Runnable? = null

    // ── Data ──────────────────────────────────────────────────────────────────

    private lateinit var activityData: Activity

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        const val RESULT_COMPLETE = "breathing_complete"

        private const val ARG_ACTIVITY_JSON = "arg_activity_json"

        private const val PRE_COUNTDOWN_MS  = 15_000L
        private const val WARNING_MS        = 30_000L
        private const val TICK_MS           = 1_000L
        private const val TIMES_UP_LINGER   = 1_500L
        private const val COMPLETE_LINGER   = 2_500L

        // Breathing cycle durations (ms)
        private const val INHALE_MS = 4_000L
        private const val HOLD_MS   = 4_000L
        private const val EXHALE_MS = 8_000L

        // Circle scale bounds
        private const val SCALE_MIN = 0.5f
        private const val SCALE_MAX = 1.0f

        fun newInstance(activity: Activity): BreathingActivityFragment =
            BreathingActivityFragment().apply {
                arguments = bundleOf(
                    ARG_ACTIVITY_JSON to Json.encodeToString(activity)
                )
            }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBreathingActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activityData = Json.decodeFromString(
            requireArguments().getString(ARG_ACTIVITY_JSON)!!
        )
        startPreCountdown()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelAll()
        _binding = null
    }

    // ── State Machine ─────────────────────────────────────────────────────────

    private fun transitionTo(newState: ActivityState) {
        state = newState
        when (newState) {
            ActivityState.PRE_COUNTDOWN -> showPreCountdownPanel()
            ActivityState.BREATHING,
            ActivityState.WARNING       -> showBreathingPanel()
            ActivityState.TIMES_UP      -> showTimesUp()
            ActivityState.COMPLETE      -> showCompletion()
        }
    }

    // ── Panel Visibility ──────────────────────────────────────────────────────

    private fun showPreCountdownPanel() {
        binding.preCountdownPanel.visibility = View.VISIBLE
        binding.breathingPanel.visibility    = View.GONE
        binding.timesUpOverlay.visibility    = View.GONE
        binding.completionOverlay.visibility = View.GONE
    }

    private fun showBreathingPanel() {
        binding.preCountdownPanel.visibility = View.GONE
        binding.timesUpOverlay.visibility    = View.GONE
        binding.completionOverlay.visibility = View.GONE

        binding.breathingPanel.alpha = 0f
        binding.breathingPanel.visibility = View.VISIBLE
        binding.breathingPanel.animate().alpha(1f).setDuration(400).start()
    }

    private fun showTimesUp() {
        stopBreathingCycle()
        binding.preCountdownPanel.visibility = View.GONE
        binding.breathingPanel.visibility    = View.GONE
        binding.completionOverlay.visibility = View.GONE

        binding.timesUpOverlay.alpha = 0f
        binding.timesUpOverlay.visibility = View.VISIBLE
        binding.timesUpOverlay.animate().alpha(1f).setDuration(300).start()

        binding.root.postDelayed({ transitionTo(ActivityState.COMPLETE) }, TIMES_UP_LINGER)
    }

    private fun showCompletion() {
        binding.preCountdownPanel.visibility = View.GONE
        binding.breathingPanel.visibility    = View.GONE
        binding.timesUpOverlay.visibility    = View.GONE

        binding.completionOverlay.alpha = 0f
        binding.completionOverlay.visibility = View.VISIBLE
        binding.completionOverlay.animate().alpha(1f).setDuration(500).start()

        pulseText(binding.tvCompletionMessage)

        binding.root.postDelayed({
            setFragmentResult(RESULT_COMPLETE, bundleOf())
        }, COMPLETE_LINGER)
    }

    // ── Pre-Countdown Timer ───────────────────────────────────────────────────

    private fun startPreCountdown() {
        transitionTo(ActivityState.PRE_COUNTDOWN)
        updatePreTimer(PRE_COUNTDOWN_MS)

        preCountdownTimer = object : CountDownTimer(PRE_COUNTDOWN_MS, TICK_MS) {
            override fun onTick(ms: Long) = updatePreTimer(ms)
            override fun onFinish() {
                transitionTo(ActivityState.BREATHING)
                startMainTimer()
                startBreathingCycle()
            }
        }.start()
    }

    private fun updatePreTimer(ms: Long) {
        val sec = (ms / 1000).toInt()
        binding.tvPreTimer.text = String.format("%02d : %02d", sec / 60, sec % 60)
        // Pre-countdown is always green
        binding.tvPreTimer.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.timer_green)
        )
    }

    // ── Main Activity Timer ───────────────────────────────────────────────────

    private fun startMainTimer() {
        val durationMs = activityData.defaultDurationMinutes * 60_000L
        updateMainTimer(durationMs)

        mainTimer = object : CountDownTimer(durationMs, TICK_MS) {
            override fun onTick(ms: Long) {
                if (ms <= WARNING_MS && state != ActivityState.WARNING) {
                    transitionTo(ActivityState.WARNING)
                }
                updateMainTimer(ms)
            }

            override fun onFinish() = transitionTo(ActivityState.TIMES_UP)
        }.start()
    }

    private fun updateMainTimer(ms: Long) {
        val sec = (ms / 1000).toInt()
        binding.tvMainTimer.text = String.format("%02d : %02d", sec / 60, sec % 60)
        binding.tvMainTimer.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (ms <= WARNING_MS) R.color.timer_red else R.color.timer_default
            )
        )
    }

    // ── Breathing Cycle ───────────────────────────────────────────────────────

    /**
     * Starts the repeating inhale → hold → exhale cycle.
     * Uses Handler.postDelayed to sequence the phases without blocking the thread.
     */
    private fun startBreathingCycle() {
        runInhalePhase()
    }

    private fun runInhalePhase() {
        if (state != ActivityState.BREATHING && state != ActivityState.WARNING) return
        currentPhase = BreathPhase.INHALE
        binding.tvBreathPhase.text = "Inhale"
        animateCircle(from = SCALE_MIN, to = SCALE_MAX, durationMs = INHALE_MS)
        scheduleNext(INHALE_MS) { runHoldPhase() }
    }

    private fun runHoldPhase() {
        if (state != ActivityState.BREATHING && state != ActivityState.WARNING) return
        currentPhase = BreathPhase.HOLD
        binding.tvBreathPhase.text = "Hold"
        // Circle stays at max scale — no animation needed
        scheduleNext(HOLD_MS) { runExhalePhase() }
    }

    private fun runExhalePhase() {
        if (state != ActivityState.BREATHING && state != ActivityState.WARNING) return
        currentPhase = BreathPhase.EXHALE
        binding.tvBreathPhase.text = "Exhale"
        animateCircle(from = SCALE_MAX, to = SCALE_MIN, durationMs = EXHALE_MS)
        scheduleNext(EXHALE_MS) { runInhalePhase() }
    }

    private fun scheduleNext(delayMs: Long, action: () -> Unit) {
        val runnable = Runnable { action() }
        breathingRunnable = runnable
        breathingHandler.postDelayed(runnable, delayMs)
    }

    private fun stopBreathingCycle() {
        breathingRunnable?.let { breathingHandler.removeCallbacks(it) }
        breathingRunnable = null
    }

    // ── Circle Animation ──────────────────────────────────────────────────────

    /**
     * Smoothly scales the breathing circle between [from] and [to] over [durationMs].
     */
    private fun animateCircle(from: Float, to: Float, durationMs: Long) {
        ObjectAnimator.ofPropertyValuesHolder(
            binding.breathingCircle,
            PropertyValuesHolder.ofFloat("scaleX", from, to),
            PropertyValuesHolder.ofFloat("scaleY", from, to)
        ).apply {
            duration = durationMs
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private fun cancelAll() {
        preCountdownTimer?.cancel()
        mainTimer?.cancel()
        stopBreathingCycle()
    }

    // ── Pulse Animation (completion text) ────────────────────────────────────

    private fun pulseText(target: View) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(target, "scaleX", 1f, 1.08f, 1f),
                ObjectAnimator.ofFloat(target, "scaleY", 1f, 1.08f, 1f)
            )
            duration = 600
            start()
        }
    }
}