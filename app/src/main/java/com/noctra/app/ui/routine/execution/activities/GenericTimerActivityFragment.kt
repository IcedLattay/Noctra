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
import com.noctra.app.R
import com.noctra.app.databinding.FragmentGenericTimerActivityBinding
import com.noctra.app.ui.routine.RoutineViewModel
import kotlinx.coroutines.launch

/**
 * GenericTimerActivityFragment — Stepper shape.
 *
 * Serves: Progressive Muscle Relaxation, Bedtime Stretching. Both alternate
 * an "action" phase (tense / pose, green timer) and a "rest" phase (default
 * color timer) across a fixed list of sub-steps.
 *
 * REBUILT from a flat single-timer fragment (previous version had no
 * sub-step sequence at all — just one icon/title/instruction/timer for the
 * whole activity). This version adds the real 5-step sequence with
 * per-step demo image, name, instruction, progress dots, and alternating
 * action/rest timers, matching the wireframes.
 *
 * IMPORTANT: this fragment computes its own total duration (sum of all step
 * action+rest durations) and passes that into
 * routineViewModel.startCurrentActivityTimer(durationSeconds) — NOT the
 * default 15s. Without this, the VM's own countdown fires GoToTransition at
 * 15s regardless of how many steps remain, cutting the sequence off early.
 *
 * Preserves from the previous version: the btnCompleteRoutine button shown
 * only on the last activity in the whole routine once its timer hits 0
 * (routineViewModel.isLastStep), wired to onCompleteRoutineTapped().
 *
 * Dropped from the previous version: the inline tvCompletionMessage /
 * completionMessageFor() lookup. Per-activity completion messaging is
 * handled by TimesUpTransitionFragment elsewhere in the app (not yet wired
 * with real messages, but that's the intended single place for it) — kept
 * out of this fragment for consistency with Audioscape/Breathing/Gratitude,
 * none of which have their own inline completion message either.
 */
class GenericTimerActivityFragment : Fragment() {

    private var _binding: FragmentGenericTimerActivityBinding? = null
    private val binding get() = _binding!!

    private val routineViewModel: RoutineViewModel by activityViewModels()

    private var preCountdownTimer: CountDownTimer? = null
    private var stepTimer: CountDownTimer? = null
    private var steps: List<StepperStepConfig> = emptyList()
    private var restLabel: String = "Rest."
    private var currentStepIndex = 0

    companion object {
        private const val PRE_COUNTDOWN_SECONDS = 15L

        // TEMPORARY FOR TESTING — set this back to false once all 9 activities
        // have been manually verified, to restore real per-step durations
        // (5s/10s for PMR, 3s/15s for Stretching). This is the ONLY flag that
        // needs to change; nothing else in this file depends on it.
        private const val TEST_MODE_SHORT_DURATIONS = true
        private const val TEST_ACTION_SECONDS = 1
        private const val TEST_REST_SECONDS = 2

        // Not part of the DB schema — step-by-step breakdown per activity label.
        // Placeholder ic_* drawable names — swap for real demo asset names.
        // These are the REAL durations — left untouched by TEST_MODE_SHORT_DURATIONS,
        // which overrides them at runtime via effectiveActionSeconds()/effectiveRestSeconds() below.
        private val STEPS_BY_LABEL: Map<String, Pair<String, List<StepperStepConfig>>> = mapOf(
            "Progressive Muscle Relaxation" to ("Release." to listOf(
                StepperStepConfig("hands", "Hands", "Make a fist with your hands as tight as possible for 5 seconds", "ic_pmr_hands", 5, 10),
                StepperStepConfig("arms", "Arms", "Flex your biceps of your arms as tight as you can for 5 seconds", "ic_pmr_arms", 5, 10),
                StepperStepConfig("feet", "Feet", "Curl up your feet's toes as tight as possible for 5 seconds", "ic_pmr_feet", 5, 10),
                StepperStepConfig("eyebrows", "Eyebrows", "Raise your brows for 5 seconds", "ic_pmr_eyebrows", 5, 10),
                StepperStepConfig("eyes", "Eyes", "Squeeze your eyes and make a tight smile for 5 seconds", "ic_pmr_eyes", 5, 10)
            )),
            "Bedtime Stretching" to ("Rest." to listOf(
                StepperStepConfig("neck_rolls", "Neck Rolls", "Move your head in a slow, continuous half-circle by dropping your chin to your chest and rolling it smoothly from one shoulder to the other.", "ic_stretch_neck_rolls", 3, 15),
                StepperStepConfig("shoulder_rolls", "Shoulder Rolls", "Move your shoulders in a slow, continuous circle by lifting them up toward your ears, rolling them backward, and dropping them down in a smooth motion.", "ic_stretch_shoulder_rolls", 3, 15),
                // Confirmed against wireframe: this text was misplaced onto the
                // Side Neck Stretch card there — it actually describes the
                // overhead reach motion, so it belongs here.
                StepperStepConfig("overhead_arm_reach", "Overhead Arm Reach", "Interlock your fingers with your palms facing up, then push your hands straight toward the ceiling while reaching as high as you can.", "ic_stretch_overhead_reach", 3, 15),
                // Wireframe had no genuine Side Neck Stretch copy (its card
                // duplicated Overhead Arm Reach's text) — new copy written to
                // match the tone/length of the other steps here.
                StepperStepConfig("side_neck_stretch", "Side Neck Stretch", "Gently tilt your head to one side, bringing your ear toward your shoulder, and hold before slowly returning to center and repeating on the other side.", "ic_stretch_side_neck", 3, 15),
                StepperStepConfig("seated_side_stretch", "Seated Side Stretch", "Sit down, reach one arm straight up, and lean your upper body to the opposite side until you feel a stretch along your ribs", "ic_stretch_seated_side", 3, 15)
            ))
        )
    }

    /** Returns the real duration, or the compressed test duration if TEST_MODE_SHORT_DURATIONS is on. */
    private fun effectiveActionSeconds(step: StepperStepConfig): Int =
        if (TEST_MODE_SHORT_DURATIONS) TEST_ACTION_SECONDS else step.actionDurationSeconds

    private fun effectiveRestSeconds(step: StepperStepConfig): Int =
        if (TEST_MODE_SHORT_DURATIONS) TEST_REST_SECONDS else step.restDurationSeconds

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGenericTimerActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = routineViewModel.currentActivity
        val label = activity?.label ?: ""
        val (rest, stepList) = STEPS_BY_LABEL[label] ?: ("Rest." to emptyList())
        restLabel = rest
        steps = stepList

        binding.tvPreTitle.text = label
        binding.tvPreInstruction.text = activity?.instruction ?: ""

        setupListeners()
        showPreCountdownPanel()
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

        if (steps.isEmpty()) {
            // No step config found for this label — shouldn't happen for the
            // 2 activities this fragment currently serves, but fail safe
            // rather than crash.
            routineViewModel.startCurrentActivityTimer(0)
            return
        }

        val totalDurationSeconds = steps.sumOf { effectiveActionSeconds(it) + effectiveRestSeconds(it) }
        routineViewModel.startCurrentActivityTimer(totalDurationSeconds)

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
            runStepTimer(effectiveActionSeconds(step), isAction = true) {
                runStep(index, isAction = false)
            }
        } else {
            binding.tvStepTitle.text = restLabel
            binding.tvStepInstruction.text = ""
            setStepImage(null)
            runStepTimer(effectiveRestSeconds(step), isAction = false) {
                val nextIndex = index + 1
                if (nextIndex < steps.size) {
                    currentStepIndex = nextIndex
                    runStep(nextIndex, isAction = true)
                }
                // else: all steps done. Local sequence holds here on the
                // final rest screen until the VM's own countdown (started
                // with the same total duration in showStepPanel()) fires
                // GoToTransition or reveals the Complete Routine button.
            }
        }
    }

    private fun runStepTimer(durationSeconds: Int, isAction: Boolean, onFinish: () -> Unit) {
        stepTimer?.cancel()
        // NOTE: no +500L padding here (unlike the pre-countdown timer) —
        // this duration must match exactly what's summed into
        // routineViewModel.startCurrentActivityTimer()'s total, or the VM's
        // independent countdown will fire GoToTransition before the local
        // step sequence actually finishes, cutting off the last step(s).
        stepTimer = object : CountDownTimer(durationSeconds * 1000L, 1000L) {
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