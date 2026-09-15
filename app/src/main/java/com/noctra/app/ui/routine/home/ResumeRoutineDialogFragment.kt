package com.noctra.app.ui.routine.home

import android.app.Dialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.noctra.app.databinding.DialogResumeRoutineBinding

/**
 * ResumeRoutineDialogFragment (Global Resume Popup, task 6/10).
 *
 * Reusable dialog: "You have a routine in progress. Resume?" — matches
 * Mesha's wireframe (same dialog shown over both the Companion and Routines
 * tab backgrounds, confirming it's meant to be a single reusable component
 * rather than screen-specific).
 *
 * This fragment is intentionally "dumb" — it only displays what it's told
 * (step position, activity count, away-duration) and reports back which
 * button was tapped via the Fragment Result API. It does NOT know about
 * RoutineViewModel, RoutinePersistenceHelper, or navigation — that's the
 * caller's job (MainActivity, task 7) so this dialog stays reusable from
 * anywhere (e.g. also the "Resume Routine" button mentioned on the Routines
 * tab in the wireframe, if that ever needs to reopen this same dialog).
 *
 * Usage (see task 7 for the real wiring):
 *   ResumeRoutineDialogFragment.newInstance(
 *       currentStepIndex = 1, totalSteps = 3, awayMinutes = 3
 *   ).show(supportFragmentManager, ResumeRoutineDialogFragment.TAG)
 *
 *   supportFragmentManager.setFragmentResultListener(
 *       ResumeRoutineDialogFragment.REQUEST_KEY, this
 *   ) { _, bundle ->
 *       val resumed = bundle.getBoolean(ResumeRoutineDialogFragment.RESULT_RESUMED)
 *       // true = user tapped "Resume Routine", false = "Not Now"
 *   }
 *
 * FLAG: back-press is disabled (isCancelable = false) — the user must
 * explicitly choose Resume or Not Now, since either choice has a real
 * state consequence (see task 8) and the wireframe doesn't define a
 * separate "dismissed without choosing" behavior.
 */
class ResumeRoutineDialogFragment : DialogFragment() {

    private var _binding: DialogResumeRoutineBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val TAG = "ResumeRoutineDialogFragment"
        const val REQUEST_KEY = "resume_routine_dialog_request"
        const val RESULT_RESUMED = "resumed"

        private const val ARG_CURRENT_STEP_INDEX = "current_step_index"
        private const val ARG_TOTAL_STEPS = "total_steps"
        private const val ARG_AWAY_MINUTES = "away_minutes"

        /**
         * @param currentStepIndex 0-based index of the activity to resume at.
         * @param totalSteps Total number of activities in this routine (should be 3).
         * @param awayMinutes Rounded minutes since last_activity_timestamp.
         */
        fun newInstance(
            currentStepIndex: Int,
            totalSteps: Int,
            awayMinutes: Long
        ): ResumeRoutineDialogFragment {
            return ResumeRoutineDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_CURRENT_STEP_INDEX, currentStepIndex)
                    putInt(ARG_TOTAL_STEPS, totalSteps)
                    putLong(ARG_AWAY_MINUTES, awayMinutes)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogResumeRoutineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentStepIndex = arguments?.getInt(ARG_CURRENT_STEP_INDEX) ?: 0
        val totalSteps = arguments?.getInt(ARG_TOTAL_STEPS) ?: 1
        val awayMinutes = arguments?.getLong(ARG_AWAY_MINUTES) ?: 0L

        // Display is 1-based ("Step 2 of 3") even though the index is 0-based.
        binding.tvResumeStepLabel.text =
            "Resume at Step ${currentStepIndex + 1} of $totalSteps"
        binding.tvAwayDuration.text =
            "Away for ~$awayMinutes min"

        renderProgressDots(totalSteps, currentStepIndex)

        binding.btnResumeRoutine.setOnClickListener {
            setFragmentResult(REQUEST_KEY, Bundle().apply {
                putBoolean(RESULT_RESUMED, true)
            })
            dismiss()
        }

        binding.btnNotNow.setOnClickListener {
            setFragmentResult(REQUEST_KEY, Bundle().apply {
                putBoolean(RESULT_RESUMED, false)
            })
            dismiss()
        }
    }

    /**
     * 3-state dots: completed (navy), current (gold), upcoming (light gray).
     * Same code-generated approach as GenericTimerActivityFragment's
     * progress dots, extended to 3 visual states instead of 2.
     */
    private fun renderProgressDots(totalSteps: Int, currentStepIndex: Int) {
        val container = binding.dotsContainer
        container.removeAllViews()
        val dotSizePx = (8 * resources.displayMetrics.density).toInt()
        val marginPx = (3 * resources.displayMetrics.density).toInt()

        for (i in 0 until totalSteps) {
            val dot = View(requireContext())
            val params = android.widget.LinearLayout.LayoutParams(dotSizePx, dotSizePx)
            params.marginStart = marginPx
            params.marginEnd = marginPx
            dot.layoutParams = params

            val colorHex = when {
                i < currentStepIndex -> "#2C1769"  // completed — navy
                i == currentStepIndex -> "#E0A72E" // current — gold
                else -> "#D9D5EC"                   // upcoming — light lavender
            }

            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor(colorHex))
            }
            dot.background = drawable
            container.addView(dot)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}