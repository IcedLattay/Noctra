package com.noctra.app.ui.routine.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.noctra.app.R
import com.noctra.app.data.repository.RoutineRepository
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.databinding.FragmentActivityLibraryBinding
import com.noctra.app.ui.routine.home.ActivityInfoDialogFragment
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.launch

/**
 * FIXED: setupAdapter() now uses a SpanSizeLookup so the last item spans
 * the full row width when the item count is odd (9 activities -> last row
 * has 1 card instead of 2). Same pattern already used in
 * RoutineHomeFragment's ActivityCardAdapter setup. Without this, the last
 * item (Bedtime To-Do List Writing, sort_order 9) was not rendering at all —
 * a GridLayoutManager + wrap_content measurement issue with an incomplete
 * final row, not a data-loading problem (confirmed the DB row itself is
 * valid and Logcat showed no fetch/decode errors).
 *
 * FIXED (edit-routine save bug): backing out of edit mode without saving now
 * clears the edit data (viewModel.resetEditSession()), so the next
 * "Edit Routine" reloads the real routine from the DB instead of showing
 * leftover unsaved changes.
 */
class ActivityLibraryFragment : Fragment() {

    private var _binding: FragmentActivityLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by navGraphViewModels(R.id.nav_graph)
    private val repository = RoutineRepository()
    private lateinit var adapter: ActivityGridAdapter
    private lateinit var gridLayoutManager: GridLayoutManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActivityLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editMode = arguments?.getBoolean("editMode") ?: false
        viewModel.isEditMode = editMode
        if (editMode) {
            setupEditMode()
            setupEditModeBackPress()
        }

        // Debug preview: look freely, save nothing. The continue button
        // is gone (every write path lives behind it); taps only rearrange
        // ViewModel memory, discarded on exit (see onDestroyView).
        val previewMode = arguments?.getBoolean("previewMode") ?: false
        if (previewMode) {
            binding.btnContinue.visibility = View.GONE
        }

        setupAdapter()
        setupButtons()
        loadActivities()
        observeSelection()

        // Fresh-process resume: restore the saved draft unless edit mode
        // preloaded the active routine (or state already exists)
        if (!viewModel.isEditMode) {
            val userId = UserSession.getUserId(requireContext())
            if (userId != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.restoreDraftIfEmpty(userId)
                }
            }
        }
    }

    private fun setupEditMode() {
        // If we are in edit mode and the VM is empty (first entry),
        // we should pre-load the current routine.
        if (viewModel.selectedActivities.value.isEmpty()) {
            val userId = UserSession.getUserId(requireContext()) ?: return
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val activeRoutine = repository.getActiveRoutine(userId)
                    if (activeRoutine != null) {
                        val profile = UserProfileRepository().getOrCreateProfile(userId)
                        val entries = repository.parseActivitySequence(activeRoutine.activitySequence)
                        val activities = repository.hydrateActivitySequence(entries)

                        viewModel.loadExistingRoutine(
                            activities = activities,
                            bedtime = profile.targetBedtime ?: "22:00"
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ActivityLibrary", "Failed to load existing routine", e)
                }
            }
        }
    }

    /** Phone back button in edit mode = cancel edit, discard changes. */
    private fun setupEditModeBackPress() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    cancelEditAndGoBack()
                }
            }
        )
    }

    private fun cancelEditAndGoBack() {
        viewModel.resetEditSession()
        findNavController().popBackStack()
    }

    private fun setupAdapter() {
        adapter = ActivityGridAdapter(
            onActivityClick = { activity -> viewModel.toggleActivity(activity) },
            onInfoClick = { activity -> showActivityDetails(activity) }
        )
        gridLayoutManager = GridLayoutManager(requireContext(), 2).apply {
            // Last item spans the full row when the count is odd
            // (10 activities -> last row would have 1 card instead of 2)
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    val count = adapter.itemCount
                    return if (count % 2 == 1 && position == count - 1) 2 else 1
                }
            }
        }
        binding.rvActivities.layoutManager = gridLayoutManager
        binding.rvActivities.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnContinue.setOnClickListener {
            viewModel.confirmSelectionAndProceed()

            if (!viewModel.isEditMode) {
                val userId = UserSession.getUserId(requireContext())
                if (userId != null) {
                    viewModel.updateStep(userId, 2)
                    viewModel.saveDraft(userId)
                }
            }

            findNavController().navigate(R.id.action_activityLibrary_to_routineSequencing)
        }

        binding.btnBack.setOnClickListener {
            if (viewModel.isEditMode) {
                cancelEditAndGoBack()
            } else {
                findNavController().popBackStack()
            }
        }
        // One-way onboarding: no back button outside edit mode.
        binding.btnBack.visibility =
            if (viewModel.isEditMode) View.VISIBLE else View.GONE

        // Start disabled
        binding.btnContinue.isEnabled = false
        binding.btnContinue.alpha = 0.5f
    }

    private fun loadActivities() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val activities = repository.getActivityLibrary()
                android.util.Log.d("ActivityLibraryDebug", "Fetched ${activities.size} activities: ${activities.map { it.label }}")
                adapter.submitList(activities) {
                    // Runs after DiffUtil finishes applying the new list.
                    gridLayoutManager.spanSizeLookup.invalidateSpanIndexCache()
                    // RecyclerView (wrap_content, inside a ScrollView) can get
                    // stuck at whatever height it was first measured at,
                    // since submitList()'s internal requestLayout() doesn't
                    // always propagate up through the ScrollView/LinearLayout
                    // chain to force them to grow for the new content height.
                    // Force it explicitly.
                    binding.rvActivities.requestLayout()
                    binding.rvActivities.post {
                        binding.rvActivities.requestLayout()
                        (binding.rvActivities.parent as? View)?.requestLayout()
                    }
                }
                binding.tvError.visibility = View.GONE
            } catch (e: Exception) {
                android.util.Log.e("ActivityLibrary", "getActivityLibrary failed", e)
                binding.tvError.visibility = View.VISIBLE
            }
        }
    }

    private fun observeSelection() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedActivities.collect { selected ->
                val count = selected.size
                val remaining = 3 - count

                // Update banner text
                binding.tvSelectionCount.text = when {
                    count == 0 -> "0 selected — add 3 more"
                    remaining > 0 -> "$count selected — add $remaining more"
                    else -> "3 selected ✓"
                }
                binding.tvSelectionHint.text = when {
                    count == 3 -> "Ready to continue!"
                    else -> "Exactly 3 activities required"
                }

                // Update 3-segment progress bar
                val segments = listOf(
                    binding.progressSegment1,
                    binding.progressSegment2,
                    binding.progressSegment3
                )
                segments.forEachIndexed { index, segment ->
                    segment.setBackgroundResource(
                        if (index < count) R.drawable.bg_progress_segment_active
                        else R.drawable.bg_progress_segment_inactive
                    )
                }

                // Update continue button
                val ready = count == 3
                binding.btnContinue.isEnabled = ready
                binding.btnContinue.alpha = if (ready) 1f else 0.5f
                binding.btnContinue.text = "Continue"

                // Update adapter selection state
                adapter.setSelected(selected.map { it.activityId }.toSet())
            }
        }
    }

    private fun showActivityDetails(activity: com.noctra.app.data.model.Activity) {
        ActivityInfoDialogFragment.show(childFragmentManager, activity)
    }

    override fun onDestroyView() {
        // Preview mutated only ViewModel memory (taps); discard it so the
        // next real flow loads fresh instead of preview leftovers.
        if (arguments?.getBoolean("previewMode") == true) {
            viewModel.resetEditSession()
        }
        super.onDestroyView()
        _binding = null
    }
}