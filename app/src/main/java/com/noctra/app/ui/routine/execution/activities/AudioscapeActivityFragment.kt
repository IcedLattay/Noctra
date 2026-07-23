package com.noctra.app.ui.routine.execution.activities

import android.media.MediaPlayer
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
import com.noctra.app.databinding.FragmentAudioscapeActivityBinding
import com.noctra.app.ui.routine.RoutineViewModel
import kotlinx.coroutines.launch

/**
 * AudioscapeActivityFragment
 *
 * Serves six activities across three visual variants, all sharing the same
 * 15s prep countdown -> main timer -> transition skeleton:
 *
 *   1. WAVEFORM variant — White/Pink Noise, Low-Stimulus Audio Listening.
 *      Audio plays on entry, animated waveform under the instruction card.
 *      Matches the SDD's AudioActivityScreen, which explicitly covers
 *      "low-stimulus audio and white/pink noise activities" together.
 *
 *   2. NATURE variant — Mindfulness only. Nature scenery image replaces the
 *      Shleepy illustration, ambient nature audio plays, NO waveform.
 *      Matches the SDD's MindfulnessActivityScreen ("nature-themed ambient
 *      visual with bundled meditation audio").
 *
 *   3. PLAIN variant — Reading, Bedtime To-Do List Writing, Warm Shower.
 *      Shleepy illustration, no audio, no waveform, just the timer.
 *      (Warm Shower has an audio entry below but no waveform, since its
 *      wireframe shows ambient shower audio without a player visualization.)
 */
class AudioscapeActivityFragment : Fragment() {

    private var _binding: FragmentAudioscapeActivityBinding? = null
    private val binding get() = _binding!!

    private val routineViewModel: RoutineViewModel by activityViewModels()

    private var mediaPlayer: MediaPlayer? = null
    private var preCountdownTimer: CountDownTimer? = null

    companion object {
        private const val PRE_COUNTDOWN_SECONDS = 15L

        // TEMPORARY FOR TESTING — set to false once all activities are
        // manually verified, to restore real per-activity durations from the DB.
        private const val TEST_MODE_SHORT_DURATION = true
        private const val TEST_DURATION_SECONDS = 15

        private const val LABEL_MINDFULNESS = "Mindfulness"
        private const val LABEL_WHITE_PINK_NOISE = "White/Pink Noise"

        // Must match the `label` column in Supabase exactly once this row is
        // seeded — it does NOT exist in the DB yet, so this activity can't
        // appear in a routine until it's added.
        private const val LABEL_LOW_STIMULUS = "Low-Stimulus Audio Listening"

        // Not part of the DB schema — audio filename per activity label.
        // Each must exist at res/raw/<name>.mp3 (or another supported format).
        private val AUDIO_RES_BY_LABEL = mapOf(
            LABEL_WHITE_PINK_NOISE to "whitenoiseaudio",
            // TODO: add the real meditation audio file to res/raw/ and confirm this name.
            LABEL_LOW_STIMULUS to "meditationaudio",
            // TODO: add the real nature/ambient audio file to res/raw/ and confirm this name.
            LABEL_MINDFULNESS to "natureaudio",
            // TODO: still a placeholder filename, confirm/replace with real asset.
            "Warm Shower" to "warm_shower"
            // Reading, Bedtime To-Do List Writing intentionally absent — no audio.
        )

        // Only these two get the animated waveform, per the SDD's
        // AudioActivityScreen. Mindfulness deliberately excluded — it gets the
        // nature visual instead.
        private val WAVEFORM_LABELS = setOf(LABEL_WHITE_PINK_NOISE, LABEL_LOW_STIMULUS)

        // TODO: replace with the real nature scenery drawable once added to
        // res/drawable/. Falls back to hiding the image if not found.
        private const val NATURE_SCENERY_DRAWABLE = "bg_nature_scenery"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAudioscapeActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupStaticUI()
        showPreCountdownPanel()
        setupListeners()
        observeVm()
        startPreCountdown()
    }

    /**
     * Title/instruction are DB-driven, and the visual variant is chosen by
     * label. Without this, all six activities would show the hardcoded
     * White/Pink Noise copy baked into the XML.
     */
    private fun setupStaticUI() {
        val activity = routineViewModel.currentActivity ?: return
        val label = activity.label

        binding.tvPreTitle.text = label
        binding.tvPreInstruction.text = activity.instruction
        binding.tvAudioLabel.text = label
        binding.tvActiveInstruction.text = activity.instruction

        val isMindfulness = label == LABEL_MINDFULNESS
        val showWaveform = WAVEFORM_LABELS.contains(label)

        // Nature scenery replaces the Shleepy illustration for Mindfulness.
        if (isMindfulness) {
            val resId = resources.getIdentifier(
                NATURE_SCENERY_DRAWABLE, "drawable", requireContext().packageName
            )
            if (resId != 0) {
                binding.imgNatureScenery.setImageResource(resId)
                binding.imgNatureScenery.visibility = View.VISIBLE
                binding.imgShleepyBodyActive.visibility = View.GONE
            } else {
                // Asset not added yet — fall back to Shleepy rather than
                // showing an empty box.
                binding.imgNatureScenery.visibility = View.GONE
                binding.imgShleepyBodyActive.visibility = View.VISIBLE
            }
        } else {
            binding.imgNatureScenery.visibility = View.GONE
            binding.imgShleepyBodyActive.visibility = View.VISIBLE
        }

        binding.waveformView.visibility = if (showWaveform) View.VISIBLE else View.GONE
    }

    private fun setupListeners() {
        binding.btnCompleteRoutine.setOnClickListener {
            routineViewModel.onCompleteRoutineTapped()
        }
    }

    private fun showPreCountdownPanel() {
        binding.preCountdownPanel.visibility = View.VISIBLE
        binding.audioPanel.visibility = View.GONE
        updatePreTimer(PRE_COUNTDOWN_SECONDS)
    }

    private fun showAudioPanel() {
        binding.preCountdownPanel.visibility = View.GONE
        binding.audioPanel.visibility = View.VISIBLE
        startAudio()
        if (binding.waveformView.visibility == View.VISIBLE) {
            binding.waveformView.start()
        }
        val durationSeconds = if (TEST_MODE_SHORT_DURATION) TEST_DURATION_SECONDS
        else (routineViewModel.currentActivity?.defaultDurationMinutes ?: 0) * 60
        routineViewModel.startCurrentActivityTimer(durationSeconds)
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
                showAudioPanel()
            }
        }.start()
    }

    private fun updatePreTimer(seconds: Long) {
        val mins = seconds / 60
        val secs = seconds % 60
        binding.tvPreTimer.text = String.format("%02d : %02d", mins, secs)
    }

    private fun observeVm() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    routineViewModel.activitySecondsRemaining.collect { secs ->
                        updateMainTimerDisplay(secs.toLong())
                        updateMainTimerColor(secs.toLong())

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
                stopAudio()
                findNavController().navigate(R.id.timesUpTransitionFragment)
            }
            is RoutineViewModel.NavigationEvent.GoToCompletion -> {
                stopAudio()
                findNavController().navigate(R.id.routineCompletionOverlayFragment)
            }
            else -> {}
        }
    }

    private fun startAudio() {
        val label = routineViewModel.currentActivity?.label ?: return
        val name = AUDIO_RES_BY_LABEL[label] ?: return
        val resId = resources.getIdentifier(name, "raw", requireContext().packageName)
        if (resId == 0) return
        try {
            mediaPlayer = MediaPlayer.create(requireContext(), resId)?.apply {
                isLooping = true
                setVolume(0.85f, 0.85f)
                start()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun stopAudio() {
        try { mediaPlayer?.run { if (isPlaying) stop(); release() } } catch (e: Exception) {}
        mediaPlayer = null
        _binding?.waveformView?.stop()
    }

    private fun updateMainTimerDisplay(seconds: Long) {
        if (_binding == null) return
        val mins = seconds / 60
        val secs = seconds % 60
        binding.tvMainTimer.text = String.format("%02d : %02d", mins, secs)
    }

    private fun updateMainTimerColor(seconds: Long) {
        if (_binding == null) return
        val colorRes = if (seconds <= 5) R.color.timer_red else R.color.timer_default
        binding.tvMainTimer.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.let { if (it.isPlaying) it.pause() }
        _binding?.waveformView?.stop()
    }

    override fun onResume() {
        super.onResume()
        if (binding.audioPanel.visibility == View.VISIBLE) {
            mediaPlayer?.let { if (!it.isPlaying) it.start() }
            if (binding.waveformView.visibility == View.VISIBLE) {
                binding.waveformView.start()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        preCountdownTimer?.cancel()
        preCountdownTimer = null
        stopAudio()
        _binding = null
    }
}