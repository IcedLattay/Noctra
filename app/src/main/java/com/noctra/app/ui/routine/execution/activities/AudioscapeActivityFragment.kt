package com.noctra.app.ui.routine.execution.activities

import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.noctra.app.R
import com.noctra.app.databinding.FragmentAudioscapeActivityBinding

/**
 * AudioscapeActivityFragment — Low-Stimulus Audio Listening
 *
 * Routed from RoutineExecutionFragment when activity.label == "White/Pink Noise"
 * (display title is "Low-Stimulus Audio Listening" per wireframe, but routing
 * key in activity_library is "White/Pink Noise" per handoff doc).
 *
 * States:
 *   ACTIVE  — Shleepy mascot + title + instruction text + countdown (MM : SS)
 *   TIMESUP — alarm clock, "Time's Up!", completion message. Does NOT auto-advance.
 *
 * No pause button. No waveform. Audio loops until timer ends.
 * Audio goes in res/raw/. Silent placeholder until real assets are added.
 * Notifies parent via ActivityCompletionListener (ActivityCompletionListener.kt).
 *
 * TODO: Replace getPlaceholderAudioResId() return with R.raw.white_noise when ready.
 */
class AudioscapeActivityFragment : Fragment() {

    private var _binding: FragmentAudioscapeActivityBinding? = null
    private val binding get() = _binding!!

    private var mediaPlayer: MediaPlayer? = null
    private var countDownTimer: CountDownTimer? = null
    private var remainingSeconds: Long = 0

    companion object {
        const val ARG_DURATION_SECONDS = "arg_duration_seconds"
        const val ARG_AUDIO_RES_ID    = "arg_audio_res_id"

        fun newInstance(
            durationSeconds: Int,
            audioResId: Int = 0   // 0 = silent placeholder until res/raw/ assets exist
        ): AudioscapeActivityFragment {
            return AudioscapeActivityFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_DURATION_SECONDS, durationSeconds)
                    putInt(ARG_AUDIO_RES_ID, audioResId)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAudioscapeActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val durationSeconds = arguments?.getInt(ARG_DURATION_SECONDS, 600) ?: 600
        val audioResId      = arguments?.getInt(ARG_AUDIO_RES_ID, 0) ?: 0
        remainingSeconds    = durationSeconds.toLong()

        showActiveState()
        updateTimerDisplay(remainingSeconds)
        startAudio(audioResId)
        startTimer()
    }

    // ── States ───────────────────────────────────────────────────────────────

    private fun showActiveState() {
        binding.ivShleepyLogo.visibility       = View.VISIBLE
        binding.ivShleepyBody.visibility       = View.VISIBLE
        binding.ivAlarmClock.visibility        = View.GONE
        binding.tvCompletionMessage.visibility = View.GONE
        binding.tvTimesUp.visibility           = View.GONE
        binding.tvTimer.visibility             = View.VISIBLE
    }

    private fun showTimesUpState() {
        stopAudio()
        binding.ivShleepyLogo.visibility       = View.GONE
        binding.ivShleepyBody.visibility       = View.GONE
        binding.ivAlarmClock.visibility        = View.VISIBLE
        binding.tvTimer.visibility             = View.GONE
        binding.tvTimesUp.visibility           = View.VISIBLE
        binding.tvCompletionMessage.visibility = View.VISIBLE
        binding.tvCompletionMessage.text =
            "You don't have to worry about\nmissing a task tomorrow. Great job!"

        // Notify RoutineExecutionFragment — it decides when to advance
        (parentFragment as? ActivityCompletionListener)?.onActivityComplete()
    }

    // ── Audio ────────────────────────────────────────────────────────────────

    private fun startAudio(audioResId: Int) {
        val resId = if (audioResId != 0) audioResId else return  // silent until asset added
        try {
            mediaPlayer = MediaPlayer.create(requireContext(), resId)?.apply {
                isLooping = true
                setVolume(0.85f, 0.85f)
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAudio() {
        try {
            mediaPlayer?.run { if (isPlaying) stop(); release() }
        } catch (e: Exception) { e.printStackTrace() }
        mediaPlayer = null
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
            seconds <= 10 -> R.color.timer_green
            else          -> R.color.timer_default
        }
        binding.tvTimer.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        countDownTimer = null
        stopAudio()
        _binding = null
    }
}