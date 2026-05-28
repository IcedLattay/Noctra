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

/**
 * ActivityCardAdapter
 *
 * Drives the 2-column activity grid on RoutineHomeFragment.
 * Uses ListAdapter with DiffUtil for efficient updates.
 *
 * File location: com/noctra/app/ui/routine/home/ActivityCardAdapter.kt
 */
class ActivityCardAdapter :
    ListAdapter<Activity, ActivityCardAdapter.ActivityCardViewHolder>(ActivityDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityCardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_card_home, parent, false)
        return ActivityCardViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActivityCardViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1) // position + 1 = step number (1-based)
    }

    // ─── ViewHolder ───────────────────────────────────────────────────────────

    class ActivityCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val tvStepNumber: TextView    = itemView.findViewById(R.id.tv_step_number)
        private val ivActivityIcon: ImageView = itemView.findViewById(R.id.iv_activity_icon)
        private val tvActivityLabel: TextView = itemView.findViewById(R.id.tv_activity_label)
        private val tvDuration: TextView      = itemView.findViewById(R.id.tv_activity_duration)

        fun bind(activity: Activity, stepNumber: Int) {
            tvStepNumber.text   = stepNumber.toString()
            tvActivityLabel.text = activity.label
            tvDuration.text     = "${activity.defaultDurationMinutes} min"

            // Map activity type to icon drawable
            val iconRes = getIconForActivityType(activity.activityType)
            ivActivityIcon.setImageResource(iconRes)
        }

        /**
         * Maps activity_type string from Supabase to a local drawable resource.
         * Add new types here as the activity library grows.
         */
        private fun getIconForActivityType(activityType: String): Int {
            return when (activityType.lowercase()) {
                "breathing"           -> R.drawable.bg_breathing_circle
                "audio", "audioscape" -> R.drawable.ic_nav_companion
                "journaling"          -> R.drawable.ic_book
                "stretching"          -> R.drawable.ic_nav_profile
                "reading"             -> R.drawable.ic_book
                "meditation"          -> R.drawable.ic_nav_companion
                else                  -> R.drawable.ic_clock
            }
        }
    }

    // ─── DiffCallback ─────────────────────────────────────────────────────────

    class ActivityDiffCallback : DiffUtil.ItemCallback<Activity>() {
        override fun areItemsTheSame(oldItem: Activity, newItem: Activity): Boolean {
            return oldItem.activityId == newItem.activityId
        }

        override fun areContentsTheSame(oldItem: Activity, newItem: Activity): Boolean {
            return oldItem == newItem
        }
    }
}
