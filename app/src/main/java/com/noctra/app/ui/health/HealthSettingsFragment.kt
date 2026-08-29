package com.noctra.app.ui.health

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.noctra.app.R
import com.noctra.app.databinding.FragmentHealthSettingsBinding
import com.noctra.app.utils.HealthConnectPermissionHelper
import kotlinx.coroutines.launch

/**
 * Read-only permission status + a single deep link into Health Connect's own
 * settings, where granting AND revoking both happen. Noctra never changes
 * permissions from this screen — HC is the single source of truth.
 */
class HealthSettingsFragment : Fragment() {

    private var _binding: FragmentHealthSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHealthSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnManagePermissions.setOnClickListener { openHealthConnectSettings() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val b = _binding ?: return@launch

            if (!HealthConnectPermissionHelper.isAvailable(context)) {
                setRow(b.tvSleepStatus, granted = false)
                setRow(b.tvHrStatus, granted = false)
                b.tvOverallStatus.text =
                    getString(R.string.health_settings_status_unavailable)
                b.btnManagePermissions.isEnabled = false
                return@launch
            }

            b.btnManagePermissions.isEnabled = true
            b.tvOverallStatus.text =
                getString(R.string.health_settings_status_disconnected)

            val client = HealthConnectClient.getOrCreate(context)
            val granted = HealthConnectPermissionHelper.getGrantedPermissions(client)
            setRow(
                b.tvSleepStatus,
                granted = HealthConnectPermissionHelper.hasSleepPermission(granted)
            )
            setRow(
                b.tvHrStatus,
                granted = HealthConnectPermissionHelper.hasHeartRatePermission(granted)
            )
        }
    }

    private fun setRow(view: android.widget.TextView, granted: Boolean) {
        if (granted) {
            view.text = getString(R.string.health_settings_status_granted)
            view.setTextColor(resources.getColor(R.color.quality_good, null))
        } else {
            view.text = getString(R.string.health_settings_status_not_shared)
            view.setTextColor(resources.getColor(R.color.noctra_text_muted, null))
        }
    }

    private fun openHealthConnectSettings() {
        startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
