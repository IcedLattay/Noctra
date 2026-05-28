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
 * RoutineStepAdapter
 *
 * Drives the ordered activity step list on RoutineStartFragment.
 * Each row shows step number, activity name, duration, and icon.
 *
 * File location: com/noctra/app/ui/routine/home/RoutineStepAdapter.kt
 */
class RoutineStepAdapter :
    ListAdapter<Activity, RoutineStepAdapter.StepViewHolder>(StepDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_routine_step_row, parent, false)
        return StepViewHolder(view)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    // ─── ViewHolder ───────────────────────────────────────────────────────────

    class StepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val tvStepNumber: TextView    = itemView.findViewById(R.id.tv_step_number)
        private val tvActivityLabel: TextView = itemView.findViewById(R.id.tv_activity_label)
        private val tvDuration: TextView      = itemView.findViewById(R.id.tv_activity_duration)
        private val ivIcon: ImageView         = itemView.findViewById(R.id.iv_activity_icon)

        fun bind(activity: Activity, stepNumber: Int) {
            tvStepNumber.text    = stepNumber.toString()
            tvActivityLabel.text = activity.label
            tvDuration.text      = "${activity.defaultDurationMinutes} minutes"

            val iconRes = when (activity.activityType.lowercase()) {
                "breathing"           -> R.drawable.bg_breathing_circle
                "audio", "audioscape" -> R.drawable.ic_nav_companion
                "journaling"          -> R.drawable.ic_book
                else                  -> R.drawable.ic_clock
            }
            ivIcon.setImageResource(iconRes)
        }
    }

    // ─── DiffCallback ─────────────────────────────────────────────────────────

    class StepDiffCallback : DiffUtil.ItemCallback<Activity>() {
        override fun areItemsTheSame(oldItem: Activity, newItem: Activity) =
            oldItem.activityId == newItem.activityId

        override fun areContentsTheSame(oldItem: Activity, newItem: Activity) =
            oldItem == newItem
    }
}
