package com.noctra.app.ui.routine.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.noctra.app.R
import com.noctra.app.databinding.FragmentRoutineSequencingBinding
import kotlinx.coroutines.launch

class RoutineSequencingFragment : Fragment() {

    private var _binding: FragmentRoutineSequencingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by navGraphViewModels(R.id.nav_graph)
    private lateinit var adapter: RoutineSequencingAdapter
    private lateinit var touchHelper: ItemTouchHelper

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoutineSequencingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeActivities()
        setupButtons()

        // Fresh-process resume: restore the saved draft unless edit mode
        // preloaded the active routine (or state already exists)
        if (!viewModel.isEditMode) {
            val userId = com.noctra.app.utils.UserSession.getUserId(requireContext())
            if (userId != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.restoreDraftIfEmpty(userId)
                }
            }
        }
    }
    // Single move path: the ViewModel emission re-submits the list and
    // DiffUtil animates the move. Never notifyItemMoved manually here —
    // double-handling (manual + diff) cancels the move out.
    private fun moveItem(from: Int, to: Int) {
        viewModel.reorderActivities(from, to)
    }

    private fun setupRecyclerView() {
        adapter = RoutineSequencingAdapter(
            onMove = ::moveItem
        )

        // ItemTouchHelper for drag-to-reorder. Long-press anywhere on a
        // card starts the drag; details open via the info button.
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                moveItem(viewHolder.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // No swipe action
            }

            // Clamp the drag so the card stays fully inside the list —
            // it can never be dragged out and cropped by the edges
            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                var finalDx = dX
                var finalDy = dY
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    val itemView = viewHolder.itemView
                    val marginPx = 8f * recyclerView.resources.displayMetrics.density
                    finalDx = 0f
                    finalDy = dY.coerceIn(
                        -itemView.top.toFloat() + marginPx,
                        (recyclerView.height - itemView.bottom).toFloat() - marginPx
                    )
                }
                super.onChildDraw(
                    c, recyclerView, viewHolder,
                    finalDx, finalDy, actionState, isCurrentlyActive
                )
            }

            // Visual feedback while dragging
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.8f
                    viewHolder?.itemView?.scaleX = 1.03f
                    viewHolder?.itemView?.scaleY = 1.03f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1f
                viewHolder.itemView.scaleX = 1f
                viewHolder.itemView.scaleY = 1f
            }
        }

        ItemTouchHelper(callback).attachToRecyclerView(binding.rvSequence)

        binding.rvSequence.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSequence.adapter = adapter
    }

    private fun observeActivities() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.orderedActivities.collect { activities ->
                // submitList needs a new list instance to detect changes.
                // DiffUtil moves don't rebind holders, so patch just the
                // rank numbers on commit — a full rebind would kill an
                // in-progress drag and force-release the card.
                adapter.submitList(activities.toList()) {
                    for (i in 0 until adapter.itemCount) {
                        val holder = binding.rvSequence
                            .findViewHolderForAdapterPosition(i) as? RoutineSequencingAdapter.ViewHolder
                        holder?.binding?.tvStepNumber?.text = "${i + 1}"
                    }
                }

                val total = viewModel.getTotalDurationMinutes()
                binding.tvTotalDuration.text = "$total minutes"
                binding.tvRoutineStartHint.text =
                    "Your routine will start $total minutes\nbefore your target bedtime"

                // Disable confirm if all activities removed
                binding.btnConfirm.isEnabled = activities.size == 3
                binding.btnConfirm.alpha = if (activities.size == 3) 1f else 0.5f
            }
        }
    }

    private fun setupButtons() {
        binding.btnConfirm.setOnClickListener {
            if (viewModel.isEditMode) {
                // Edit flow: return to the Routine tab instead of entering
                // the onboarding-only health flow
                if (!findNavController().popBackStack(R.id.routineHomeFragment, false)) {
                    findNavController().navigate(R.id.action_routineSequencing_to_healthEducation)
                }
            } else {
                val userId = com.noctra.app.utils.UserSession.getUserId(requireContext())
                if (userId != null) {
                    viewModel.updateStep(userId, 3)
                    viewModel.saveDraft(userId)
                }
                findNavController().navigate(R.id.action_routineSequencing_to_healthEducation)
            }
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
