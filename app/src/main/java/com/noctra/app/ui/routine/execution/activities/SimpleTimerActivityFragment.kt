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
import androidx.navigation.fragment.navArgs
import com.noctra.app.R
import com.noctra.app.databinding.FragmentSimpleTimerActivityBinding
import com.noctra.app.ui.routine.RoutineViewModel
import kotlinx.coroutines.launch

/**
 * Shape A: Simple Timer.
 * Used by Reading, White/Pink Noise, Bedtime To-Do List Writing,
 * Low-Stimulus Audio Listening, and Warm Shower.
 *
 * Flow: 15s prep countdown -> main timer (VM-driven) -> Time's Up / Completion
 * (both handled by shared fragments elsewhere in the nav graph).
 *
 * audioResName is nullable: Reading and Bedtime To-Do List Writing pass null
 * (no audio); White/Pink Noise, Low-Stimulus Audio, and Warm Shower pass
 * their res/raw filename.
 */
class SimpleTimerActivityFragment : Fragment() {

    private var _binding: FragmentSimpleTimerActivityBinding? = null
    private val binding get() = _binding!!

    private val args: SimpleTimerActivityFragmentArgs by navArgs()
    private val routineViewModel: RoutineViewModel by activityViewModels()

    private var mediaPlayer: MediaPlayer? = null
    private var preCountdownTimer: CountDownTimer? = null

    companion object {
        private const val PRE_COUNTDOWN_SECONDS = 15L
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSimpleTimerActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvPreTitle.text = args.activityTitle
        binding.tvPreInstruction.text = args.activityInstruction
        binding.tvActiveLabel.text = args.activityTitle
        binding.tvActiveInstruction.text = args.activityInstruction

        showPreCountdownPanel()
        observeVm()
        startPreCountdown()
    }

    private fun showPreCountdownPanel() {
        binding.preCountdownPanel.visibility = View.VISIBLE
        binding.activePanel.visibility = View.GONE
        updatePreTimer(PRE_COUNTDOWN_SECONDS)
    }

    private fun showActivePanel() {
        binding.preCountdownPanel.visibility = View.GONE
        binding.activePanel.visibility = View.VISIBLE
        startAudioIfNeeded()
        routineViewModel.startCurrentActivityTimer()
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
                showActivePanel()
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

    private fun startAudioIfNeeded() {
        val name = args.audioResName ?: return
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
        // Pause audio if the app is backgrounded mid-activity, so it doesn't
        // keep playing indefinitely while the user isn't looking at the screen.
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