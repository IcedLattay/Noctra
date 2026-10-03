package com.noctra.app.ui.routine.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.noctra.app.R
import com.noctra.app.databinding.FragmentRoutineSequencingBinding
import com.noctra.app.ui.routine.home.ActivityInfoDialogFragment
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.launch

class RoutineSequencingFragment : Fragment() {

    private var _binding: FragmentRoutineSequencingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by navGraphViewModels(R.id.nav_graph)
    private lateinit var adapter: RoutineSequencingAdapter
    private lateinit var touchHelper: ItemTouchHelper
    private var confirmText: CharSequence = "Confirm" // original button text from the layout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoutineSequencingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        confirmText = binding.btnConfirm.text

        // Debug preview: look freely, save nothing. The active routine is
        // loaded read-only and the confirm button is gone, so no write
        // path is reachable. Reorders stay in ViewModel memory only and
        // are discarded with the preview (see onDestroyView).
        val previewMode = arguments?.getBoolean("previewMode") ?: false
        if (previewMode) {
            binding.btnConfirm.visibility = View.GONE
        }

        setupRecyclerView()
        observeActivities()
        observeSaveState()
        setupButtons()

        if (previewMode) {
            val userId = com.noctra.app.utils.UserSession.getUserId(requireContext())
            if (userId != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.previewActiveRoutine(userId)
                }
            }
            return
        }

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

            // Both drag paths live: handle touch-down AND row long-press
            // (no info dialog on this screen to compete with) — so the
            // default long-press-drag stays enabled: no override.

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
                // Re-draw the step numbers (1, 2, 3) after a drop — moving
                // rows doesn't rebind them, so they'd show the old order.
                recyclerView.post { adapter.notifyItemRangeChanged(0, adapter.itemCount) }
            }
        }

        touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(binding.rvSequence)
        adapter.touchHelper = touchHelper

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

    /**
     * Edit mode only: reacts to the save result.
     *   Saving -> button disabled, "Saving..."
     *   Saved  -> toast, clear edit data, go back to the Routine tab
     *   Error  -> toast, re-enable button so the user can retry
     */
    private fun observeSaveState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.saveState.collect { state ->
                when (state) {
                    is OnboardingViewModel.SaveState.Idle -> {
                        binding.btnConfirm.text = confirmText
                    }
                    is OnboardingViewModel.SaveState.Saving -> {
                        binding.btnConfirm.isEnabled = false
                        binding.btnConfirm.alpha = 0.5f
                        binding.btnConfirm.text = "Saving..."
                    }
                    is OnboardingViewModel.SaveState.Saved -> {
                        Toast.makeText(requireContext(), "Routine updated", Toast.LENGTH_SHORT).show()
                        viewModel.resetEditSession()
                        goBackToRoutineHome()
                    }
                    is OnboardingViewModel.SaveState.Error -> {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        viewModel.resetSaveState()   // Idle restores the button text
                        binding.btnConfirm.isEnabled = true
                        binding.btnConfirm.alpha = 1f
                    }
                }
            }
        }
    }

    private fun setupButtons() {
        binding.btnConfirm.setOnClickListener {
            val userId = UserSession.getUserId(requireContext())

            if (viewModel.isEditMode) {
                // Edit mode saves and returns (never continues into the
                // Health Connect onboarding screens).
                if (userId == null) {
                    Toast.makeText(requireContext(), "Please log in again.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                viewModel.saveEditedRoutine(userId)
            } else {
                if (userId != null) {
                    viewModel.updateStep(userId, 3)
                    viewModel.saveDraft(userId)
                }
                findNavController().navigate(R.id.action_routineSequencing_to_healthEducation)
            }
        }

    }

    /**
     * Routine Home is still on the back stack (Home -> Library -> Sequencing),
     * so pop back to it. RoutineHomeFragment.onResume() calls refresh(),
     * which re-reads the new active routine from Supabase.
     * Fallback: the global action, in case Home isn't on the stack.
     */
    private fun goBackToRoutineHome() {
        val popped = findNavController().popBackStack(R.id.routineHomeFragment, false)
        if (!popped) {
            findNavController().navigate(R.id.action_global_routineHomeFragment)
        }
    }

    override fun onDestroyView() {
        // Preview mutated only ViewModel memory (reorders); discard it so
        // the next real flow loads fresh instead of preview leftovers.
        if (arguments?.getBoolean("previewMode") == true) {
            viewModel.resetEditSession()
        }
        super.onDestroyView()
        _binding = null
    }
}