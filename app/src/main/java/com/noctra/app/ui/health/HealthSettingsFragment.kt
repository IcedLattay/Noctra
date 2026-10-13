package com.noctra.app.ui.health

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.noctra.app.R
import com.noctra.app.utils.HealthConnectPermissionHelper
import kotlinx.coroutines.launch

class HealthSettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_health_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btn_back).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<View>(R.id.btn_open_health_connect).setOnClickListener {
            openHealthConnectSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val context = requireContext()
        val view = view ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            if (!HealthConnectPermissionHelper.isAvailable(context)) {
                updateBadge(view.findViewById(R.id.badge_sleep), granted = false)
                updateBadge(view.findViewById(R.id.badge_heart_rate), granted = false)
                updateBadge(view.findViewById(R.id.badge_sleep_stages), granted = false)
                return@launch
            }

            val client = HealthConnectClient.getOrCreate(context)
            val granted = HealthConnectPermissionHelper.getGrantedPermissions(client)
            val hasSleep = HealthConnectPermissionHelper.hasSleepPermission(granted)
            val hasHeartRate = HealthConnectPermissionHelper.hasHeartRatePermission(granted)

            updateBadge(view.findViewById(R.id.badge_sleep), granted = hasSleep)
            updateBadge(view.findViewById(R.id.badge_heart_rate), granted = hasHeartRate)
            updateBadge(view.findViewById(R.id.badge_sleep_stages), granted = hasSleep)
        }
    }

    private fun updateBadge(badge: TextView, granted: Boolean) {
        if (granted) {
            badge.text = "Granted"
            badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.granted_green))
            badge.setBackgroundResource(R.drawable.bg_badge_green)
        } else {
            badge.text = "Not Granted"
            badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.pill_text_grey))
            badge.setBackgroundResource(R.drawable.bg_badge_grey)
        }
    }

    private fun openHealthConnectSettings() {
        startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
    }
}
