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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AudioscapeActivityFragment
 *
 * Serves six activities across three visual variants, all sharing the same
 * 15s prep countdown -> main timer -> transition skeleton:
 *
 *   1. WAVEFORM variant — White/Pink Noise, Low-Stimulus Audio Listening.
 *      Audio plays on entry, animated waveform under the instruction card.
 *
 *   2. NATURE variant — Mindfulness only. A slideshow of nature scenery
 *      images crossfades every 10 seconds while ambient audio plays.
 *      NO waveform.
 *
 *   3. PLAIN variant — Reading, Bedtime To-Do List Writing, Warm Shower.
 *      Shleepy illustration, no waveform.
 */
class AudioscapeActivityFragment : Fragment() {

    private var _binding: FragmentAudioscapeActivityBinding? = null
    private val binding get() = _binding!!

    private val routineViewModel: RoutineViewModel by activityViewModels()

    private var mediaPlayer: MediaPlayer? = null
    private var preCountdownTimer: CountDownTimer? = null

    /** Resolved drawable IDs for the nature slideshow; empty for non-Mindfulness. */
    private var natureSceneryResIds: List<Int> = emptyList()
    private var slideshowJob: Job? = null

    companion object {
        private const val PRE_COUNTDOWN_SECONDS = 15L

        // TEMPORARY FOR TESTING — set to false once all activities are
        // manually verified, to restore real per-activity durations from the DB.
        private const val TEST_MODE_SHORT_DURATION = true
        private const val TEST_DURATION_SECONDS = 15

        private const val LABEL_MINDFULNESS = "Mindfulness"
        private const val LABEL_WHITE_PINK_NOISE = "White/Pink Noise"

        // Must match the `label` column in Supabase exactly once this row is
        // seeded — it does NOT exist in the DB yet.
        private const val LABEL_LOW_STIMULUS = "Low-Stimulus Audio Listening"

        // Audio filename per activity label. Each must exist in res/raw/.
        private val AUDIO_RES_BY_LABEL = mapOf(
            LABEL_WHITE_PINK_NOISE to "whitenoiseaudio",
            LABEL_LOW_STIMULUS to "meditationaudio",
            LABEL_MINDFULNESS to "natureaudio",
            "Warm Shower" to "warm_shower"
            // Reading, Bedtime To-Do List Writing intentionally absent — no audio.
        )

        // Only these two get the animated waveform. Mindfulness deliberately
        // excluded — it gets the nature slideshow instead.
        private val WAVEFORM_LABELS = setOf(LABEL_WHITE_PINK_NOISE, LABEL_LOW_STIMULUS)

        // Nature slideshow: expects bg_nature_scenery_1 .. bg_nature_scenery_8
        // in res/drawable/ (NOT res/raw — raw is for audio only).
        // Any that are missing are silently skipped, so a partial set still works.
        private const val NATURE_SCENERY_PREFIX = "bg_nature_scenery_"
        private const val NATURE_SCENERY_COUNT = 8
        private const val SLIDESHOW_INTERVAL_MS = 10_000L
        private const val SLIDESHOW_FADE_MS = 600L
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

    private fun setupStaticUI() {
        val activity = routineViewModel.currentActivity ?: return
        val label = activity.label

        binding.tvPreTitle.text = label
        binding.tvPreInstruction.text = activity.instruction
        binding.tvAudioLabel.text = label
        binding.tvActiveInstruction.text = activity.instruction

        val isMindfulness = label == LABEL_MINDFULNESS
        val showWaveform = WAVEFORM_LABELS.contains(label)

        if (isMindfulness) {
            natureSceneryResIds = resolveNatureSceneryResIds()
            if (natureSceneryResIds.isNotEmpty()) {
                binding.imgNatureScenery.setImageResource(natureSceneryResIds.first())
                binding.imgNatureScenery.visibility = View.VISIBLE
                binding.imgShleepyBodyActive.visibility = View.GONE
            } else {
                // No scenery assets present — fall back to Shleepy rather than
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

    /** Collects bg_nature_scenery_1..8, skipping any that don't exist. */
    private fun resolveNatureSceneryResIds(): List<Int> {
        val pkg = requireContext().packageName
        return (1..NATURE_SCENERY_COUNT).mapNotNull { i ->
            val id = resources.getIdentifier("$NATURE_SCENERY_PREFIX$i", "drawable", pkg)
            if (id != 0) id else null
        }
    }

    /**
     * Crossfades to the next scenery image every SLIDESHOW_INTERVAL_MS.
     * No-op unless there are at least 2 images to cycle between.
     */
    private fun startNatureSlideshow() {
        if (natureSceneryResIds.size < 2) return
        slideshowJob?.cancel()
        slideshowJob = viewLifecycleOwner.lifecycleScope.launch {
            var index = 0
            while (true) {
                delay(SLIDESHOW_INTERVAL_MS)
                if (_binding == null) return@launch
                index = (index + 1) % natureSceneryResIds.size
                crossfadeSceneryTo(natureSceneryResIds[index])
            }
        }
    }

    private fun crossfadeSceneryTo(resId: Int) {
        val image = _binding?.imgNatureScenery ?: return
        image.animate()
            .alpha(0f)
            .setDuration(SLIDESHOW_FADE_MS / 2)
            .withEndAction {
                if (_binding == null) return@withEndAction
                image.setImageResource(resId)
                image.animate()
                    .alpha(1f)
                    .setDuration(SLIDESHOW_FADE_MS / 2)
                    .start()
            }
            .start()
    }

    private fun stopNatureSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = null
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
        if (binding.imgNatureScenery.visibility == View.VISIBLE) {
            startNatureSlideshow()
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
                stopNatureSlideshow()
                findNavController().navigate(R.id.timesUpTransitionFragment)
            }
            is RoutineViewModel.NavigationEvent.GoToCompletion -> {
                stopAudio()
                stopNatureSlideshow()
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
        stopNatureSlideshow()
    }

    override fun onResume() {
        super.onResume()
        if (binding.audioPanel.visibility == View.VISIBLE) {
            mediaPlayer?.let { if (!it.isPlaying) it.start() }
            if (binding.waveformView.visibility == View.VISIBLE) {
                binding.waveformView.start()
            }
            if (binding.imgNatureScenery.visibility == View.VISIBLE) {
                startNatureSlideshow()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        preCountdownTimer?.cancel()
        preCountdownTimer = null
        stopAudio()
        stopNatureSlideshow()
        _binding = null
    }
}