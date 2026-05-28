package com.noctra.app.ui.routine

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.noctra.app.domain.usecase.RewardCalculationUseCase
import kotlinx.coroutines.launch

/**
 * RoutineCompletionOverlayFragment
 *
 * Full-screen completion overlay shown after the user completes their routine.
 * Displays Dream Tokens, Growth Points, and current streak.
 *
 * Behavior (per SDD):
 *   - Not dismissible via back button
 *   - Auto-navigates to CompanionFragment after AUTO_DISMISS_MS milliseconds
 *   - Fades out before navigating (handled via nav transition animation)
 *
 * File location: com/noctra/app/ui/routine/RoutineCompletionOverlayFragment.kt
 */
class RoutineCompletionOverlayFragment : Fragment() {

    // ─── ViewModel ────────────────────────────────────────────────────────────

    private val routineViewModel: RoutineViewModel by activityViewModels()

    // ─── Views ────────────────────────────────────────────────────────────────

    private lateinit var tvTokensEarned: TextView
    private lateinit var tvXpEarned: TextView
    private lateinit var tvStreak: TextView

    // ─── Constants ────────────────────────────────────────────────────────────

    companion object {
        /** How long the overlay stays visible before auto-navigating. */
        private const val AUTO_DISMISS_MS = 4000L
    }

    private val autoDismissHandler = Handler(Looper.getMainLooper())

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_routine_completion_overlay, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        blockBackButton()
        observeRewardResult()
        scheduleAutoDismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        autoDismissHandler.removeCallbacksAndMessages(null)
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private fun bindViews(view: View) {
        tvTokensEarned = view.findViewById(R.id.tv_tokens_earned)
        tvXpEarned     = view.findViewById(R.id.tv_xp_earned)
        tvStreak       = view.findViewById(R.id.tv_streak)
    }

    /**
     * Blocks the system back button per SDD spec:
     * "Not dismissible via back button."
     */
    private fun blockBackButton() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Intentionally consume the back press — do nothing
                }
            }
        )
    }

    // ─── Reward Display ───────────────────────────────────────────────────────

    private fun observeRewardResult() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                routineViewModel.rewardResult.collect { result ->
                    result?.let { populateRewards(it) }
                }
            }
        }
    }

    private fun populateRewards(result: RewardCalculationUseCase.RewardResult) {
        tvTokensEarned.text = "+${result.tokensEarned}"
        tvXpEarned.text     = "+${result.xpEarned}"

        // Streak = currentStreak + 1 (tonight counts)
        val newStreak = (routineViewModel.currentStreak + 1)
        tvStreak.text = "$newStreak ${if (newStreak == 1) "day" else "days"}"
    }

    // ─── Auto Dismiss ─────────────────────────────────────────────────────────

    /**
     * Navigates to CompanionFragment after AUTO_DISMISS_MS milliseconds.
     * Resets the RoutineViewModel so it's fresh for tomorrow's session.
     */
    private fun scheduleAutoDismiss() {
        autoDismissHandler.postDelayed({
            if (isAdded && !isDetached) {
                routineViewModel.reset()
                findNavController().navigate(
                    R.id.action_routineCompletionOverlayFragment_to_companionFragment
                )
            }
        }, AUTO_DISMISS_MS)
    }
}