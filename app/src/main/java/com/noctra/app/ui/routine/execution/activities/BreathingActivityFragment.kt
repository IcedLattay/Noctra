package com.noctra.app.ui.routine.execution.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.noctra.app.R
import com.noctra.app.databinding.FragmentBreathingActivityBinding

/**
 * BreathingActivityFragment — Slow-Paced Breathing
 *
 * Routed from RoutineExecutionFragment when activity.label == "Slow-Paced Breathing"
 *
 * Flow:
 *   1. PRE-COUNTDOWN panel — 15s green countdown, Shleepy body + instructions
 *   2. BREATHING panel — animated circle pulses with 4-7-8 pattern:
 *        Inhale  4s → circle expands  (scale 0.6 → 1.0)
 *        Hold    7s → circle holds    (scale stays 1.0)
 *        Exhale  8s → circle shrinks  (scale 1.0 → 0.6)
 *      Main countdown timer runs from activity duration down to 0.
 *   3. TIME'S UP overlay — alarm clock, "Time's Up!" shown.
 *   4. COMPLETION overlay — green glow + motivational message.
 *      Does NOT auto-advance — parent (RoutineExecutionFragment) manages that.
 *
 * Notifies parent via ActivityCompletionListener when timer ends.
 */
class BreathingActivityFragment : Fragment() {

    private var _binding: FragmentBreathingActivityBinding? = null
    private val binding get() = _binding!!

    // ── Timers ───────────────────────────────────────────────────────────────
    private var preCountdownTimer: CountDownTimer? = null
    private var mainTimer: CountDownTimer? = null
    private var remainingMainSeconds: Long = 0

    // ── Breathing animation ──────────────────────────────────────────────────
    private var breathingAnimatorSet: AnimatorSet? = null
    private var isBreathingRunning = false

    // 4-7-8 pattern in milliseconds
    private val INHALE_MS  = 4_000L
    private val HOLD_MS    = 7_000L
    private val EXHALE_MS  = 8_000L

    private val CIRCLE_MIN_SCALE = 0.6f
    private val CIRCLE_MAX_SCALE = 1.0f

    companion object {
        const val ARG_DURATION_SECONDS = "arg_duration_seconds"
        private const val PRE_COUNTDOWN_SECONDS = 15L

        fun newInstance(durationSeconds: Int): BreathingActivityFragment {
            return BreathingActivityFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_DURATION_SECONDS, durationSeconds)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBreathingActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val durationSeconds = arguments?.getInt(ARG_DURATION_SECONDS, 10) ?: 10
        remainingMainSeconds = durationSeconds.toLong()

        showPreCountdownPanel()
        startPreCountdown()
    }

    // ── Panel switches ────────────────────────────────────────────────────────

    private fun showPreCountdownPanel() {
        binding.preCountdownPanel.visibility = View.VISIBLE
        binding.breathingPanel.visibility    = View.GONE
        binding.timesUpOverlay.visibility    = View.GONE
        binding.completionOverlay.visibility = View.GONE
        updatePreTimer(PRE_COUNTDOWN_SECONDS)
    }

    private fun showBreathingPanel() {
        binding.preCountdownPanel.visibility = View.GONE
        binding.breathingPanel.visibility    = View.VISIBLE
        binding.timesUpOverlay.visibility    = View.GONE
        binding.completionOverlay.visibility = View.GONE
        updateMainTimer(remainingMainSeconds)
        updateTimerColor(remainingMainSeconds)
        startBreathingLoop()
        startMainTimer()
    }

    private fun showTimesUpOverlay() {
        stopBreathingAnimation()
        binding.preCountdownPanel.visibility = View.GONE
        binding.breathingPanel.visibility    = View.GONE
        binding.timesUpOverlay.visibility    = View.VISIBLE
        binding.completionOverlay.visibility = View.GONE

        // Brief pause then show completion
        binding.root.postDelayed({ showCompletionOverlay() }, 1_500L)
    }

    private fun showCompletionOverlay() {
        binding.timesUpOverlay.visibility    = View.GONE
        binding.completionOverlay.visibility = View.VISIBLE

        // Notify parent — it decides when to advance
        (parentFragment as? ActivityCompletionListener)?.onActivityComplete()
    }

    // ── Pre-countdown (15s) ──────────────────────────────────────────────────

    private fun startPreCountdown() {
        // Fix: Add a 500ms safety cushion buffer so the first tick calculation evaluates to exactly 15
        val totalDurationMillis = (PRE_COUNTDOWN_SECONDS * 1000L) + 500L

        preCountdownTimer = object : CountDownTimer(totalDurationMillis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                // Fix: Divide the raw milliseconds to calculate the current true second step
                val secs = millisUntilFinished / 1000L

                // Prevent overflow display edge-case if the safety cushion is checked too quickly
                val displaySecs = if (secs > PRE_COUNTDOWN_SECONDS) PRE_COUNTDOWN_SECONDS else secs

                updatePreTimer(displaySecs)

                // Turn red in last 5s
                val colorRes = if (displaySecs <= 5) R.color.timer_red else R.color.timer_green
                binding.tvPreTimer.setTextColor(
                    ContextCompat.getColor(requireContext(), colorRes)
                )
            }
            override fun onFinish() {
                // Hardcode final precision clean up before switching states
                updatePreTimer(0)
                showBreathingPanel()
            }
        }.start()
    }

    private fun updatePreTimer(seconds: Long) {
        val mins = seconds / 60
        val secs = seconds % 60
        binding.tvPreTimer.text = String.format("%02d : %02d", mins, secs)
    }

    // ── Main countdown ────────────────────────────────────────────────────────

    private fun startMainTimer() {
        mainTimer = object : CountDownTimer(remainingMainSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMainSeconds = millisUntilFinished / 1000L
                updateMainTimer(remainingMainSeconds)
                updateTimerColor(remainingMainSeconds)
            }
            override fun onFinish() {
                remainingMainSeconds = 0
                showTimesUpOverlay()
            }
        }.start()
    }

    private fun updateMainTimer(seconds: Long) {
        val mins = seconds / 60
        val secs = seconds % 60
        binding.tvMainTimer.text = String.format("%02d : %02d", mins, secs)
    }

    private fun updateTimerColor(seconds: Long) {
        val colorRes = when {
            seconds <= 5  -> R.color.timer_red
            seconds <= 30 -> R.color.timer_green
            else          -> R.color.timer_default
        }
        binding.tvMainTimer.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    // ── Breathing circle animation (4-7-8 loop) ───────────────────────────────

    /**
     * Pulses the breathingCircle view with the 4-7-8 pattern.
     * Each cycle:
     *   Inhale  4s — scale 0.6 → 1.0, label "Inhale", color green
     *   Hold    7s — scale stays 1.0,  label "Hold",   color purple
     *   Exhale  8s — scale 1.0 → 0.6, label "Exhale",  color blue-grey
     * Loops indefinitely until stopBreathingAnimation() is called.
     */
    private fun startBreathingLoop() {
        if (isBreathingRunning) return
        isBreathingRunning = true
        runNextBreathPhase(BreathPhase.INHALE)
    }

    private enum class BreathPhase { INHALE, HOLD, EXHALE }

    private fun runNextBreathPhase(phase: BreathPhase) {
        if (!isBreathingRunning || _binding == null) return

        when (phase) {
            BreathPhase.INHALE -> {
                setPhaseLabel("Inhale", "#4CAF50")   // green
                animateCircle(
                    fromScale = CIRCLE_MIN_SCALE,
                    toScale   = CIRCLE_MAX_SCALE,
                    duration  = INHALE_MS
                ) { runNextBreathPhase(BreathPhase.HOLD) }
            }
            BreathPhase.HOLD -> {
                setPhaseLabel("Hold", "#7C4DFF")      // purple
                // No scale animation — just hold and wait
                binding.root.postDelayed({
                    if (isBreathingRunning) runNextBreathPhase(BreathPhase.EXHALE)
                }, HOLD_MS)
            }
            BreathPhase.EXHALE -> {
                setPhaseLabel("Exhale", "#5C6BC0")    // blue-grey
                animateCircle(
                    fromScale = CIRCLE_MAX_SCALE,
                    toScale   = CIRCLE_MIN_SCALE,
                    duration  = EXHALE_MS
                ) { runNextBreathPhase(BreathPhase.INHALE) }
            }
        }
    }

    private fun animateCircle(
        fromScale: Float,
        toScale: Float,
        duration: Long,
        onEnd: () -> Unit
    ) {
        val circle = binding.breathingCircle

        val scaleX = ObjectAnimator.ofFloat(circle, "scaleX", fromScale, toScale)
        val scaleY = ObjectAnimator.ofFloat(circle, "scaleY", fromScale, toScale)

        breathingAnimatorSet = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (isBreathingRunning) onEnd()
                }
            })
            start()
        }
    }

    private fun setPhaseLabel(text: String, hexColor: String) {
        if (_binding == null) return
        binding.tvBreathPhase.text = text
        binding.tvBreathPhase.setTextColor(android.graphics.Color.parseColor(hexColor))
    }

    private fun stopBreathingAnimation() {
        isBreathingRunning = false
        breathingAnimatorSet?.cancel()
        breathingAnimatorSet = null
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onDestroyView() {
        super.onDestroyView()
        preCountdownTimer?.cancel()
        preCountdownTimer = null
        mainTimer?.cancel()
        mainTimer = null
        stopBreathingAnimation()
        _binding = null
    }

    interface ActivityCompletionListener {
        fun onActivityComplete()
    }
}