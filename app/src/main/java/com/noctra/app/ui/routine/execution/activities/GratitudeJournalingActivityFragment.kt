package com.noctra.app.ui.routine.execution.activities

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.noctra.app.R
import com.noctra.app.databinding.FragmentGratitudeJournalingActivityBinding

/**
 * GratitudeJournalingActivityFragment
 *
 * Displays a free-text reflection prompt with a countdown timer.
 * The text entered by the user is NEVER saved — it exists only for
 * in-session reflection. This is intentional per the SDD and MVP spec.
 *
 * Required arguments:
 *   - ARG_DURATION_SECONDS: Int — total activity duration in seconds
 *   - ARG_STEP_NUMBER: Int — current step (e.g. 2)
 *   - ARG_TOTAL_STEPS: Int — total steps in routine (e.g. 3)
 *
 * Calls onActivityComplete() on the parent Fragment (RoutineExecutionFragment)
 * when the timer finishes.
 */
class GratitudeJournalingActivityFragment : Fragment() {

    private var _binding: FragmentGratitudeJournalingActivityBinding? = null
    private val binding get() = _binding!!

    private var countDownTimer: CountDownTimer? = null
    private var remainingSeconds: Long = 0
    private var isTimerRunning = false

    companion object {
        const val ARG_DURATION_SECONDS = "arg_duration_seconds"
        const val ARG_STEP_NUMBER = "arg_step_number"
        const val ARG_TOTAL_STEPS = "arg_total_steps"

        // Prompts rotate per session — picked by step index mod size
        private val JOURNAL_PROMPTS = listOf(
            "What are three things that made today feel okay — even small ones?",
            "Who helped you today, or who are you grateful to have in your life right now?",
            "What's one moment from today you'd like to carry into tomorrow?",
            "Name something your body did for you today that you usually take for granted.",
            "What's one thing you're looking forward to, no matter how small?"
        )

        fun newInstance(
            durationSeconds: Int,
            stepNumber: Int,
            totalSteps: Int
        ): GratitudeJournalingActivityFragment {
            return GratitudeJournalingActivityFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_DURATION_SECONDS, durationSeconds)
                    putInt(ARG_STEP_NUMBER, stepNumber)
                    putInt(ARG_TOTAL_STEPS, totalSteps)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGratitudeJournalingActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val durationSeconds = arguments?.getInt(ARG_DURATION_SECONDS, 300) ?: 300
        val stepNumber = arguments?.getInt(ARG_STEP_NUMBER, 1) ?: 1
        val totalSteps = arguments?.getInt(ARG_TOTAL_STEPS, 3) ?: 3

        remainingSeconds = durationSeconds.toLong()

        setupStaticContent(stepNumber, totalSteps, durationSeconds)
        updateTimerDisplay(remainingSeconds)
        startTimer()
    }

    private fun setupStaticContent(stepNumber: Int, totalSteps: Int, durationSeconds: Int) {
        // Step counter
        binding.tvStepCounter.text = "Step $stepNumber of $totalSteps"

        // Activity title
        binding.tvActivityTitle.text = "Gratitude Journal"

        // Rotate prompt based on step number so it varies across nights
        val prompt = JOURNAL_PROMPTS[(stepNumber - 1) % JOURNAL_PROMPTS.size]
        binding.tvJournalPrompt.text = prompt

        // Hint in the EditText
        binding.etJournalEntry.hint = "Write freely — this is just for you…"

        // Duration label
        val minutes = durationSeconds / 60
        binding.tvDurationLabel.text = "${minutes} min"
    }

    private fun startTimer() {
        isTimerRunning = true
        countDownTimer = object : CountDownTimer(remainingSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = millisUntilFinished / 1000L
                updateTimerDisplay(remainingSeconds)
                updateTimerColor(remainingSeconds)
            }

            override fun onFinish() {
                remainingSeconds = 0
                updateTimerDisplay(0)
                isTimerRunning = false
                dismissKeyboard()
                onActivityComplete()
            }
        }.start()
    }

    private fun updateTimerDisplay(seconds: Long) {
        val mins = seconds / 60
        val secs = seconds % 60
        binding.tvTimer.text = String.format("%d:%02d", mins, secs)
    }

    private fun updateTimerColor(seconds: Long) {
        val colorRes = when {
            seconds <= 30 -> R.color.timer_red
            seconds <= 60 -> R.color.timer_green  // reuse green as "almost done" warm
            else -> R.color.timer_default
        }
        binding.tvTimer.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    private fun dismissKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etJournalEntry.windowToken, 0)
    }

    /**
     * Notifies the parent RoutineExecutionFragment that this activity is done.
     * The parent manages navigation to the next step.
     * NOTE: journal text is intentionally discarded here — never read, never saved.
     */
    private fun onActivityComplete() {
        // Clear the field before navigating — belt-and-suspenders privacy measure
        binding.etJournalEntry.setText("")

        val parent = parentFragment
        if (parent is ActivityCompletionListener) {
            parent.onActivityComplete()
        }
    }

    override fun onPause() {
        super.onPause()
        // Timer keeps running in background (RoutineExecutionFragment keeps screen on)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        countDownTimer = null
        _binding = null
    }

    /** Implemented by RoutineExecutionFragment to receive completion callbacks. */
    interface ActivityCompletionListener {
        fun onActivityComplete()
    }
}