package com.noctra.app.ui.routine.execution.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.noctra.app.R
import com.noctra.app.databinding.FragmentBreathingActivityBinding
import com.noctra.app.ui.routine.RoutineViewModel
import kotlinx.coroutines.launch

/**
 * BreathingActivityFragment — Shape C: Breathing Circle.
 *
 * Serves: Slow-Paced Breathing, Mindfulness. Both use the same green
 * expanding/shrinking circle synced to a 4-7-8 Inhale/Hold/Exhale cycle.
 * Title/instruction come from routineViewModel.currentActivity.
 */
class BreathingActivityFragment : Fragment() {

    private var _binding: FragmentBreathingActivityBinding? = null
    private val binding get() = _binding!!

    private val routineViewModel: RoutineViewModel by activityViewModels()

    private var preCountdownTimer: CountDownTimer? = null
    private var breathingAnimatorSet: AnimatorSet? = null
    private var isBreathingRunning = false

    private val INHALE_MS = 4_000L
    private val HOLD_MS = 7_000L
    private val EXHALE_MS = 8_000L
    private val CIRCLE_MIN_SCALE = 0.6f
    private val CIRCLE_MAX_SCALE = 1.0f

    companion object {
        private const val PRE_COUNTDOWN_SECONDS = 15L
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBreathingActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = routineViewModel.currentActivity
        binding.tvPreTitle.text = activity?.label ?: ""
        binding.tvPreInstruction.text = activity?.instruction ?: ""

        showPreCountdownPanel()
        observeVm()
        startPreCountdown()
    }

    private fun observeVm() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    routineViewModel.activitySecondsRemaining.collect { secs ->
                        updateMainTimerDisplay(secs.toLong())
                        updateMainTimerColor(secs.toLong())
                    }
                }
                launch {
                    routineViewModel.navigationEvent.collect { handleNavigationEvent(it) }
                }
            }
        }
    }

    private fun handleNavigationEvent(event: RoutineViewModel.NavigationEvent) {
        when (event) {
            is RoutineViewModel.NavigationEvent.GoToTransition -> {
                stopBreathingAnimation()
                findNavController().navigate(R.id.timesUpTransitionFragment)
            }
            is RoutineViewModel.NavigationEvent.GoToCompletion -> {
                stopBreathingAnimation()
                findNavController().navigate(R.id.routineCompletionOverlayFragment)
            }
            else -> {}
        }
    }

    // ─── Panels ───────────────────────────────────────────────────────────

    private fun showPreCountdownPanel() {
        binding.preCountdownPanel.visibility = View.VISIBLE
        binding.breathingPanel.visibility = View.GONE
        updatePreTimer(PRE_COUNTDOWN_SECONDS)
    }

    private fun showBreathingPanel() {
        binding.preCountdownPanel.visibility = View.GONE
        binding.breathingPanel.visibility = View.VISIBLE
        startBreathingLoop()
        val durationSeconds = (routineViewModel.currentActivity?.defaultDurationMinutes ?: 0) * 60
        routineViewModel.startCurrentActivityTimer(durationSeconds)
    }

    // ─── Pre-countdown ──────────────────────────────────────────────────────

    private fun startPreCountdown() {
        preCountdownTimer = object : CountDownTimer((PRE_COUNTDOWN_SECONDS * 1000L) + 500L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = (millisUntilFinished / 1000L).coerceAtMost(PRE_COUNTDOWN_SECONDS)
                updatePreTimer(secs)
                val colorRes = if (secs <= 5) R.color.timer_red else R.color.timer_green
                binding.tvPreTimer.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
            }
            override fun onFinish() {
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

    private fun updateMainTimerDisplay(seconds: Long) {
        if (_binding == null) return
        val mins = seconds / 60
        val secs = seconds % 60
        binding.tvMainTimer.text = String.format("%02d : %02d", mins, secs)
    }

    private fun updateMainTimerColor(seconds: Long) {
        if (_binding == null) return
        val colorRes = if (seconds <= 5) R.color.timer_red else R.color.timer_default
        binding.tvMainTimer.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    // ─── Breathing Animation (4-7-8) ──────────────────────────────────────

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
                setPhaseLabel("Inhale", "#4CAF50")
                animateCircle(CIRCLE_MIN_SCALE, CIRCLE_MAX_SCALE, INHALE_MS) {
                    runNextBreathPhase(BreathPhase.HOLD)
                }
            }
            BreathPhase.HOLD -> {
                setPhaseLabel("Hold", "#7C4DFF")
                binding.root.postDelayed({
                    if (isBreathingRunning) runNextBreathPhase(BreathPhase.EXHALE)
                }, HOLD_MS)
            }
            BreathPhase.EXHALE -> {
                setPhaseLabel("Exhale", "#5C6BC0")
                animateCircle(CIRCLE_MAX_SCALE, CIRCLE_MIN_SCALE, EXHALE_MS) {
                    runNextBreathPhase(BreathPhase.INHALE)
                }
            }
        }
    }

    private fun animateCircle(fromScale: Float, toScale: Float, duration: Long, onEnd: () -> Unit) {
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

    override fun onDestroyView() {
        super.onDestroyView()
        preCountdownTimer?.cancel()
        preCountdownTimer = null
        stopBreathingAnimation()
        _binding = null
    }
}