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

class BreathingActivityFragment : Fragment() {

    private var _binding: FragmentBreathingActivityBinding? = null
    private val binding get() = _binding!!

    private val routineViewModel: RoutineViewModel by activityViewModels()

    // Pre-countdown is a local 15s warm-up. VM doesn't know about it.
    private var preCountdownTimer: CountDownTimer? = null

    private var breathingAnimatorSet: AnimatorSet? = null
    private var isBreathingRunning = false

    private val INHALE_MS = 4_000L
    private val HOLD_MS = 7_000L
    private val EXHALE_MS = 8_000L
    private val CIRCLE_MIN_SCALE = 0.6f
    private val CIRCLE_MAX_SCALE = 1.0f

    companion object { private const val PRE_COUNTDOWN_SECONDS = 15L }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBreathingActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showPreCountdownPanel()
        setupListeners()
        observeVm()
        startPreCountdown()
    }

    private fun setupListeners() {
        binding.btnCompleteRoutine.setOnClickListener {
            routineViewModel.onCompleteRoutineTapped()
        }
    }

    private fun observeVm() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    routineViewModel.activitySecondsRemaining.collect { secs ->
                        updateMainTimerDisplay(secs.toLong())
                        updateMainTimerColor(secs.toLong())
                        
                        // Show "Complete Routine" button if timer is 0 AND it's the last step
                        if (secs == 0 && routineViewModel.isLastStep) {
                            binding.btnCompleteRoutine.visibility = View.VISIBLE
                        } else {
                            binding.btnCompleteRoutine.visibility = View.GONE
                        }
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
            else -> { /* not for us */ }
        }
    }

    // ─── Panels ───────────────────────────────────────────────────────────────

    private fun showPreCountdownPanel() {
        binding.preCountdownPanel.visibility = View.VISIBLE
        binding.breathingPanel.visibility = View.GONE
        updatePreTimer(PRE_COUNTDOWN_SECONDS)
    }

    private fun showBreathingPanel() {
        binding.preCountdownPanel.visibility = View.GONE
        binding.breathingPanel.visibility = View.VISIBLE
        startBreathingLoop()
        routineViewModel.startCurrentActivityTimer()
    }

    // ─── Pre-countdown ────────────────────────────────────────────────────────

    private fun startPreCountdown() {
        val totalDurationMillis = (PRE_COUNTDOWN_SECONDS * 1000L) + 500L
        preCountdownTimer = object : CountDownTimer(totalDurationMillis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = millisUntilFinished / 1000L
                val display = if (secs > PRE_COUNTDOWN_SECONDS) PRE_COUNTDOWN_SECONDS else secs
                updatePreTimer(display)
                val colorRes = if (display <= 5) R.color.timer_red else R.color.timer_green
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

    // ─── Main Timer Display (driven by VM) ────────────────────────────────────

    private fun updateMainTimerDisplay(seconds: Long) {
        if (_binding == null) return
        val mins = seconds / 60
        val secs = seconds % 60
        binding.tvMainTimer.text = String.format("%02d : %02d", mins, secs)
    }

    private fun updateMainTimerColor(seconds: Long) {
        if (_binding == null) return
        val colorRes = when {
            seconds <= 5 -> R.color.timer_red
            seconds <= 30 -> R.color.timer_green
            else -> R.color.timer_default
        }
        binding.tvMainTimer.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    // ─── Breathing Animation (4-7-8) ──────────────────────────────────────────

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