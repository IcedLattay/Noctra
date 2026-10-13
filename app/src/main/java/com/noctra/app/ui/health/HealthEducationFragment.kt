package com.noctra.app.ui.health

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.noctra.app.R
import com.noctra.app.databinding.FragmentHealthEducationBinding
import com.noctra.app.ui.routine.onboarding.OnboardingViewModel
import com.noctra.app.utils.UserSession

class HealthEducationFragment : Fragment() {

    private var _binding: FragmentHealthEducationBinding? = null
    private val binding get() = _binding!!
    private val onboardingViewModel: OnboardingViewModel by navGraphViewModels(R.id.nav_graph)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHealthEducationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnConnect.setOnClickListener {
            saveStep(4)
            findNavController().navigate(R.id.action_healthEducation_to_healthGrant)
        }

        binding.btnSkip.setOnClickListener {
            showSkipConfirmation()
        }
    }

    private fun showSkipConfirmation() {
        SkipHealthBottomSheet().apply {
            onContinue = {
                // Skip the health flow entirely — proceed to the onboarding summary
                saveStep(5)
                findNavController().navigate(R.id.action_healthEducation_to_onboardingSummary)
            }
        }.show(parentFragmentManager, "skip_health")
    }

    private fun saveStep(step: Int) {
        UserSession.getUserId(requireContext())?.let { userId ->
            onboardingViewModel.updateStep(userId, step)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
