package com.noctra.app.ui.routine.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noctra.app.data.model.Activity
import com.noctra.app.databinding.ItemSequencingRowBinding

class RoutineSequencingAdapter(
    private val onMove: (from: Int, to: Int) -> Unit
) : ListAdapter<Activity, RoutineSequencingAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemSequencingRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(activity: Activity, position: Int) {
            binding.tvStepNumber.text = "${position + 1}"
            binding.tvActivityLabel.text = activity.label
            binding.tvActivityDescription.text = activity.description
            binding.tvActivityDuration.text = "${activity.defaultDurationMinutes} minutes"

            // Chevron steppers — tap to move one slot
            binding.btnMoveUp.setOnClickListener {
                val from = adapterPosition
                if (from != RecyclerView.NO_POSITION && from > 0) onMove(from, from - 1)
            }
            binding.btnMoveDown.setOnClickListener {
                val from = adapterPosition
                if (from != RecyclerView.NO_POSITION && from < itemCount - 1) onMove(from, from + 1)
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSequencingRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Activity>() {
            override fun areItemsTheSame(a: Activity, b: Activity) =
                a.activityId == b.activityId
            override fun areContentsTheSame(a: Activity, b: Activity) = a == b
        }
    }
}