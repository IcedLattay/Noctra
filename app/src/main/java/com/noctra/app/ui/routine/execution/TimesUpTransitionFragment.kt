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
 * 5-second transition between activities. Plays a chime, shows the
 * completion message for the activity that just finished, then notifies
 * the VM which fires GoToActivity for the next destination.
 *
 * IMPORTANT: routineViewModel.currentStepIndex is already incremented by
 * RoutineViewModel.onActivityComplete() before this fragment is reached, so
 * routineViewModel.currentActivity now points to the *next* activity, not
 * the one that just finished. The completion message shown here is keyed
 * off that *next* activity's position, which is a deliberate simplification —
 * see COMPLETION_MESSAGE_BY_LABEL below for the caveat this creates.
 */
class TimesUpTransitionFragment : Fragment() {

    private val routineViewModel: RoutineViewModel by activityViewModels()

    private lateinit var tvEncouragement: TextView
    private lateinit var tvCountdown: TextView

    private var chimePlayer: MediaPlayer? = null
    private var countdownJob: Job? = null

    companion object {
        private const val COUNTDOWN_SECONDS = 5

        // Not part of the DB schema — completion message per activity label,
        // sourced from the wireframes. Shown here rather than by
        // RoutineCompletionOverlayFragment, since that fragment only fires
        // once at the very end of the whole routine (session-level reward
        // screen), not after each individual activity.
        private val COMPLETION_MESSAGE_BY_LABEL = mapOf(
            "Reading" to "Your eyes and mind are ready to rest. Reading is great, keep it up!",
            "White/Pink Noise" to "Good job. Keeping up with such noise is such a skill!",
            "Bedtime To-Do List Writing" to "You don't have to worry about missing a task tomorrow. Great job!",
            "Warm Shower" to "Good job! Your body will start cooling down.",
            "Slow-Paced Breathing" to "You did a fantastic job. Breathing is an essential core of how we live.",
            "Mindfulness" to "Good job! Slowly bring your awareness back. Well done.",
            "Progressive Muscle Relaxation" to "Your eyes and body are ready to rest as every muscle lets go.",
            "Bedtime Stretching" to "Great job, your body and mind thank you for this.",
            "Gratitude Journaling" to "Great job. Suppressing your thoughts is bad for your health. Think of yourself for a second."
        )
    }

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

        // currentStepIndex was already incremented before this fragment was
        // reached, so currentActivity here is the activity that JUST
        // finished only if step index still pointed at it — but per
        // RoutineViewModel.onActivityComplete(), the index is incremented
        // BEFORE this fragment is shown, meaning currentActivity actually
        // returns the *next* activity already. Since we want the message
        // for the activity that just finished, we look one step back.
        val justFinishedIndex = (routineViewModel.currentStepIndex.value - 1)
            .coerceAtLeast(0)
        val justFinishedLabel = routineViewModel.activities.getOrNull(justFinishedIndex)?.label
        val completionMessage = COMPLETION_MESSAGE_BY_LABEL[justFinishedLabel]
            ?: "Great job completing this activity!"

        val nextLabel = routineViewModel.currentActivity?.label ?: "next activity"
        tvEncouragement.text = "$completionMessage\n\nNext up: $nextLabel"

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

    /**
     * Same label -> destination dispatch as RoutineStartFragment. All 9
     * seeded activities route to one of the 4 existing shared destinations.
     */
    private fun navigateToActivity(index: Int) {
        val activity = routineViewModel.activities.getOrNull(index) ?: return

        val destinationId = when (activity.label) {
            "Bedtime To-Do List Writing",
            "Reading",
            "White/Pink Noise",
            "Warm Shower"
                -> R.id.audioscapeActivityFragment

            "Slow-Paced Breathing",
            "Mindfulness"
                -> R.id.breathingActivityFragment

            "Gratitude Journaling"
                -> R.id.gratitudeJournalingActivityFragment

            "Progressive Muscle Relaxation",
            "Bedtime Stretching"
                -> R.id.genericTimerActivityFragment

            else -> {
                android.util.Log.w(
                    "TimesUpTransitionFragment",
                    "No destination mapping for activity label '${activity.label}', falling back to genericTimerActivityFragment"
                )
                R.id.genericTimerActivityFragment
            }
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