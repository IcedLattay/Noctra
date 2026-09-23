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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * GenericTimerActivityFragment — Stepper shape.
 *
 * Serves: Progressive Muscle Relaxation, Bedtime Stretching. Both alternate
 * an "action" phase (tense / pose, green timer) and a "rest" phase (default
 * color timer) across a fixed list of sub-steps.
 *
 * Frame cycling: each sub-activity's imageAssets list can be ANY length —
 * confirmed real asset counts below are 1, 2, 3, or 4, not a fixed number.
 * During the action phase, frames cycle forward on a 1s interval, wrapping
 * to the first frame after the last. A single-frame list just displays
 * statically. The loop runs ONLY during the action phase — rest phase
 * shows no image.
 *
 * IMPORTANT: computes its own total duration (sum of all step action+rest
 * durations) and passes it into routineViewModel.startCurrentActivityTimer().
 * Without this, the VM's own countdown fires GoToTransition at the 15s
 * default regardless of how many steps remain.
 */
class GenericTimerActivityFragment : Fragment() {

    private var _binding: FragmentGenericTimerActivityBinding? = null
    private val binding get() = _binding!!

    private val routineViewModel: RoutineViewModel by activityViewModels()

    private var preCountdownTimer: CountDownTimer? = null
    private var stepTimer: CountDownTimer? = null
    private var frameLoopJob: Job? = null
    private var steps: List<StepperStepConfig> = emptyList()
    private var restLabel: String = "Rest."
    private var currentStepIndex = 0

    companion object {
        private const val PRE_COUNTDOWN_SECONDS = 15L
        private const val FRAME_INTERVAL_MILLIS = 1000L

        // TEMPORARY FOR TESTING — set back to false once all activities
        // verified, to restore real durations.
        private const val TEST_MODE_SHORT_DURATIONS = true
        private const val TEST_ACTION_SECONDS = 1
        private const val TEST_REST_SECONDS = 2

        // Real asset filenames (confirmed, no longer placeholders), minus
        // the .png extension — Android's getIdentifier() looks up drawable
        // resources by name without the file extension.
        //
        // Confirmed frame counts per sub-activity:
        //   PMR:        arms=1, eyes=1, eyebrows=1, hands=1, feet(toes)=1
        //   Stretching: neck_rolls=4, shoulder_rolls=3,
        //               overhead_arm_reach=1, side_neck_stretch=2,
        //               seated_side_stretch=2
        // PMR is now entirely single-frame (static) — a change from an
        // earlier draft that assumed 2 frames each; real assets came back
        // as 1 static pose per PMR sub-activity.
        private val STEPS_BY_LABEL: Map<String, Pair<String, List<StepperStepConfig>>> = mapOf(
            "Progressive Muscle Relaxation" to ("Release." to listOf(
                StepperStepConfig("hands", "Hands", "Make a fist with your hands as tight as possible for 5 seconds", listOf("hands"), 5, 10),
                StepperStepConfig("arms", "Arms", "Flex your biceps of your arms as tight as you can for 5 seconds", listOf("arm1"), 5, 10),
                StepperStepConfig("feet", "Feet", "Curl up your feet's toes as tight as possible for 5 seconds", listOf("toes"), 5, 10),
                StepperStepConfig("eyebrows", "Eyebrows", "Raise your brows for 5 seconds", listOf("eyebrow"), 5, 10),
                StepperStepConfig("eyes", "Eyes", "Squeeze your eyes and make a tight smile for 5 seconds", listOf("eye"), 5, 10)
            )),
            "Bedtime Stretching" to ("Rest." to listOf(
                // 4 frames: left hold -> left transition -> right hold ->
                // right transition -> loops back to left hold.
                StepperStepConfig("neck_rolls", "Neck Rolls", "Move your head in a slow, continuous half-circle by dropping your chin to your chest and rolling it smoothly from one shoulder to the other.", listOf("neckrolllefthold", "neckrollllefttransition", "neckrollrighthold", "neckrollrighttransition"), 3, 15),
                // 3 frames: up -> back -> down, matching the physical motion.
                StepperStepConfig("shoulder_rolls", "Shoulder Rolls", "Move your shoulders in a slow, continuous circle by lifting them up toward your ears, rolling them backward, and dropping them down in a smooth motion.", listOf("shoulderollup", "shoulderollback", "shoulderolldown"), 3, 15),
                StepperStepConfig("overhead_arm_reach", "Overhead Arm Reach", "Interlock your fingers with your palms facing up, then push your hands straight toward the ceiling while reaching as high as you can.", listOf("overheadarmreach"), 3, 15),
                StepperStepConfig("side_neck_stretch", "Side Neck Stretch", "Gently tilt your head to one side, bringing your ear toward your shoulder, and hold before slowly returning to center and repeating on the other side.", listOf("sideneckstretchleft", "sideneckstretchright"), 3, 15),
                StepperStepConfig("seated_side_stretch", "Seated Side Stretch", "Sit down, reach one arm straight up, and lean your upper body to the opposite side until you feel a stretch along your ribs", listOf("seatedsidestretchleft", "seatedsidestretchright"), 3, 15)
            ))
        )
    }

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

    private fun showPreCountdownPanel() {
        binding.preCountdownPanel.visibility = View.VISIBLE
        binding.stepPanel.visibility = View.GONE
        updatePreTimer(PRE_COUNTDOWN_SECONDS)
    }

    private fun showStepPanel() {
        binding.preCountdownPanel.visibility = View.GONE
        binding.stepPanel.visibility = View.VISIBLE

        if (steps.isEmpty()) {
            routineViewModel.startCurrentActivityTimer(0)
            return
        }

        val totalDurationSeconds = steps.sumOf { effectiveActionSeconds(it) + effectiveRestSeconds(it) }
        routineViewModel.startCurrentActivityTimer(totalDurationSeconds)

        currentStepIndex = 0
        runStep(currentStepIndex, isAction = true)
    }

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

    private fun runStep(index: Int, isAction: Boolean) {
        if (_binding == null || index >= steps.size) return
        val step = steps[index]
        renderStepDots(index)

        if (isAction) {
            binding.tvStepTitle.text = step.name
            binding.tvStepInstruction.text = step.instruction
            startFrameLoop(step.imageAssets)
            runStepTimer(effectiveActionSeconds(step), isAction = true) {
                runStep(index, isAction = false)
            }
        } else {
            binding.tvStepTitle.text = restLabel
            binding.tvStepInstruction.text = ""
            stopFrameLoop()
            runStepTimer(effectiveRestSeconds(step), isAction = false) {
                val nextIndex = index + 1
                if (nextIndex < steps.size) {
                    currentStepIndex = nextIndex
                    runStep(nextIndex, isAction = true)
                }
            }
        }
    }

    private fun runStepTimer(durationSeconds: Int, isAction: Boolean, onFinish: () -> Unit) {
        stepTimer?.cancel()
        stepTimer = object : CountDownTimer(durationSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = (millisUntilFinished / 1000L).coerceAtMost(durationSeconds.toLong())
                updateStepTimer(secs)
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

    /**
     * Cycles through `frames` at FRAME_INTERVAL_MILLIS (1s). Works for any
     * list length: empty clears the image, 1 item shows statically, 2+
     * items cycle forward and wrap to index 0. Runs only during action
     * phase — stopFrameLoop() runs at the start of every rest phase.
     */
    private fun startFrameLoop(frames: List<String>) {
        stopFrameLoop()

        if (frames.isEmpty()) {
            setStepImage(null)
            return
        }

        if (frames.size == 1) {
            setStepImage(frames[0])
            return
        }

        frameLoopJob = viewLifecycleOwner.lifecycleScope.launch {
            var frameIndex = 0
            while (true) {
                setStepImage(frames[frameIndex])
                delay(FRAME_INTERVAL_MILLIS)
                frameIndex = (frameIndex + 1) % frames.size
            }
        }
    }

    private fun stopFrameLoop() {
        frameLoopJob?.cancel()
        frameLoopJob = null
    }

    private fun setStepImage(imageAssetName: String?) {
        if (_binding == null) return
        if (imageAssetName == null) {
            binding.imgStepDemo.setImageDrawable(null)
            return
        }
        val resId = resources.getIdentifier(imageAssetName, "drawable", requireContext().packageName)
        if (resId != 0) {
            binding.imgStepDemo.setImageResource(resId)
        } else {
            binding.imgStepDemo.setImageDrawable(null)
        }
    }

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
        stopFrameLoop()
        _binding = null
    }
}