package com.noctra.app.ui.routine.execution.activities

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.noctra.app.R
import com.noctra.app.databinding.FragmentStepperActivityBinding
import com.noctra.app.ui.routine.RoutineViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Shape B: Stepper.
 * Used by Progressive Muscle Relaxation and Bedtime Stretching.
 *
 * Flow: 15s prep countdown -> for each step: action phase (green timer) ->
 * rest phase (default-color timer) -> next step -> ... -> stays on final
 * rest state until the VM's overall activity timer fires GoToTransition.
 *
 * IMPORTANT: this fragment does NOT drive navigation to Time's Up itself.
 * It assumes Activity.defaultDurationMinutes for this activity is configured
 * to equal the sum of all step action+rest durations, so the VM's own
 * countdown (routineViewModel.activitySecondsRemaining) naturally reaches
 * zero around the same time the local step sequence finishes. If those two
 * durations drift, the UI will either finish steps early (and idle on the
 * last rest screen) or run out of steps before the VM signals completion.
 */
class StepperActivityFragment : Fragment() {

    private var _binding: FragmentStepperActivityBinding? = null
    private val binding get() = _binding!!

    private val args: StepperActivityFragmentArgs by navArgs()
    private val routineViewModel: RoutineViewModel by activityViewModels()

    private var preCountdownTimer: CountDownTimer? = null
    private var stepTimer: CountDownTimer? = null
    private var steps: List<StepperStepConfig> = emptyList()
    private var currentStepIndex = 0

    companion object {
        private const val PRE_COUNTDOWN_SECONDS = 15L
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStepperActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        steps = Json.decodeFromString(args.stepsJson)

        binding.tvPreTitle.text = args.activityTitle
        binding.tvPreInstruction.text = args.activityInstruction

        showPreCountdownPanel()
        observeVm()
        startPreCountdown()
    }

    private fun observeVm() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    routineViewModel.navigationEvent.collect { handleNavigationEvent(it) }
                }
            }
        }
    }

    private fun handleNavigationEvent(event: RoutineViewModel.NavigationEvent) {
        when (event) {
            is RoutineViewModel.NavigationEvent.GoToTransition -> {
                stopStepTimer()
                findNavController().navigate(R.id.timesUpTransitionFragment)
            }
            is RoutineViewModel.NavigationEvent.GoToCompletion -> {
                stopStepTimer()
                findNavController().navigate(R.id.routineCompletionOverlayFragment)
            }
            else -> {}
        }
    }

    // ─── Panels ───────────────────────────────────────────────────────────

    private fun showPreCountdownPanel() {
        binding.preCountdownPanel.visibility = View.VISIBLE
        binding.stepPanel.visibility = View.GONE
        updatePreTimer(PRE_COUNTDOWN_SECONDS)
    }

    private fun showStepPanel() {
        binding.preCountdownPanel.visibility = View.GONE
        binding.stepPanel.visibility = View.VISIBLE
        routineViewModel.startCurrentActivityTimer()
        currentStepIndex = 0
        runStep(currentStepIndex, isAction = true)
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
                showStepPanel()
            }
        }.start()
    }

    private fun updatePreTimer(seconds: Long) {
        val mins = seconds / 60
        val secs = seconds % 60
        binding.tvPreTimer.text = String.format("%02d : %02d", mins, secs)
    }

    // ─── Step sequence ────────────────────────────────────────────────────

    private fun runStep(index: Int, isAction: Boolean) {
        if (_binding == null || index >= steps.size) return
        val step = steps[index]
        renderStepDots(index)

        if (isAction) {
            binding.tvStepTitle.text = step.name
            binding.tvStepInstruction.text = step.instruction
            setStepImage(step.imageAsset)
            runStepTimer(step.actionDurationSeconds, isAction = true) {
                runStep(index, isAction = false)
            }
        } else {
            binding.tvStepTitle.text = args.restLabel
            binding.tvStepInstruction.text = ""
            setStepImage(null)
            runStepTimer(step.restDurationSeconds, isAction = false) {
                val nextIndex = index + 1
                if (nextIndex < steps.size) {
                    currentStepIndex = nextIndex
                    runStep(nextIndex, isAction = true)
                }
                // else: all steps done, hold on this rest screen until VM
                // fires GoToTransition (see class doc above).
            }
        }
    }

    private fun runStepTimer(durationSeconds: Int, isAction: Boolean, onFinish: () -> Unit) {
        stepTimer?.cancel()
        stepTimer = object : CountDownTimer((durationSeconds * 1000L) + 500L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = (millisUntilFinished / 1000L).coerceAtMost(durationSeconds.toLong())
                updateStepTimer(secs)
                // Action phase = green (matches wireframe's tense/pose timers).
                // Rest phase = default color (matches wireframe's Release/Rest timers).
                val colorRes = if (isAction) R.color.timer_green else R.color.timer_default
                binding.tvStepTimer.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
            }
            override fun onFinish() {
                updateStepTimer(0)
                onFinish()
            }
        }.start()
    }

    private fun updateStepTimer(seconds: Long) {
        if (_binding == null) return
        val mins = seconds / 60
        val secs = seconds % 60
        binding.tvStepTimer.text = String.format("%02d : %02d", mins, secs)
    }

    private fun stopStepTimer() {
        stepTimer?.cancel()
        stepTimer = null
    }

    private fun setStepImage(imageAssetName: String?) {
        if (_binding == null) return
        if (imageAssetName == null) {
            binding.imgStepDemo.setImageDrawable(null)
            return
        }
        // Placeholder lookup until real per-step demo images/animations are added.
        val resId = resources.getIdentifier(imageAssetName, "drawable", requireContext().packageName)
        if (resId != 0) {
            binding.imgStepDemo.setImageResource(resId)
        } else {
            binding.imgStepDemo.setImageDrawable(null)
        }
    }

    // ─── Progress dots (generated in code, no extra drawable resources needed) ──

    private fun renderStepDots(activeIndex: Int) {
        if (_binding == null) return
        val container = binding.dotsContainer
        container.removeAllViews()
        val dotSizePx = (8 * resources.displayMetrics.density).toInt()
        val marginPx = (4 * resources.displayMetrics.density).toInt()
        for (i in steps.indices) {
            val dot = View(requireContext())
            val params = android.widget.LinearLayout.LayoutParams(dotSizePx, dotSizePx)
            params.marginStart = marginPx
            params.marginEnd = marginPx
            dot.layoutParams = params
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                val color = if (i == activeIndex) "#2C1769" else "#D9D5EC"
                setColor(android.graphics.Color.parseColor(color))
            }
            dot.background = drawable
            container.addView(dot)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        preCountdownTimer?.cancel()
        preCountdownTimer = null
        stopStepTimer()
        _binding = null
    }
}