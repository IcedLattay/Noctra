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
import com.noctra.app.utils.ActivityIllustrations

/**
 * RoutineSequencingAdapter — "Arrange Your Routine" list (onboarding + Edit Routine).
 * Hybrid (merge/routine-cards):
 *   - their visuals: illustration thumbnail per row, drag handle
 *   - our chevrons: tap to move one slot (exact, accessible moves)
 *   - reorder by chevron tap (exact) or row long-press drag
 *   - moves commit through onMove only — never notifyItemMoved manually
 *     (manual + DiffUtil double-handling cancels the move out)
 */
class RoutineSequencingAdapter(
    private val onMove: (from: Int, to: Int) -> Unit
) : ListAdapter<Activity, RoutineSequencingAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemSequencingRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(activity: Activity, position: Int) {
            binding.tvStepNumber.text = "${position + 1}"
            binding.tvActivityLabel.text = activity.label
            binding.tvActivityDuration.text = "${activity.defaultDurationMinutes} minutes"

            ActivityIllustrations.load(binding.ivActivityIllustration, activity.label)

            // Chevron steppers — tap to move one slot
            binding.btnMoveUp.setOnClickListener {
                val from = adapterPosition
                if (from != RecyclerView.NO_POSITION && from > 0) onMove(from, from - 1)
            }
            binding.btnMoveDown.setOnClickListener {
                val from = adapterPosition
                if (from != RecyclerView.NO_POSITION && from < itemCount - 1) onMove(from, from + 1)
            }

            // No row long-press action: info dialogs live on the selection
            // screen, not here — ItemTouchHelper owns long-press for drag.
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
