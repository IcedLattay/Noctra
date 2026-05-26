package com.noctra.app.ui.routine.execution.activities

// ─── Replace "com.noctra.app" with your actual applicationId if different ────

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.noctra.app.R
import com.noctra.app.data.model.Activity
import com.noctra.app.databinding.FragmentGenericTimerActivityBinding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * GenericTimerActivityFragment
 *
 * Handles all non-MVP activities that don't have custom UIs:
 * Reading, Warm Shower, Bedtime Stretching, Progressive Muscle Relaxation,
 * Mindfulness, Bedtime To-Do List
 *
 * State machine:
 *   PRE_COUNTDOWN (15s green) → RUNNING (dark) → WARNING (≤30s red)
 *   → TIMES_UP → COMPLETE → fires Fragment Result → RoutineExecutionFragment advances
 *
 * Usage — create via companion factory:
 *   val fragment = GenericTimerActivityFragment.newInstance(activity)
 *
 * Listen for completion in RoutineExecutionFragment:
 *   childFragmentManager.setFragmentResultListener(
 *       GenericTimerActivityFragment.RESULT_COMPLETE, viewLifecycleOwner
 *   ) { _, _ -> advanceToNextStep() }
 */
class GenericTimerActivityFragment : Fragment() {

    // ── ViewBinding ───────────────────────────────────────────────────────────

    private var _binding: FragmentGenericTimerActivityBinding? = null
    private val binding get() = _binding!!

    // ── State ─────────────────────────────────────────────────────────────────

    private enum class TimerState {
        PRE_COUNTDOWN, RUNNING, WARNING, TIMES_UP, COMPLETE
    }

    private var state = TimerState.PRE_COUNTDOWN

    // ── Timers ────────────────────────────────────────────────────────────────

    private var preCountdownTimer: CountDownTimer? = null
    private var mainTimer: CountDownTimer? = null

    // ── Data ──────────────────────────────────────────────────────────────────

    private lateinit var activityData: Activity

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        const val RESULT_COMPLETE = "generic_timer_complete"

        private const val ARG_ACTIVITY_JSON = "arg_activity_json"
        private const val PRE_COUNTDOWN_MS  = 15_000L
        private const val WARNING_MS        = 30_000L
        private const val TICK_MS           = 1_000L
        private const val TIMES_UP_LINGER   = 1_500L
        private const val COMPLETE_LINGER   = 2_500L

        /**
         * Factory method. Always create instances this way.
         */
        fun newInstance(activity: Activity): GenericTimerActivityFragment =
            GenericTimerActivityFragment().apply {
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
        _binding = FragmentGenericTimerActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activityData = Json.decodeFromString(
            requireArguments().getString(ARG_ACTIVITY_JSON)!!
        )
        setupStaticUI()
        startPreCountdown()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelTimers()
        _binding = null
    }

    // ── UI Setup ──────────────────────────────────────────────────────────────

    private fun setupStaticUI() {
        binding.tvActivityTitle.text    = activityData.label
        binding.tvInstruction.text      = activityData.instruction
        binding.tvCompletionMessage.text = completionMessageFor(activityData.label)

        // Resolve activity icon from iconAsset string (e.g. "ic_book")
        val iconRes = resources.getIdentifier(
            activityData.iconAsset, "drawable", requireContext().packageName
        )
        if (iconRes != 0) binding.imgActivityIcon.setImageResource(iconRes)
    }

    // ── State Machine ─────────────────────────────────────────────────────────

    private fun transitionTo(newState: TimerState) {
        state = newState
        when (newState) {
            TimerState.PRE_COUNTDOWN,
            TimerState.RUNNING,
            TimerState.WARNING  -> showRunningPanel()
            TimerState.TIMES_UP -> showTimesUp()
            TimerState.COMPLETE -> showCompletion()
        }
    }

    // ── Panel Visibility ──────────────────────────────────────────────────────

    private fun showRunningPanel() {
        binding.runningPanel.visibility      = View.VISIBLE
        binding.timesUpOverlay.visibility    = View.GONE
        binding.completionOverlay.visibility = View.GONE
    }

    private fun showTimesUp() {
        binding.runningPanel.visibility   = View.GONE
        binding.completionOverlay.visibility = View.GONE

        binding.timesUpOverlay.alpha = 0f
        binding.timesUpOverlay.visibility = View.VISIBLE
        binding.timesUpOverlay.animate().alpha(1f).setDuration(300).start()

        binding.root.postDelayed(
            { transitionTo(TimerState.COMPLETE) },
            TIMES_UP_LINGER
        )
    }

    private fun showCompletion() {
        binding.runningPanel.visibility   = View.GONE
        binding.timesUpOverlay.visibility = View.GONE

        binding.completionOverlay.alpha = 0f
        binding.completionOverlay.visibility = View.VISIBLE
        binding.completionOverlay.animate().alpha(1f).setDuration(500).start()

        pulseText(binding.tvCompletionMessage)

        binding.root.postDelayed({
            // Signal RoutineExecutionFragment to advance to next step
            setFragmentResult(RESULT_COMPLETE, bundleOf())
        }, COMPLETE_LINGER)
    }

    // ── Timer Logic ───────────────────────────────────────────────────────────

    /**
     * 15-second green pre-countdown — user gets ready before the activity starts.
     */
    private fun startPreCountdown() {
        transitionTo(TimerState.PRE_COUNTDOWN)
        updateTimerText(PRE_COUNTDOWN_MS, isPreCountdown = true)

        preCountdownTimer = object : CountDownTimer(PRE_COUNTDOWN_MS, TICK_MS) {
            override fun onTick(ms: Long) = updateTimerText(ms, isPreCountdown = true)
            override fun onFinish() {
                transitionTo(TimerState.RUNNING)
                startMainTimer()
            }
        }.start()
    }

    /**
     * Main activity countdown — duration from activity_library.default_duration_minutes.
     */
    private fun startMainTimer() {
        val durationMs = activityData.defaultDurationMinutes * 60_000L
        updateTimerText(durationMs, isPreCountdown = false)

        mainTimer = object : CountDownTimer(durationMs, TICK_MS) {
            override fun onTick(ms: Long) {
                if (ms <= WARNING_MS && state != TimerState.WARNING) {
                    transitionTo(TimerState.WARNING)
                }
                updateTimerText(ms, isPreCountdown = false)
            }

            override fun onFinish() = transitionTo(TimerState.TIMES_UP)
        }.start()
    }

    private fun cancelTimers() {
        preCountdownTimer?.cancel()
        mainTimer?.cancel()
    }

    // ── Display Helpers ───────────────────────────────────────────────────────

    /**
     * Formats ms → "MM : SS" and applies the correct color per state.
     *
     *   Pre-countdown  → timer_green
     *   Running        → timer_default (dark navy)
     *   Warning (≤30s) → timer_red
     */
    private fun updateTimerText(ms: Long, isPreCountdown: Boolean) {
        val totalSec = (ms / 1000).toInt()
        binding.tvTimer.text = String.format("%02d : %02d", totalSec / 60, totalSec % 60)

        binding.tvTimer.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                when {
                    isPreCountdown  -> R.color.timer_green
                    ms <= WARNING_MS -> R.color.timer_red
                    else             -> R.color.timer_default
                }
            )
        )
    }

    // ── Completion Message Map ────────────────────────────────────────────────

    private fun completionMessageFor(label: String): String = when (label) {
        "Reading"                        ->
            "Your eyes and mind are ready to rest.\nReading is great, keep it up!"
        "Warm Shower"                    ->
            "A warm shower helps your body cool down naturally.\nSleep tight!"
        "Bedtime Stretching"             ->
            "Your muscles are relaxed and ready for rest.\nGreat work!"
        "Progressive Muscle Relaxation"  ->
            "Your body is fully relaxed.\nTime to drift off!"
        "Mindfulness"                    ->
            "Your mind is calm and centered.\nSleep well!"
        "Bedtime To-Do List"             ->
            "Your tasks are noted.\nNow let your mind rest!"
        else                             ->
            "Great job completing this activity!\nTime to wind down."
    }

    // ── Animation ─────────────────────────────────────────────────────────────

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