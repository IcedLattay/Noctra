package com.noctra.app.ui.routine.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noctra.app.R
import com.noctra.app.data.model.Activity
import com.noctra.app.utils.ActivityIllustrations

/**
 * ActivityCardAdapter
 *
 * Drives the activity list on RoutineHomeFragment.
 * Session 5: single full-width column (was a 2-column grid), real
 * illustrations via ActivityIllustrations, one-line description, and
 * long-press -> onLongPress(activity, stepNumber) to open the info dialog.
 *
 * File location: com/noctra/app/ui/routine/home/ActivityCardAdapter.kt
 */
class ActivityCardAdapter(
    private val onLongPress: ((activity: Activity, stepNumber: Int) -> Unit)? = null
) : ListAdapter<Activity, ActivityCardAdapter.ActivityCardViewHolder>(ActivityDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityCardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_card_home, parent, false)
        return ActivityCardViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActivityCardViewHolder, position: Int) {
        val activity = getItem(position)
        val stepNumber = position + 1 // 1-based
        holder.bind(activity, stepNumber)

        holder.itemView.setOnLongClickListener {
            onLongPress?.invoke(activity, stepNumber)
            onLongPress != null // true = long-press handled
        }
    }

    // ─── ViewHolder ───────────────────────────────────────────────────────────

    class ActivityCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val tvStepNumber: TextView    = itemView.findViewById(R.id.tv_step_number)
        private val ivActivityIcon: ImageView = itemView.findViewById(R.id.iv_activity_icon)
        private val tvActivityLabel: TextView = itemView.findViewById(R.id.tv_activity_label)
        private val tvDuration: TextView      = itemView.findViewById(R.id.tv_activity_duration)
        private val tvDescription: TextView   = itemView.findViewById(R.id.tv_activity_description)

        fun bind(activity: Activity, stepNumber: Int) {
            tvStepNumber.text    = stepNumber.toString()
            tvActivityLabel.text = activity.label
            tvDuration.text      = "${activity.defaultDurationMinutes} min"

            if (activity.description.isNotBlank()) {
                tvDescription.text = activity.description
                tvDescription.visibility = View.VISIBLE
            } else {
                tvDescription.visibility = View.GONE
            }

            ActivityIllustrations.load(ivActivityIcon, activity.label)
        }
    }

    // ─── DiffCallback ─────────────────────────────────────────────────────────

    class ActivityDiffCallback : DiffUtil.ItemCallback<Activity>() {
        override fun areItemsTheSame(oldItem: Activity, newItem: Activity): Boolean =
            oldItem.activityId == newItem.activityId

        override fun areContentsTheSame(oldItem: Activity, newItem: Activity): Boolean =
            oldItem == newItem
    }
}