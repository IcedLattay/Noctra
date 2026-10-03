package com.noctra.app.ui.routine.onboarding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noctra.app.R
import com.noctra.app.data.model.Activity
import com.noctra.app.databinding.ItemActivityCardBinding
import com.noctra.app.utils.ActivityIllustrations

/**
 * ActivityGridAdapter — "Choose Your Activities" grid (onboarding + Edit Routine).
 *
 * Session 5 (R1): real illustrations via ActivityIllustrations (replaces the
 * alarm-clock placeholders), and long-press -> onActivityLongPress so the
 * fragment can open ActivityInfoDialogFragment.
 */
class ActivityGridAdapter(
    private val onActivityClick: (Activity) -> Unit,
    private val onInfoClick: (Activity) -> Unit = {}
) : ListAdapter<Activity, ActivityGridAdapter.ViewHolder>(DIFF) {

    private var selectedIds: Set<String> = emptySet()

    fun setSelected(ids: Set<String>) {
        selectedIds = ids
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemActivityCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(activity: Activity) {
            val context = binding.root.context
            val isSelected = selectedIds.contains(activity.activityId)

            binding.tvActivityName.text = activity.label
            binding.tvActivityDuration.text = "${activity.defaultDurationMinutes} min"

            ActivityIllustrations.load(binding.ivActivityIcon, activity.label)

            // Selected state: purple stroke + check badge
            binding.root.strokeColor = if (isSelected)
                ContextCompat.getColor(context, R.color.noctra_purple)
            else
                ContextCompat.getColor(context, R.color.noctra_lavender_border)

            binding.ivCheckSelected.visibility = if (isSelected) View.VISIBLE else View.GONE

            // Dim unselectable cards when 3 already chosen, and kill the
            // ripple so they don't look pressable
            val maxReached = selectedIds.size >= 3
            val enabled = !maxReached || isSelected
            binding.root.alpha = if (enabled) 1.0f else 0.5f
            binding.root.isClickable = enabled

            binding.root.setOnClickListener { onActivityClick(activity) }
            binding.btnInfo.setOnClickListener { onInfoClick(activity) }
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