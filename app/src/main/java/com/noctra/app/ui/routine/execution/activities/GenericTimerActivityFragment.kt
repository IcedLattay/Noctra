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
 * Session 5 (R12) — SINGLE TIMER: the VM's activitySecondsRemaining is the
 * only clock during execution. Phase (action/rest), step, dots, text and the
 * on-screen countdown are all DERIVED from elapsed = total - remaining, so
 * the visual can't drift from the real timer, and a paused/stopped VM timer
 * stops the visual too. The sub-second image loop (200ms frames can't come
 * from 1s ticks) is cosmetic only: it starts when the VM-derived phase
 * becomes "action" and is cancelled the moment the phase changes.
 * The 15s pre-countdown is a separate, untimed intro; the VM timer starts
 * only after it.
 *
 * Session 5 (R13) — new phase structure per exercise:
 *   PMR:        Instructions (5s) -> Execution (5s) -> Releasing tension (10s)
 *   Stretching: Instructions (5–15s) -> Preparation (3s) -> Execution (60s)
 *               -> Break (15s)
 * Session 5 (R14): the exercise visual (image / image sequence) plays ONLY
 * during INSTRUCTION. PREP and EXECUTION show no exercise image; REST keeps
 * the pause icon.
 * Session 5 (R15): Side Neck Stretch shows text cues during EXECUTION
 * (Hold Left 25s -> Rest 10s -> Hold Right 25s), derived from the same
 * single timer. Instruction-screen image timings shortened to fit:
 * Side Neck 2s/1s/2s, Seated Side 7s/8s.
 * Session 5 (R16): EXECUTION shows a big red "EXECUTE" in the demo area.
 * REST shows the pause symbol alone (no circle, no grey box) and a green
 * "Rest." / "Release." title.
 * The Complete Routine button appears when the last rest/break ends
 * (= the single VM timer hitting 0 on the last routine step).
 *
 * IMPORTANT: computes its own total duration (sum of every step's
 * instruction + prep + execution + rest durations) and passes it into routineViewModel.startCurrentActivityTimer().
 */
class GenericTimerActivityFragment : Fragment() {

    private var _binding: FragmentGenericTimerActivityBinding? = null
    private val binding get() = _binding!!

    private val routineViewModel: RoutineViewModel by activityViewModels()

    private var preCountdownTimer: CountDownTimer? = null
    private var frameLoopJob: Job? = null
    private var steps: List<StepperStepConfig> = emptyList()
    private var restLabel: String = "Rest."

    /** One phase of one exercise on the timeline (seconds from start). */
    private enum class Phase { INSTRUCTION, PREP, ACTION, REST }
    private data class Segment(val stepIndex: Int, val phase: Phase, val start: Int, val end: Int)

    private var segments: List<Segment> = emptyList()
    private var totalSeconds = 0
    private var stepPhaseActive = false
    private var lastSegmentIndex = -1
    private var demoBoxBackground: android.graphics.drawable.Drawable? = null

    companion object {
        private const val PRE_COUNTDOWN_SECONDS = 15L
        private const val DEFAULT_FRAME_MILLIS = 1000L

        // TEMPORARY FOR TESTING — true shortens every phase (instruction 2s,
        // prep 1s, execution 3s, rest 2s) for quick flow tests. Keep FALSE
        // for real timings.
        private const val TEST_MODE_SHORT_DURATIONS = false
        private const val TEST_INSTRUCTION_SECONDS = 2
        private const val TEST_PREP_SECONDS = 1
        private const val TEST_ACTION_SECONDS = 3
        private const val TEST_REST_SECONDS = 2

        // Drawable names without the .png extension.
        // StepperStepConfig(id, name, instruction, images,
        //                   instructionSecs, prepSecs, executionSecs, restSecs,
        //                   frameDurationsMs (instruction visual), executionCues)
        private val STEPS_BY_LABEL: Map<String, Pair<String, List<StepperStepConfig>>> = mapOf(

            // PMR — all static images.
            // Instructions 5s -> Execution 5s -> Releasing tension 10s. No prep.
            "Progressive Muscle Relaxation" to ("Release." to listOf(
                StepperStepConfig("hands", "Hands", "Make a fist with your hands as tight as possible for 5 seconds.", listOf("hands"), 5, 0, 5, 10),
                StepperStepConfig("arms", "Arms", "Flex your biceps of your arms as tight as you can for 5 seconds.", listOf("arm1"), 5, 0, 5, 10),
                StepperStepConfig("feet", "Feet", "Curl up your feet’s toes as tight as possible for 5 seconds.", listOf("toes"), 5, 0, 5, 10),
                StepperStepConfig("eyebrows", "Eyebrows", "Raise your brows for 5 seconds.", listOf("eyebrow"), 5, 0, 5, 10),
                StepperStepConfig("eyes", "Eyes", "Squeeze your eyes and make a tight smile for 5 seconds.", listOf("eye"), 5, 0, 5, 10)
            )),

            // Bedtime Stretching.
            // Instructions (per exercise) -> Preparation 3s -> Execution 60s -> Break 15s.
            "Bedtime Stretching" to ("Rest." to listOf(

                // Loop: left hold 2s -> left transition 0.2s -> right transition 0.2s
                //       -> right hold 2s -> right transition 0.2s -> left transition 0.2s
                // One loop = 4.8s, repeats through the 15s instruction screen.
                StepperStepConfig(
                    "neck_rolls", "Neck Rolls",
                    "Move your head in a slow, continuous half-circle by dropping your chin to your chest and rolling it smoothly from one shoulder to the other.",
                    listOf("neckrolllefthold", "neckrolllefttransition", "neckrollrighttransition",
                        "neckrollrighthold", "neckrollrighttransition", "neckrolllefttransition"),
                    15, 3, 60, 15,
                    listOf(2000L, 200L, 200L, 2000L, 200L, 200L)
                ),

                // Loop: up -> back -> down, 0.5s each (1.5s per roll).
                StepperStepConfig(
                    "shoulder_rolls", "Shoulder Rolls",
                    "Move your shoulders in a slow, continuous circle by lifting them up toward your ears, rolling them backward, and dropping them down in a smooth motion.",
                    listOf("shoulderollup", "shoulderollback", "shoulderolldown"),
                    15, 3, 60, 15,
                    listOf(500L, 500L, 500L)
                ),

                // Static.
                StepperStepConfig(
                    "overhead_arm_reach", "Overhead Arm Reach",
                    "Interlock your fingers with your palms facing up, then push your hands straight toward the ceiling while reaching as high as you can.",
                    listOf("overheadarmreach"),
                    15, 3, 60, 15
                ),

                // FLAG: the R13 spec has NO instruction text for this exercise.
                // Keeping the previous text as a placeholder until the team
                // provides the official one.
                // Instruction visual: left 2s -> hold 1s -> right 2s (= 5s instruction).
                // Execution text:    Hold Left 25s -> Rest 10s -> Hold Right 25s (= 60s).
                StepperStepConfig(
                    "side_neck_stretch", "Side Neck Stretch",
                    "Gently tilt your head to one side, bringing your ear toward your shoulder, and hold before slowly returning to center and repeating on the other side.",
                    listOf("sideneckstretchleft", "sideneckstretchhold", "sideneckstretchright"),
                    5, 3, 60, 15,
                    listOf(2_000L, 1_000L, 2_000L),
                    listOf(25 to "Hold Left", 10 to "Rest", 25 to "Hold Right")
                ),

                // Instruction visual: left 7s -> right 8s (= 15s instruction).
                StepperStepConfig(
                    "seated_side_stretch", "Seated Side Stretch",
                    "Sit down, reach one arm straight up, and lean your upper body to the opposite side until you feel a stretch along your ribs",
                    listOf("seatedsidestretchleft", "seatedsidestretchright"),
                    15, 3, 60, 15,
                    listOf(7_000L, 8_000L)
                )
            ))
        )
    }

    private fun effectiveInstructionSeconds(step: StepperStepConfig): Int =
        if (TEST_MODE_SHORT_DURATIONS) TEST_INSTRUCTION_SECONDS else step.instructionDurationSeconds

    private fun effectivePrepSeconds(step: StepperStepConfig): Int =
        if (step.prepDurationSeconds == 0) 0
        else if (TEST_MODE_SHORT_DURATIONS) TEST_PREP_SECONDS else step.prepDurationSeconds

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
        demoBoxBackground = binding.imgStepDemo.background // grey box; hidden during REST

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
                        if (stepPhaseActive) renderTick(secs)
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
                stopFrameLoop()
                findNavController().navigate(R.id.timesUpTransitionFragment)
            }
            is RoutineViewModel.NavigationEvent.GoToCompletion -> {
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

        var cursor = 0
        val built = mutableListOf<Segment>()
        fun add(i: Int, phase: Phase, secs: Int) {
            if (secs <= 0) return
            built += Segment(i, phase, cursor, cursor + secs); cursor += secs
        }
        steps.forEachIndexed { i, step ->
            add(i, Phase.INSTRUCTION, effectiveInstructionSeconds(step))
            add(i, Phase.PREP, effectivePrepSeconds(step))
            add(i, Phase.ACTION, effectiveActionSeconds(step))
            add(i, Phase.REST, effectiveRestSeconds(step))
        }
        segments = built
        totalSeconds = cursor
        lastSegmentIndex = -1
        stepPhaseActive = true

        renderTick(totalSeconds) // paint the first frame before the VM's first tick
        routineViewModel.startCurrentActivityTimer(totalSeconds)
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

    /**
     * Single render path: everything on screen is a function of the VM's
     * remaining seconds. Phase-change work (title, dots, images) runs once
     * per segment; the countdown text runs every tick.
     */
    private fun renderTick(remaining: Int) {
        if (_binding == null || segments.isEmpty()) return
        val elapsed = (totalSeconds - remaining).coerceIn(0, totalSeconds)
        val lookup = minOf(elapsed, totalSeconds - 1)
        val segIndex = segments.indexOfFirst { lookup >= it.start && lookup < it.end }
        if (segIndex < 0) return
        val seg = segments[segIndex]
        val step = steps[seg.stepIndex]

        if (segIndex != lastSegmentIndex) {
            lastSegmentIndex = segIndex
            renderStepDots(seg.stepIndex)
            // Defaults for every phase; REST / ACTION override below.
            binding.tvStepTitle.setTextColor(android.graphics.Color.parseColor("#2C1769")) // layout default
            binding.tvExecuteBadge.visibility = View.GONE
            binding.imgStepDemo.background = demoBoxBackground
            when (seg.phase) {
                Phase.INSTRUCTION -> {
                    // The only phase that shows the exercise visual.
                    binding.tvStepTitle.text = step.name
                    binding.tvStepInstruction.text = step.instruction
                    binding.tvStepInstruction.visibility = View.VISIBLE
                    binding.imgStepDemo.visibility = View.VISIBLE
                    startFrameLoop(step.imageAssets, step.frameDurationsMs)
                }
                Phase.PREP -> {
                    binding.tvStepTitle.text = "Get ready."
                    binding.tvStepInstruction.text = step.name
                    binding.tvStepInstruction.visibility = View.VISIBLE
                    stopFrameLoop()
                    binding.imgStepDemo.visibility = View.INVISIBLE // no visual; keeps layout from jumping
                }
                Phase.ACTION -> {
                    binding.tvStepTitle.text = step.name
                    binding.tvStepInstruction.text = step.instruction
                    binding.tvStepInstruction.visibility = View.VISIBLE
                    stopFrameLoop()
                    binding.imgStepDemo.visibility = View.INVISIBLE // no visual; keeps layout from jumping
                    binding.tvExecuteBadge.visibility = View.VISIBLE
                }
                Phase.REST -> {
                    binding.tvStepTitle.text = restLabel
                    binding.tvStepTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.timer_green))
                    binding.tvStepInstruction.text = ""
                    binding.tvStepInstruction.visibility = View.INVISIBLE // keeps layout from jumping
                    stopFrameLoop()
                    binding.imgStepDemo.visibility = View.VISIBLE
                    binding.imgStepDemo.background = null // symbol only, no grey box
                    showRestIcon()
                }
            }
        }

        if (seg.phase == Phase.ACTION && step.executionCues.isNotEmpty()) {
            binding.tvStepTitle.text = cueAt(step.executionCues, lookup - seg.start) ?: step.name
        }

        val segLeft = if (elapsed >= totalSeconds) 0 else seg.end - elapsed
        updateStepTimer(segLeft.toLong())
        // Green only while the user is actually doing the exercise.
        val colorRes = if (seg.phase == Phase.ACTION) R.color.timer_green else R.color.timer_default
        binding.tvStepTimer.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    /** Text cue for `offsetSecs` into EXECUTION; null if past the last cue. */
    private fun cueAt(cues: List<Pair<Int, String>>, offsetSecs: Int): String? {
        var cursor = 0
        for ((secs, text) in cues) {
            cursor += secs
            if (offsetSecs < cursor) return text
        }
        return null
    }

    private fun updateStepTimer(seconds: Long) {
        if (_binding == null) return
        val mins = seconds / 60
        val secs = seconds % 60
        binding.tvStepTimer.text = String.format("%02d : %02d", mins, secs)
    }

    /**
     * Plays `frames` in order during the INSTRUCTION phase, each for its own
     * time from `durationsMs` (same order). Missing/empty durations = 1s each.
     * 1 frame = static. 2+ frames = loop until the instruction phase ends
     * (stopFrameLoop() is called when PREP / EXECUTION starts).
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
        stepPhaseActive = false
        stopFrameLoop()
        _binding = null
    }
}