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
import com.noctra.app.databinding.FragmentActivityLibraryBinding
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

        setupAdapter()
        setupButtons()
        loadActivities()
        observeSelection()
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
        val min = OnboardingViewModel.MIN_SELECTABLE_ACTIVITIES
        val max = OnboardingViewModel.MAX_SELECTABLE_ACTIVITIES

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedActivities.collect { selected ->
                val count = selected.size
                val remainingToMin = min - count

                // Update banner text
                binding.tvSelectionCount.text = when {
                    count == 0 -> "0 selected — pick $min to $max"
                    count < min -> "$count selected — add $remainingToMin more"
                    count in min..max -> "$count selected ✓"
                    else -> "$count selected"
                }
                binding.tvSelectionHint.text = when {
                    count < min -> "Select at least $min activities"
                    count in min..max -> "Ready to continue! (up to $max total)"
                    else -> "Maximum is $max activities"
                }

                // Update continue button
                val ready = count in min..max
                binding.btnContinue.isEnabled = ready
                binding.btnContinue.alpha = if (ready) 1f else 0.5f
                binding.btnContinue.text = if (ready)
                    "Continue"
                else
                    "Select $min to $max activities"

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