package com.noctra.app.ui.routine.onboarding

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noctra.app.data.model.Activity
import com.noctra.app.databinding.ItemSequencingRowBinding

class RoutineSequencingAdapter(
    private val onRemove: (Int) -> Unit
) : ListAdapter<Activity, RoutineSequencingAdapter.ViewHolder>(DIFF) {

    var touchHelper: ItemTouchHelper? = null

    inner class ViewHolder(val binding: ItemSequencingRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bind(activity: Activity, position: Int) {
            binding.tvStepNumber.text = "${position + 1}"
            binding.tvActivityLabel.text = activity.label
            binding.tvActivityDuration.text = "${activity.defaultDurationMinutes} minutes"

            // Drag handle — start drag on touch down
            binding.ivDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchHelper?.startDrag(this)
                }
                false
            }

            // Remove button
            binding.btnRemove.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) {
                    onRemove(pos)
                }
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