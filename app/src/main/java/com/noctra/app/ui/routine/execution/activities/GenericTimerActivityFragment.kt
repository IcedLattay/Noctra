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
 * Session 5 (R8):
 *   - Each image can have its own on-screen time (StepperStepConfig.frameDurationsMs),
 *     e.g. neck-roll holds 2s and transitions 0.2s.
 *   - REST phase shows a pause icon (ic_pause_rest) instead of the step images.
 *   - Pose lengths changed so each stretch shows its full image sequence.
 *
 * IMPORTANT: computes its own total duration (sum of all step action+rest
 * durations) and passes it into routineViewModel.startCurrentActivityTimer().
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
        private const val DEFAULT_FRAME_MILLIS = 1000L

        // TEMPORARY FOR TESTING — true shortens every pose to 1s and rest to
        // 2s, which is too short to see the image sequences. Kept FALSE so the
        // new timings can be checked; flip to true only for quick flow tests.
        private const val TEST_MODE_SHORT_DURATIONS = false
        private const val TEST_ACTION_SECONDS = 1
        private const val TEST_REST_SECONDS = 2

        // Drawable names without the .png extension.
        private val STEPS_BY_LABEL: Map<String, Pair<String, List<StepperStepConfig>>> = mapOf(

            // PMR — all static images. Tense 5s, release 10s.
            "Progressive Muscle Relaxation" to ("Release." to listOf(
                StepperStepConfig("hands", "Hands", "Make a fist with your hands as tight as possible for 5 seconds", listOf("hands"), 5, 10),
                StepperStepConfig("arms", "Arms", "Flex your biceps of your arms as tight as you can for 5 seconds", listOf("arm1"), 5, 10),
                StepperStepConfig("feet", "Feet", "Curl up your feet's toes as tight as possible for 5 seconds", listOf("toes"), 5, 10),
                StepperStepConfig("eyebrows", "Eyebrows", "Raise your brows for 5 seconds", listOf("eyebrow"), 5, 10),
                StepperStepConfig("eyes", "Eyes", "Squeeze your eyes and make a tight smile for 5 seconds", listOf("eye"), 5, 10)
            )),

            // Bedtime Stretching — rest 15s after every pose.
            "Bedtime Stretching" to ("Rest." to listOf(

                // Loop: left hold 2s -> left transition 0.2s -> right transition 0.2s
                //       -> right hold 2s -> right transition 0.2s -> left transition 0.2s
                // One full loop = 4.8s, so pose = 5s.
                StepperStepConfig(
                    "neck_rolls", "Neck Rolls",
                    "Move your head in a slow, continuous half-circle by dropping your chin to your chest and rolling it smoothly from one shoulder to the other.",
                    listOf("neckrolllefthold", "neckrolllefttransition", "neckrollrighttransition",
                        "neckrollrighthold", "neckrollrighttransition", "neckrolllefttransition"),
                    5, 15,
                    listOf(2000L, 200L, 200L, 2000L, 200L, 200L)
                ),

                // Loop: up -> back -> down, 0.5s each (1.5s per roll). Pose 3s = 2 rolls.
                StepperStepConfig(
                    "shoulder_rolls", "Shoulder Rolls",
                    "Move your shoulders in a slow, continuous circle by lifting them up toward your ears, rolling them backward, and dropping them down in a smooth motion.",
                    listOf("shoulderollup", "shoulderollback", "shoulderolldown"),
                    3, 15,
                    listOf(500L, 500L, 500L)
                ),

                // Static.
                StepperStepConfig(
                    "overhead_arm_reach", "Overhead Arm Reach",
                    "Interlock your fingers with your palms facing up, then push your hands straight toward the ceiling while reaching as high as you can.",
                    listOf("overheadarmreach"),
                    3, 15
                ),

                // Left 20s -> hold 5s -> right 20s. Pose = 45s (plays once).
                StepperStepConfig(
                    "side_neck_stretch", "Side Neck Stretch",
                    "Gently tilt your head to one side, bringing your ear toward your shoulder, and hold before slowly returning to center and repeating on the other side.",
                    listOf("sideneckstretchleft", "sideneckstretchhold", "sideneckstretchright"),
                    45, 15,
                    listOf(20_000L, 5_000L, 20_000L)
                ),

                // Left 30s -> right 30s. Pose = 60s (plays once).
                StepperStepConfig(
                    "seated_side_stretch", "Seated Side Stretch",
                    "Sit down, reach one arm straight up, and lean your upper body to the opposite side until you feel a stretch along your ribs",
                    listOf("seatedsidestretchleft", "seatedsidestretchright"),
                    60, 15,
                    listOf(30_000L, 30_000L)
                )
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
        binding.tvStepActivityLabel.text = label

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
                stopFrameLoop()
                findNavController().navigate(R.id.timesUpTransitionFragment)
            }
            is RoutineViewModel.NavigationEvent.GoToCompletion -> {
                stopStepTimer()
                stopFrameLoop()
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
                if (_binding == null) return
                val secs = (millisUntilFinished / 1000L).coerceAtMost(PRE_COUNTDOWN_SECONDS)
                updatePreTimer(secs)
                val colorRes = if (secs <= 5) R.color.timer_red else R.color.timer_green
                binding.tvPreTimer.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
            }
            override fun onFinish() {
                if (_binding == null) return
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
            binding.tvStepInstruction.visibility = View.VISIBLE
            startFrameLoop(step.imageAssets, step.frameDurationsMs)
            runStepTimer(effectiveActionSeconds(step), isAction = true) {
                runStep(index, isAction = false)
            }
        } else {
            binding.tvStepTitle.text = restLabel
            binding.tvStepInstruction.text = ""
            binding.tvStepInstruction.visibility = View.INVISIBLE // keeps layout from jumping
            stopFrameLoop()
            showRestIcon()
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
                if (_binding == null) return
                val secs = (millisUntilFinished / 1000L).coerceAtMost(durationSeconds.toLong())
                updateStepTimer(secs)
                val colorRes = if (isAction) R.color.timer_green else R.color.timer_default
                binding.tvStepTimer.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
            }
            override fun onFinish() {
                if (_binding == null) return
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
     * Plays `frames` in order during the action phase, each for its own time
     * from `durationsMs` (same order). Missing/empty durations = 1s each.
     * 1 frame = static. 2+ frames = loop until the pose timer ends
     * (stopFrameLoop() is called when rest starts).
     */
    private fun startFrameLoop(frames: List<String>, durationsMs: List<Long>) {
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
                delay(durationsMs.getOrNull(frameIndex) ?: DEFAULT_FRAME_MILLIS)
                frameIndex = (frameIndex + 1) % frames.size
            }
        }
    }

    private fun stopFrameLoop() {
        frameLoopJob?.cancel()
        frameLoopJob = null
    }

    /** REST phase: pause icon instead of the step images. */
    private fun showRestIcon() {
        if (_binding == null) return
        binding.imgStepDemo.setImageResource(R.drawable.ic_pause_rest)
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
            // Session 5 (R9): a missing/misspelled file used to CLEAR the
            // image -> blank frame in the middle of a sequence. Now the
            // previous image stays on screen, and the bad name is logged
            // (Logcat tag "GenericTimer") so it can be fixed.
            android.util.Log.w("GenericTimer", "Missing step image '$imageAssetName' — check the filename in res/drawable")
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