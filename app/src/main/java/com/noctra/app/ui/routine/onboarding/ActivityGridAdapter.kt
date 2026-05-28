package com.noctra.app.ui.routine.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noctra.app.R
import com.noctra.app.data.model.Activity
import com.noctra.app.databinding.ItemActivityCardBinding

class ActivityGridAdapter(
    private val onActivityClick: (Activity) -> Unit
) : ListAdapter<Activity, ActivityGridAdapter.ViewHolder>(DIFF) {

    private var selectedIds: Set<String> = emptySet()

    fun setSelected(ids: Set<String>) {
        selectedIds = ids
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemActivityCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(activity: Activity) {
            val isSelected = selectedIds.contains(activity.activityId)

            binding.tvActivityName.text = activity.label
            binding.tvActivityDuration.text = "${activity.defaultDurationMinutes}m"

            // Icon — map activity label to drawable
            val iconRes = when (activity.label) {
                "Slow-Paced Breathing" -> R.drawable.ic_alarm_clock
                "White/Pink Noise"     -> R.drawable.ic_alarm_clock
                "Gratitude Journaling" -> R.drawable.ic_book
                else                   -> R.drawable.ic_alarm_clock
            }
            binding.ivActivityIcon.setImageResource(iconRes)

            // Selected state: stroke color + checkmark
            binding.root.strokeColor = if (isSelected)
                ContextCompat.getColor(binding.root.context, R.color.noctra_purple)
            else
                ContextCompat.getColor(binding.root.context, R.color.noctra_lavender_border)

            binding.ivCheckSelected.visibility =
                if (isSelected) android.view.View.VISIBLE else android.view.View.GONE

            // Dim unselectable cards when 3 already chosen
            val maxReached = selectedIds.size >= 3
            binding.root.alpha = if (maxReached && !isSelected) 0.5f else 1.0f

            binding.root.setOnClickListener { onActivityClick(activity) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemActivityCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Activity>() {
            override fun areItemsTheSame(a: Activity, b: Activity) =
                a.activityId == b.activityId
            override fun areContentsTheSame(a: Activity, b: Activity) = a == b
        }
    }
}