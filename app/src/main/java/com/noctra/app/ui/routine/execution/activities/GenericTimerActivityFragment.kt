package com.noctra.app.ui.routine.execution.activities

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
import com.noctra.app.databinding.FragmentGenericTimerActivityBinding
import com.noctra.app.ui.routine.RoutineViewModel
import kotlinx.coroutines.launch

/**
 * GenericTimerActivityFragment
 *
 * Handles all non-MVP activities that don't have custom UIs.
 * Synchronized with RoutineViewModel for timer management.
 */
class GenericTimerActivityFragment : Fragment() {

    private var _binding: FragmentGenericTimerActivityBinding? = null
    private val binding get() = _binding!!

    private val routineViewModel: RoutineViewModel by activityViewModels()

    private enum class TimerState {
        PRE_COUNTDOWN, RUNNING
    }

    private var state = TimerState.PRE_COUNTDOWN
    private var preCountdownTimer: CountDownTimer? = null

    companion object {
        private const val PRE_COUNTDOWN_MS  = 15_000L
        private const val TICK_MS           = 1_000L
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGenericTimerActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupStaticUI()
        setupListeners()
        observeVm()
        startPreCountdown()
    }

    private fun setupListeners() {
        binding.btnCompleteRoutine.setOnClickListener {
            routineViewModel.onCompleteRoutineTapped()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        preCountdownTimer?.cancel()
        _binding = null
    }

    private fun setupStaticUI() {
        val activity = routineViewModel.currentActivity ?: return
        binding.tvActivityTitle.text    = activity.label
        binding.tvInstruction.text      = activity.instruction
        binding.tvCompletionMessage.text = completionMessageFor(activity.label)

        val iconRes = resources.getIdentifier(
            activity.iconAsset, "drawable", requireContext().packageName
        )
        if (iconRes != 0) binding.imgActivityIcon.setImageResource(iconRes)
    }

    private fun observeVm() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    routineViewModel.activitySecondsRemaining.collect { secs ->
                        if (state != TimerState.PRE_COUNTDOWN) {
                            updateTimerText(secs * 1000L, isPreCountdown = false)
                            
                            // Show "Complete Routine" button if timer is 0 AND it's the last step
                            if (secs == 0 && routineViewModel.isLastStep) {
                                binding.btnCompleteRoutine.visibility = View.VISIBLE
                            } else {
                                binding.btnCompleteRoutine.visibility = View.GONE
                            }
                        }
                    }
                }
                launch {
                    routineViewModel.navigationEvent.collect { event ->
                        when (event) {
                            is RoutineViewModel.NavigationEvent.GoToTransition -> {
                                findNavController().navigate(R.id.timesUpTransitionFragment)
                            }
                            is RoutineViewModel.NavigationEvent.GoToCompletion -> {
                                findNavController().navigate(R.id.routineCompletionOverlayFragment)
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun startPreCountdown() {
        state = TimerState.PRE_COUNTDOWN
        updateTimerText(PRE_COUNTDOWN_MS, isPreCountdown = true)

        preCountdownTimer = object : CountDownTimer(PRE_COUNTDOWN_MS, TICK_MS) {
            override fun onTick(ms: Long) = updateTimerText(ms, isPreCountdown = true)
            override fun onFinish() {
                state = TimerState.RUNNING
                routineViewModel.startCurrentActivityTimer()
            }
        }.start()
    }

    private fun updateTimerText(ms: Long, isPreCountdown: Boolean) {
        val totalSec = (ms / 1000).toInt()
        binding.tvTimer.text = String.format("%02d : %02d", totalSec / 60, totalSec % 60)

        binding.tvTimer.setTextColor(
            ContextCompat.getColor(requireContext(),
                when {
                    isPreCountdown  -> R.color.timer_green
                    ms <= 30_000L   -> R.color.timer_red
                    else            -> R.color.timer_default
                }
            )
        )
    }

    private fun completionMessageFor(label: String): String = when (label) {
        "Reading"                        -> "Your eyes and mind are ready to rest."
        "Warm Shower"                    -> "A warm shower helps your body cool down naturally."
        "Bedtime Stretching"             -> "Your muscles are relaxed and ready for rest."
        "Progressive Muscle Relaxation"  -> "Your body is fully relaxed."
        "Mindfulness"                    -> "Your mind is calm and centered."
        "Bedtime To-Do List"             -> "Your tasks are noted. Now let your mind rest!"
        else                             -> "Great job completing this activity!"
    }
}