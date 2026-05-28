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
    }

    private fun setupRecyclerView() {
        adapter = RoutineSequencingAdapter(
            onRemove = { index -> viewModel.removeActivityAt(index) }
        )

        // ItemTouchHelper for drag-to-reorder
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                viewModel.reorderActivities(from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // No swipe action
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

        touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(binding.rvSequence)

        // Give adapter a reference so drag handle works
        adapter.touchHelper = touchHelper

        binding.rvSequence.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSequence.adapter = adapter
    }

    private fun observeActivities() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.orderedActivities.collect { activities ->
                // submitList needs a new list instance to detect changes
                adapter.submitList(activities.toList())

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
            findNavController().navigate(R.id.action_routineSequencing_to_onboardingSummary)
        }

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}