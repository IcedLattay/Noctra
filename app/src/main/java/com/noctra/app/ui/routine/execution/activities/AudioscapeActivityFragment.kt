package com.noctra.app.ui.routine.execution.activities

import android.animation.ValueAnimator
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.noctra.app.R
import com.noctra.app.databinding.FragmentAudioscapeActivityBinding

/**
 * AudioscapeActivityFragment
 *
 * Plays locally-bundled white/pink noise audio with an animated waveform.
 * Per spec: NO pause button — audio plays from entry until timer ends.
 * Audio files live in res/raw/. Currently uses a placeholder (silence)
 * until real audio assets are added.
 *
 * Required arguments:
 *   - ARG_DURATION_SECONDS: Int
 *   - ARG_STEP_NUMBER: Int
 *   - ARG_TOTAL_STEPS: Int
 *   - ARG_AUDIO_RES_ID: Int (optional — defaults to placeholder silence)
 *
 * When the timer ends, notifies the parent via ActivityCompletionListener.
 */
class AudioscapeActivityFragment : Fragment() {

    private var _binding: FragmentAudioscapeActivityBinding? = null
    private val binding get() = _binding!!

    private var mediaPlayer: MediaPlayer? = null
    private var countDownTimer: CountDownTimer? = null
    private var remainingSeconds: Long = 0

    // Waveform bar animators — we animate 5 bars independently
    private val barAnimators = mutableListOf<ValueAnimator>()

    companion object {
        const val ARG_DURATION_SECONDS = "arg_duration_seconds"
        const val ARG_STEP_NUMBER = "arg_step_number"
        const val ARG_TOTAL_STEPS = "arg_total_steps"
        const val ARG_AUDIO_RES_ID = "arg_audio_res_id"

        fun newInstance(
            durationSeconds: Int,
            stepNumber: Int,
            totalSteps: Int,
            audioResId: Int = 0  // 0 = use placeholder silence
        ): AudioscapeActivityFragment {
            return AudioscapeActivityFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_DURATION_SECONDS, durationSeconds)
                    putInt(ARG_STEP_NUMBER, stepNumber)
                    putInt(ARG_TOTAL_STEPS, totalSteps)
                    putInt(ARG_AUDIO_RES_ID, audioResId)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAudioscapeActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val durationSeconds = arguments?.getInt(ARG_DURATION_SECONDS, 600) ?: 600
        val stepNumber = arguments?.getInt(ARG_STEP_NUMBER, 1) ?: 1
        val totalSteps = arguments?.getInt(ARG_TOTAL_STEPS, 3) ?: 3
        val audioResId = arguments?.getInt(ARG_AUDIO_RES_ID, 0) ?: 0

        remainingSeconds = durationSeconds.toLong()

        setupStaticContent(stepNumber, totalSteps, durationSeconds)
        updateTimerDisplay(remainingSeconds)
        startAudio(audioResId)
        startWaveformAnimation()
        startTimer()
    }

    private fun setupStaticContent(stepNumber: Int, totalSteps: Int, durationSeconds: Int) {
        binding.tvStepCounter.text = "Step $stepNumber of $totalSteps"
        binding.tvActivityTitle.text = "White / Pink Noise"
        binding.tvActivitySubtitle.text = "Let the sound carry you into rest"
        val minutes = durationSeconds / 60
        binding.tvDurationLabel.text = "${minutes} min"
        binding.tvNowPlaying.text = "Now playing"
    }

    // ─── Audio ──────────────────────────────────────────────────────────────

    private fun startAudio(audioResId: Int) {
        try {
            val resId = if (audioResId != 0) audioResId else getPlaceholderAudioResId()
            if (resId == 0) {
                // No audio asset at all — waveform still animates, just silent
                return
            }
            mediaPlayer = MediaPlayer.create(requireContext(), resId)?.apply {
                isLooping = true
                setVolume(0.85f, 0.85f)
                start()
            }
        } catch (e: Exception) {
            // Graceful degradation: animation still runs without audio
            e.printStackTrace()
        }
    }

    /**
     * Returns the res/raw resource ID for the placeholder silence file.
     * Replace this logic once real audio assets exist — just swap in the
     * real R.raw.white_noise or R.raw.pink_noise resource IDs.
     *
     * TODO: Replace with actual audio files:
     *   - R.raw.white_noise
     *   - R.raw.pink_noise
     *   - R.raw.rain_tin_roof  (localized audioscape)
     *   - R.raw.lofi_fan_ambience
     */
    private fun getPlaceholderAudioResId(): Int {
        // Return 0 until real assets are added
        // Once you have the file, change this to: return R.raw.white_noise
        return 0
    }

    // ─── Waveform animation ──────────────────────────────────────────────────

    /**
     * Animates 5 waveform bars with staggered durations to simulate
     * a live audio waveform. Each bar oscillates between a min and max
     * scale-Y value independently.
     */
    private fun startWaveformAnimation() {
        val bars = listOf(
            binding.waveBar1,
            binding.waveBar2,
            binding.waveBar3,
            binding.waveBar4,
            binding.waveBar5
        )

        // Staggered durations so bars feel organic, not synchronized
        val durations = listOf(600L, 800L, 500L, 750L, 650L)
        val minScales = listOf(0.2f, 0.3f, 0.15f, 0.25f, 0.2f)
        val maxScales = listOf(1.0f, 0.85f, 1.0f, 0.9f, 0.95f)

        bars.forEachIndexed { index, bar ->
            val animator = ValueAnimator.ofFloat(minScales[index], maxScales[index]).apply {
                duration = durations[index]
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
                startDelay = (index * 80L)  // stagger start
                addUpdateListener { bar.scaleY = it.animatedValue as Float }
            }
            animator.start()
            barAnimators.add(animator)
        }
    }

    private fun stopWaveformAnimation() {
        barAnimators.forEach { it.cancel() }
        barAnimators.clear()
        // Reset all bars to neutral
        listOf(
            binding.waveBar1, binding.waveBar2, binding.waveBar3,
            binding.waveBar4, binding.waveBar5
        ).forEach { it.scaleY = 0.1f }
    }

    // ─── Timer ───────────────────────────────────────────────────────────────

    private fun startTimer() {
        countDownTimer = object : CountDownTimer(remainingSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = millisUntilFinished / 1000L
                updateTimerDisplay(remainingSeconds)
                updateTimerColor(remainingSeconds)
            }

            override fun onFinish() {
                remainingSeconds = 0
                updateTimerDisplay(0)
                stopWaveformAnimation()
                stopAudio()
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
            else -> R.color.timer_default
        }
        binding.tvTimer.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    // ─── Completion ──────────────────────────────────────────────────────────

    private fun onActivityComplete() {
        val parent = parentFragment
        if (parent is GratitudeJournalingActivityFragment.ActivityCompletionListener) {
            parent.onActivityComplete()
        }
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    private fun stopAudio() {
        try {
            mediaPlayer?.run {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        countDownTimer = null
        stopWaveformAnimation()
        stopAudio()
        _binding = null
    }
}