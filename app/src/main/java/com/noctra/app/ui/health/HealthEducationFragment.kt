package com.noctra.app.ui.health

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.noctra.app.R
import com.noctra.app.databinding.FragmentHealthEducationBinding

class HealthEducationFragment : Fragment() {

    private var _binding: FragmentHealthEducationBinding? = null
    private val binding get() = _binding!!

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
            findNavController().navigate(R.id.action_healthEducation_to_healthGrant)
        }

        binding.btnSkip.setOnClickListener {
            showSkipConfirmation()
        }
    }

    private fun showSkipConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.health_grant_skip_dialog_title)
            .setMessage(R.string.health_grant_skip_dialog_message)
            .setNegativeButton(R.string.health_grant_skip_dialog_go_back, null)
            .setPositiveButton(R.string.health_grant_skip_dialog_continue) { _, _ ->
                // Skip the health flow entirely — proceed to the onboarding summary
                findNavController().navigate(R.id.action_healthEducation_to_onboardingSummary)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
