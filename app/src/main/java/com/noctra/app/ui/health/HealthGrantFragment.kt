package com.noctra.app.ui.health

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import androidx.navigation.navGraphViewModels
import com.noctra.app.R
import com.noctra.app.databinding.FragmentHealthGrantBinding
import com.noctra.app.ui.routine.onboarding.OnboardingViewModel
import com.noctra.app.utils.HealthConnectPermissionHelper
import com.noctra.app.utils.UserSession

class HealthGrantFragment : Fragment() {

    private var _binding: FragmentHealthGrantBinding? = null
    private val binding get() = _binding!!
    private val onboardingViewModel: OnboardingViewModel by navGraphViewModels(R.id.nav_graph)

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
                HealthConnectPermissionHelper.isAvailable(requireContext()) -> {
                    setGrantLoading(true)
                    launchPermissionRequest()
                }

                // Android < 14 without the Health Connect module:
                // the button becomes the install action until HC is present
                HealthConnectPermissionHelper.needsInstall(requireContext()) ->
                    startActivity(HealthConnectPermissionHelper.getInstallIntent())
            }
        }

        binding.btnSkip.setOnClickListener { showSkipConfirmation() }

        // If everything is already granted, this screen has no purpose —
        // skip straight to Summary (once, on creation only)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val context = requireContext()
                if (!HealthConnectPermissionHelper.isAvailable(context)) return@launch
                val client = androidx.health.connect.client.HealthConnectClient
                    .getOrCreate(context)
                val granted = HealthConnectPermissionHelper.getGrantedPermissions(client)
                if (HealthConnectPermissionHelper.hasSleepPermission(granted) &&
                    HealthConnectPermissionHelper.hasHeartRatePermission(granted) &&
                    isAdded
                ) {
                    advanceAfterHealthFlow()
                }
            } catch (e: Exception) {
                // Stay put on failure — user can Grant or Skip manually
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUiState()
    }

    // Loading state: dimmed + "Opening…" text + trailing spinner. The
    // button deliberately stays enabled — re-tapping a stalled Health
    // Connect request just re-opens it, so it can never wedge disabled.
    private fun setGrantLoading(loading: Boolean) {
        binding.btnGrant.alpha = if (loading) 0.6f else 1f
        binding.progressGrant.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) {
            binding.btnGrant.text = getString(R.string.health_grant_opening)
        }
    }

    // One-shot grant flow: the single popup result (all, partial, or
    // denied) is final for onboarding — advance immediately. No status
    // tracking, no re-requests, so the re-prompt suppression quirk can't
    // bite. Post-onboarding changes go through Health Settings.
    private fun refreshUiState() {
        val context = requireContext()
        setGrantLoading(false)

        when {
            HealthConnectPermissionHelper.isAvailable(context) -> {
                binding.btnGrant.isEnabled = true
                binding.btnGrant.text = getString(R.string.health_grant_button)
            }

            HealthConnectPermissionHelper.needsInstall(context) -> {
                binding.btnGrant.isEnabled = true
                binding.btnGrant.text = getString(R.string.health_grant_install)
            }

            else -> {
                // SDK_UNAVAILABLE: device can't run Health Connect at all
                binding.btnGrant.isEnabled = false
                binding.btnGrant.text = getString(R.string.health_grant_unsupported)
            }
        }
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
     * The single popup result — all, partial, or denied — is final for
     * onboarding. Advance immediately; the sync pipeline redistributes
     * scoring around whatever is actually granted.
     */
    private fun handlePermissionResult(granted: Set<String>) {
        android.util.Log.d("HealthGrant", "HC result, granted=$granted")
        advanceAfterHealthFlow()
    }

    private fun showSkipConfirmation() {
        SkipHealthBottomSheet().apply {
            onContinue = { advanceAfterHealthFlow() }
        }.show(parentFragmentManager, "skip_health")
    }

    /**
     * Grant success or Skip-continue both advance to the onboarding summary.
     * (These screens live only in onboarding_graph; post-onboarding permission
     * management happens via Health Settings' deep link into Health Connect.)
     */
    private fun advanceAfterHealthFlow() {
        UserSession.getUserId(requireContext())?.let { userId ->
            onboardingViewModel.updateStep(userId, 5)
        }
        findNavController().navigate(R.id.action_healthGrant_to_onboardingSummary)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
