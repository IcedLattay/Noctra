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
 * completion message for the activity that just finished plus what's coming
 * next, then notifies the VM which fires GoToActivity for the next destination.
 *
 * FIXED: previously showed only "Nice work — next up: X" — the per-activity
 * completion messages from the wireframes had never been wired into any
 * screen in the app. Now shown here.
 *
 * NOTE on which activity's message appears: RoutineViewModel.onActivityComplete()
 * increments currentStepIndex BEFORE emitting GoToTransition, so by the time
 * this fragment runs, currentActivity already points at the NEXT activity.
 * The message for the activity that just ENDED therefore comes from
 * activities[currentStepIndex - 1].
 *
 * NOTE on the last activity: it never reaches this screen. The final activity
 * emits GoToCompletion (to RoutineCompletionOverlayFragment) instead of
 * GoToTransition, so its completion message is never displayed. If that
 * message matters, it needs to be surfaced on the completion overlay instead.
 */
class TimesUpTransitionFragment : Fragment() {

    private val routineViewModel: RoutineViewModel by activityViewModels()

    private lateinit var tvEncouragement: TextView
    private lateinit var tvCountdown: TextView

    private var chimePlayer: MediaPlayer? = null
    private var countdownJob: Job? = null

    companion object {
        private const val COUNTDOWN_SECONDS = 5

        private const val DEFAULT_COMPLETION_MESSAGE = "Great job completing this activity!"

        /**
         * Per-activity completion copy, taken from the activity wireframes.
         * Keys must match activity_library.label exactly.
         *
         * Not part of the DB schema — if these ever need to be editable
         * without a rebuild, they'd belong in a `completion_message` column
         * on activity_library instead.
         */
        private val COMPLETION_MESSAGE_BY_LABEL = mapOf(
            "Reading" to
                    "Your eyes and mind are ready to rest. Reading is great, keep it up!",
            "White/Pink Noise" to
                    "Good job. Keeping up with such noise is such a skill!",
            "Bedtime To-Do List Writing" to
                    "You don't have to worry about missing a task tomorrow. Great job!",
            "Warm Shower" to
                    "Good job! Your body will start cooling down.",
            "Slow-Paced Breathing" to
                    "You did a fantastic job. Breathing is an essential core of how we live.",
            "Mindfulness" to
                    "Good job! Slowly bring your awareness back. Well done.",
            "Progressive Muscle Relaxation" to
                    "Your eyes and body are ready to rest as every muscle lets go.",
            "Bedtime Stretching" to
                    "Great job, your body and mind thank you for this."
            // Gratitude Journaling's wireframe message contained a typo
            // ("Surpressing"); corrected spelling used below.
            ,"Gratitude Journaling" to
                    "Great job. Suppressing your thoughts is bad for your health. Think of yourself for a second."
            // Low-Stimulus Audio Listening had no wireframe message of its own
            // (its wireframe mistakenly reused Bedtime To-Do List Writing's
            // copy) — original copy written below, matching the tone of the
            // other AUDIO-type messages (White/Pink Noise, Mindfulness).
            ,"Low-Stimulus Audio Listening" to
                    "Good job. Letting the sound wash over you is a great way to unwind."
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

        showTransitionText()
        observeNavigation()
        playChime()
        startCountdown()
    }

    /**
     * Shows the completion message for the activity that just ended, plus a
     * line naming what's coming next.
     */
    private fun showTransitionText() {
        val activities = routineViewModel.activities

        // currentStepIndex was already advanced by the VM, so step back one
        // to find the activity that actually just finished.
        val justFinishedIndex = (routineViewModel.currentStepIndex.value - 1).coerceAtLeast(0)
        val justFinishedLabel = activities.getOrNull(justFinishedIndex)?.label

        val completionMessage = COMPLETION_MESSAGE_BY_LABEL[justFinishedLabel]
            ?: DEFAULT_COMPLETION_MESSAGE

        val nextLabel = routineViewModel.currentActivity?.label

        tvEncouragement.text = if (nextLabel != null) {
            "$completionMessage\n\nNext up: $nextLabel"
        } else {
            completionMessage
        }
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
     * Same label -> destination dispatch as RoutineStartFragment.
     * Keep both in sync.
     */
    private fun navigateToActivity(index: Int) {
        val activity = routineViewModel.activities.getOrNull(index) ?: return

        val destinationId = when (activity.label) {
            "Bedtime To-Do List Writing",
            "Reading",
            "White/Pink Noise",
            "Warm Shower",
            "Mindfulness",                    // nature-scenery ambient variant
            "Low-Stimulus Audio Listening"    // waveform variant
                -> R.id.audioscapeActivityFragment

            "Slow-Paced Breathing"
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