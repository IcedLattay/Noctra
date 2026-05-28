package com.noctra.app.ui.routine.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.noctra.app.R
import com.noctra.app.data.repository.RoutineRepository
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.databinding.FragmentActivityLibraryBinding
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.launch

class ActivityLibraryFragment : Fragment() {

    private var _binding: FragmentActivityLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by navGraphViewModels(R.id.nav_graph)
    private val repository = RoutineRepository()
    private lateinit var adapter: ActivityGridAdapter

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
        }

        setupAdapter()
        setupButtons()
        loadActivities()
        observeSelection()
    }

    private fun setupEditMode() {
        // If we are in edit mode and the VM is empty (first entry), 
        // we should pre-load the current routine.
        if (viewModel.selectedActivities.value.isEmpty()) {
            val userId = UserSession.getUserId(requireContext())
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

    private fun setupAdapter() {
        adapter = ActivityGridAdapter { activity ->
            viewModel.toggleActivity(activity)
        }
        binding.rvActivities.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvActivities.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnContinue.setOnClickListener {
            viewModel.confirmSelectionAndProceed()
            findNavController().navigate(R.id.action_activityLibrary_to_routineSequencing)
        }

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // Start disabled
        binding.btnContinue.isEnabled = false
        binding.btnContinue.alpha = 0.5f
    }

    private fun loadActivities() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val activities = repository.getActivityLibrary()
                adapter.submitList(activities)
                binding.tvError.visibility = View.GONE
            } catch (e: Exception) {
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

                // Update continue button
                val ready = count == 3
                binding.btnContinue.isEnabled = ready
                binding.btnContinue.alpha = if (ready) 1f else 0.5f
                binding.btnContinue.text = if (ready)
                    "Continue"
                else
                    "Select exactly 3 activities"

                // Update adapter selection state
                adapter.setSelected(selected.map { it.activityId }.toSet())
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}