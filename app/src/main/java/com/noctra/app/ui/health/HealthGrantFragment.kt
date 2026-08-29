package com.noctra.app.ui.health

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.health.connect.client.PermissionController
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.noctra.app.R
import com.noctra.app.databinding.FragmentHealthGrantBinding
import com.noctra.app.utils.HealthConnectPermissionHelper

class HealthGrantFragment : Fragment() {

    private var _binding: FragmentHealthGrantBinding? = null
    private val binding get() = _binding!!

    /**
     * Launches Health Connect's system permission screen. The result is the set
     * of permissions the user actually granted (may be partial or empty).
     */
    private val requestPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted: Set<String> ->
        handlePermissionResult(granted)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHealthGrantBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnGrant.setOnClickListener {
            when {
                HealthConnectPermissionHelper.isAvailable(requireContext()) ->
                    launchPermissionRequest()

                // Android < 14 without the Health Connect module:
                // the button becomes the install action until HC is present
                HealthConnectPermissionHelper.needsInstall(requireContext()) ->
                    startActivity(HealthConnectPermissionHelper.getInstallIntent())
            }
        }

        binding.btnContinue.setOnClickListener {
            advanceAfterHealthFlow()
        }

        binding.btnSkip.setOnClickListener { showSkipConfirmation() }
    }

    override fun onResume() {
        super.onResume()
        refreshUiState()
    }

    private fun refreshUiState() {
        val context = requireContext()

        when {
            HealthConnectPermissionHelper.isAvailable(context) -> {
                binding.btnGrant.isEnabled = true
                binding.btnGrant.text = getString(R.string.health_grant_button)
                checkPermissionStatus()
            }

            HealthConnectPermissionHelper.needsInstall(context) -> {
                binding.btnGrant.isEnabled = true
                binding.btnGrant.text = getString(R.string.health_grant_install)
                showNotAllGranted()
            }

            else -> {
                // SDK_UNAVAILABLE: device can't run Health Connect at all
                binding.btnGrant.isEnabled = false
                binding.btnGrant.text = getString(R.string.health_grant_unsupported)
                showNotAllGranted()
            }
        }
    }

    private fun checkPermissionStatus() {
        // Check current permission status and update UI accordingly
        // Note: We can't read actual permissions without launching a request,
        // but we can track state from previous requests or initial load
        val hasAllPermissions = checkIfAllPermissionsGranted()

        if (hasAllPermissions) {
            showAllGranted()
        } else {
            showNotAllGranted()
        }
    }

    private fun checkIfAllPermissionsGranted(): Boolean {
        // This is a simplified check - in practice you'd need to use
        // Health Connect's API to check granted permissions
        // For now, we'll track state via the permission request result
        return arguments?.getBoolean(ARG_ALL_GRANTED, false) ?: false
    }

    private fun showAllGranted() {
        // Update badges to "Granted"
        binding.badgeSleep.text = getString(R.string.badge_granted)
        binding.badgeSleep.setBackgroundResource(R.drawable.badge_granted)
        binding.badgeSleep.setTextColor(resources.getColor(R.color.granted_green, null))

        binding.badgeHeartRate.text = getString(R.string.badge_granted)
        binding.badgeHeartRate.setBackgroundResource(R.drawable.badge_granted)
        binding.badgeHeartRate.setTextColor(resources.getColor(R.color.granted_green, null))

        // Show success card
        binding.cardSuccess.visibility = View.VISIBLE

        // Show Continue button, hide Grant + Skip
        binding.btnGrant.visibility = View.GONE
        binding.btnSkip.visibility = View.GONE
        binding.btnContinue.visibility = View.VISIBLE
    }

    private fun showNotAllGranted() {
        // Update badges to "Required"
        binding.badgeSleep.text = getString(R.string.badge_required)
        binding.badgeSleep.setBackgroundResource(R.drawable.badge_required)
        binding.badgeSleep.setTextColor(resources.getColor(R.color.required_red, null))

        binding.badgeHeartRate.text = getString(R.string.badge_required)
        binding.badgeHeartRate.setBackgroundResource(R.drawable.badge_required)
        binding.badgeHeartRate.setTextColor(resources.getColor(R.color.required_red, null))

        // Hide success card
        binding.cardSuccess.visibility = View.GONE

        // Show Grant + Skip buttons, hide Continue
        binding.btnGrant.visibility = View.VISIBLE
        binding.btnSkip.visibility = View.VISIBLE
        binding.btnContinue.visibility = View.GONE
    }

    private fun launchPermissionRequest() {
        requestPermissions.launch(
            setOf(
                HealthConnectPermissionHelper.READ_SLEEP_PERMISSION,
                HealthConnectPermissionHelper.READ_HEART_RATE_PERMISSION
            )
        )
    }

    /**
     * After permission request, update UI based on what was granted.
     * Don't auto-advance - stay on screen and show the updated state.
     */
    private fun handlePermissionResult(granted: Set<String>) {
        if (granted.isNotEmpty()) {
            // Store that we have all permissions and refresh UI
            arguments?.putBoolean(ARG_ALL_GRANTED, true)
            refreshUiState()
        }
        // If empty (Don't allow), user is back on this screen - can retry or skip
    }

    private fun showSkipConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.health_grant_skip_dialog_title)
            .setMessage(R.string.health_grant_skip_dialog_message)
            .setNegativeButton(R.string.health_grant_skip_dialog_go_back, null)
            .setPositiveButton(R.string.health_grant_skip_dialog_continue) { _, _ ->
                advanceAfterHealthFlow()
            }
            .show()
    }

    /**
     * Grant success or Skip-continue both advance to the onboarding summary.
     * (These screens live only in onboarding_graph; post-onboarding permission
     * management happens via Health Settings' deep link into Health Connect.)
     */
    private fun advanceAfterHealthFlow() {
        findNavController().navigate(R.id.action_healthGrant_to_onboardingSummary)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ALL_GRANTED = "all_granted"
    }
}
