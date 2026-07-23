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
 * Serves: Bedtime To-Do List Writing, Reading, White/Pink Noise, Warm Shower.
 *
 * FIXED: audio filename was previously a single hardcoded "white_noise"
 * string used for every activity that reaches this fragment — meaning Warm
 * Shower would incorrectly try to play White/Pink Noise's audio too, and
 * there was no way to add Warm Shower's own file. Now looked up per-label.
 *
 * FIXED: updateMainTimerColor() had an erroneous green band between the red
 * threshold and the default color (previously `seconds <= 10 -> green`),
 * which doesn't match the wireframe (main timer is only ever default color
 * or red, never green — green is reserved for the prep countdown only).
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
        // manually verified, to restore real per-activity durations from
        // the DB (15min Reading, 10min White/Pink Noise, 5min Bedtime To-Do
        // List, 10min Warm Shower). Same pattern as GenericTimerActivityFragment.
        private const val TEST_MODE_SHORT_DURATION = true
        private const val TEST_DURATION_SECONDS = 15

        // Not part of the DB schema — audio filename per activity label.
        // File must exist at res/raw/<name>.mp3 (or other supported format).
        // Update these as real assets are added.
        private val AUDIO_RES_BY_LABEL = mapOf(
            "White/Pink Noise" to "whitenoiseaudio",
            "Warm Shower" to "warm_shower" // TODO: still a placeholder filename, confirm/replace with real asset
            // Reading, Bedtime To-Do List Writing intentionally absent — no audio.
        )
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
     * FIXED: title/instruction were previously left as the hardcoded
     * "White/Pink Noise" text baked into the XML. That was fine when this
     * fragment only served White/Pink Noise, but it now serves 4 activities
     * (Reading, White/Pink Noise, Bedtime To-Do List Writing, Warm Shower) —
     * so Reading and Bedtime To-Do List were showing White/Pink Noise's
     * title and instruction. Now DB-driven, same pattern as
     * GenericTimerActivityFragment.setupStaticUI().
     */
    private fun setupStaticUI() {
        val activity = routineViewModel.currentActivity ?: return
        binding.tvPreTitle.text = activity.label
        binding.tvPreInstruction.text = activity.instruction
        binding.tvAudioLabel.text = activity.label
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
    }

    override fun onResume() {
        super.onResume()
        mediaPlayer?.let { if (!it.isPlaying) it.start() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        preCountdownTimer?.cancel()
        preCountdownTimer = null
        stopAudio()
        _binding = null
    }
}