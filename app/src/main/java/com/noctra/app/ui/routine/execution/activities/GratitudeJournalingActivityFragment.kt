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
 * Routed from RoutineExecutionFragment when activity.label == "Gratitude Journaling"
 * (routing is by label per handoff doc — activity_type alone is too coarse).
 *
 * States:
 *   ACTIVE  — Shleepy mascot + title + text input + countdown (MM : SS)
 *   TIMESUP — alarm clock replaces mascot, "Time's Up!" shown, input hidden,
 *             completion message shown. Does NOT auto-advance.
 *
 * Text entered is NEVER saved — cleared on Time's Up. No DB write here.
 * Notifies parent via ActivityCompletionListener (ActivityCompletionListener.kt).
 */
class GratitudeJournalingActivityFragment : Fragment() {

    private var _binding: FragmentGratitudeJournalingActivityBinding? = null
    private val binding get() = _binding!!

    private var countDownTimer: CountDownTimer? = null
    private var remainingSeconds: Long = 0

    companion object {
        const val ARG_DURATION_SECONDS = "arg_duration_seconds"

        fun newInstance(durationSeconds: Int): GratitudeJournalingActivityFragment {
            return GratitudeJournalingActivityFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_DURATION_SECONDS, durationSeconds)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGratitudeJournalingActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val durationSeconds = arguments?.getInt(ARG_DURATION_SECONDS, 600) ?: 600
        remainingSeconds = durationSeconds.toLong()
        showActiveState()
        updateTimerDisplay(remainingSeconds)
        startTimer()
    }

    // ── States ───────────────────────────────────────────────────────────────

    private fun showActiveState() {
        binding.ivShleepyLogo.visibility       = View.VISIBLE
        binding.ivShleepyBody.visibility       = View.VISIBLE
        binding.ivAlarmClock.visibility        = View.GONE
        binding.tvTitle.text                   = "Gratitude Journaling"
        binding.tvSubtitle.visibility          = View.VISIBLE
        binding.cardInput.visibility           = View.VISIBLE
        binding.tvCompletionMessage.visibility = View.GONE
        binding.tvTimesUp.visibility           = View.GONE
        binding.tvTimer.visibility             = View.VISIBLE
        binding.cardTimer.visibility           = View.VISIBLE
    }

    private fun showTimesUpState() {
        dismissKeyboard()
        binding.etJournalEntry.setText("")  // never saved — cleared immediately

        binding.ivShleepyLogo.visibility       = View.GONE
        binding.ivShleepyBody.visibility       = View.GONE
        binding.ivAlarmClock.visibility        = View.VISIBLE
        binding.cardInput.visibility           = View.GONE
        binding.tvSubtitle.visibility          = View.GONE
        binding.tvTimer.visibility             = View.GONE
        binding.tvTimesUp.visibility           = View.VISIBLE
        binding.tvCompletionMessage.visibility = View.VISIBLE
        binding.tvCompletionMessage.text =
            "Great job.\nSuppressing your thoughts is bad for your health.\nThink of yourself for a second."

        // Notify RoutineExecutionFragment — it decides when to advance
        (parentFragment as? ActivityCompletionListener)?.onActivityComplete()
    }

    // ── Timer ────────────────────────────────────────────────────────────────

    private fun startTimer() {
        countDownTimer = object : CountDownTimer(remainingSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = millisUntilFinished / 1000L
                updateTimerDisplay(remainingSeconds)
                updateTimerColor(remainingSeconds)
            }
            override fun onFinish() {
                remainingSeconds = 0
                showTimesUpState()
            }
        }.start()
    }

    /** MM : SS — always two digits each side, matches wireframe format exactly */
    private fun updateTimerDisplay(seconds: Long) {
        val mins = seconds / 60
        val secs = seconds % 60
        binding.tvTimer.text = String.format("%02d : %02d", mins, secs)
    }

    private fun updateTimerColor(seconds: Long) {
        val colorRes = when {
            seconds <= 5  -> R.color.timer_red
            seconds <= 15 -> R.color.timer_green
            else          -> R.color.timer_default
        }
        binding.tvTimer.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    private fun dismissKeyboard() {
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etJournalEntry.windowToken, 0)
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        countDownTimer = null
        _binding = null
    }
}