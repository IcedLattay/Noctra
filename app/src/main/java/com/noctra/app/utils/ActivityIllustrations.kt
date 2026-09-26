package com.noctra.app.utils

import android.content.Context
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.noctra.app.R

/**
 * ActivityIllustrations
 *
 * One place that answers: "which illustration goes with this activity?"
 * Used by the Routine screen cards, the onboarding/edit cards, and the
 * long-press info dialog, so all three always show the same picture.
 *
 * Keys must match activity_library.label EXACTLY (same as the label
 * dispatch in RoutineStartFragment / TimesUpTransitionFragment).
 *
 * If a label is missing here, or the drawable file doesn't exist, the
 * lavender -> cream placeholder is shown instead of crashing.
 */
object ActivityIllustrations {

    private val DRAWABLE_NAME_BY_LABEL = mapOf(
        "Gratitude Journaling"          to "illus_gratitude_journaling",
        "Bedtime To-Do List Writing"    to "illus_todo_list",
        "Reading"                       to "illus_reading",
        "White/Pink Noise"              to "illus_white_pink_noise",
        "Mindfulness"                   to "illus_mindfulness",
        "Warm Shower"                   to "illus_warm_shower",
        "Slow-Paced Breathing"          to "illus_breathing",
        "Progressive Muscle Relaxation" to "illus_pmr",
        "Bedtime Stretching"            to "illus_stretching",
        "Low-Stimulus Audio Listening"  to "illus_low_stimulus_audio"
    )

    /** Drawable resource ID for this activity, or 0 if there isn't one. */
    @DrawableRes
    fun resIdFor(context: Context, label: String?): Int {
        val name = DRAWABLE_NAME_BY_LABEL[label] ?: return 0
        return context.resources.getIdentifier(name, "drawable", context.packageName)
    }

    /** Puts the activity's illustration into [imageView], or the placeholder. */
    fun load(imageView: ImageView, label: String?) {
        val resId = resIdFor(imageView.context, label)
        if (resId != 0) {
            imageView.setImageResource(resId)
            imageView.background = null
        } else {
            imageView.setImageDrawable(null)
            imageView.setBackgroundResource(R.drawable.bg_illus_placeholder)
        }
    }
}