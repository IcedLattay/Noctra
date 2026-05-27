package com.noctra.app.ui.routine.execution

import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.noctra.app.R
import com.noctra.app.ui.routine.RoutineViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 5-second transition between activities. Plays a chime, shows "Time's Up!"
 * and "Nice work — next up: [activity]", then notifies the VM which fires
 * GoToActivity for the next destination.
 */
class TimesUpTransitionFragment : Fragment() {

    private val routineViewModel: RoutineViewModel by activityViewModels()

    private lateinit var tvEncouragement: TextView
    private lateinit var tvCountdown: TextView

    private var chimePlayer: MediaPlayer? = null
    private var countdownJob: Job? = null

    companion object { private const val COUNTDOWN_SECONDS = 5 }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_times_up_transition, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvEncouragement = view.findViewById(R.id.tv_encouragement)
        tvCountdown = view.findViewById(R.id.tv_countdown)

        // Block back button — user shouldn't be able to escape mid-transition
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { /* consume */ }
            }
        )

        // VM's currentStepIndex was already incremented before this fragment was reached,
        // so currentActivity now returns the *next* activity.
        val nextLabel = routineViewModel.currentActivity?.label ?: "next activity"
        tvEncouragement.text = "Nice work — next up: $nextLabel"

        observeNavigation()
        playChime()
        startCountdown()
    }

    private fun observeNavigation() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                routineViewModel.navigationEvent.collect { event ->
                    if (event is RoutineViewModel.NavigationEvent.GoToActivity) {
                        navigateToActivity(event.index)
                    }
                }
            }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewLifecycleOwner.lifecycleScope.launch {
            for (i in COUNTDOWN_SECONDS downTo 1) {
                tvCountdown.text = i.toString()
                delay(1000)
            }
            tvCountdown.text = ""
            routineViewModel.onTransitionComplete()
        }
    }

    private fun playChime() {
        // R.raw.chime — drop any short .mp3/.wav file at res/raw/chime.{ext}.
        // If absent, fail silently.
        val resId = resources.getIdentifier("chime", "raw", requireContext().packageName)
        if (resId == 0) return
        try {
            chimePlayer = MediaPlayer.create(requireContext(), resId)?.apply {
                setVolume(0.7f, 0.7f)
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (e: Exception) { /* silent fallback */ }
    }

    private fun navigateToActivity(index: Int) {
        val activity = routineViewModel.activities.getOrNull(index) ?: return
        val destinationId = when (activity.activityType.lowercase()) {
            "breathing"           -> R.id.breathingActivityFragment
            "audio", "audioscape" -> R.id.audioscapeActivityFragment
            "journaling"          -> R.id.gratitudeJournalingActivityFragment
            else                  -> R.id.genericTimerActivityFragment
        }
        findNavController().navigate(destinationId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countdownJob?.cancel()
        countdownJob = null
        try { chimePlayer?.run { if (isPlaying) stop(); release() } } catch (e: Exception) {}
        chimePlayer = null
    }
}