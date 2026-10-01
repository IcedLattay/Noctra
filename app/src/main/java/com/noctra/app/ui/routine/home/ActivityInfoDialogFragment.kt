package com.noctra.app.ui.routine.home

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.noctra.app.R
import com.noctra.app.data.model.Activity
import com.noctra.app.utils.ActivityIllustrations

/**
 * ActivityInfoDialogFragment — Session 5.
 *
 * Opened by LONG-PRESSING an activity card (Routine screen now; onboarding /
 * edit cards in step 4). Matches the user's mockup:
 *   round illustration -> [number badge] title -> duration ->
 *   DESCRIPTION box (lavender) -> ACTIVITY TYPE box (cream/gold).
 * Closes via the round X or tapping outside. No other buttons.
 *
 * "Dumb" dialog: only shows what it's given, no ViewModel.
 * Pass stepNumber = 0 to hide the number badge (e.g. in the activity library,
 * where cards aren't in routine order yet).
 */
class ActivityInfoDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "ActivityInfoDialog"

        private const val ARG_LABEL = "label"
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_TYPE = "activity_type"
        private const val ARG_DURATION = "duration"
        private const val ARG_STEP = "step_number"

        fun newInstance(activity: Activity, stepNumber: Int = 0) =
            ActivityInfoDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_LABEL, activity.label)
                    putString(ARG_DESCRIPTION, activity.description)
                    putString(ARG_TYPE, activity.activityType)
                    putInt(ARG_DURATION, activity.defaultDurationMinutes)
                    putInt(ARG_STEP, stepNumber)
                }
            }

        /** Shows the dialog, ignoring rapid double long-presses. */
        fun show(fm: FragmentManager, activity: Activity, stepNumber: Int = 0) {
            if (fm.findFragmentByTag(TAG) != null) return
            newInstance(activity, stepNumber).show(fm, TAG)
        }

        /**
         * Friendly text for the raw activity_type value from the DB.
         * Unknown types fall back to the raw value, capitalized.
         */
        fun friendlyType(raw: String?): String {
            if (raw.isNullOrBlank()) return "—"
            return when (raw.uppercase()) {
                "AUDIO"      -> "Audio · Put on your headphones and listen"
                "TIMER"      -> "Timed · Follow along until the timer ends"
                "BREATHING"  -> "Breathing · Follow the guided breathing circle"
                "JOURNALING" -> "Journaling · Write down your thoughts"
                "STEPPER"    -> "Guided steps · Follow each movement on screen"
                "PHYS", "PHYSICAL" -> "Physical · Gentle movement to relax your body"
                else         -> raw.lowercase().replace('_', ' ')
                    .replaceFirstChar { it.uppercase() }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_activity_info, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val label = args.getString(ARG_LABEL)
        val step = args.getInt(ARG_STEP)
        val minutes = args.getInt(ARG_DURATION)

        ActivityIllustrations.load(view.findViewById<ImageView>(R.id.iv_info_illustration), label)

        view.findViewById<TextView>(R.id.tv_info_title).text = label

        view.findViewById<TextView>(R.id.tv_info_step).apply {
            if (step > 0) { text = step.toString(); visibility = View.VISIBLE }
            else visibility = View.GONE
        }

        view.findViewById<TextView>(R.id.tv_info_duration).text =
            if (minutes == 1) "1 minute" else "$minutes minutes"

        view.findViewById<TextView>(R.id.tv_info_description).text =
            args.getString(ARG_DESCRIPTION).takeUnless { it.isNullOrBlank() }
                ?: "No description yet."

        view.findViewById<TextView>(R.id.tv_info_type).text =
            friendlyType(args.getString(ARG_TYPE))

        view.findViewById<View>(R.id.btn_info_close).setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.apply {
            setCanceledOnTouchOutside(true)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) // let our rounded card show
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            window?.setDimAmount(0.55f)
        }
    }
}