package com.noctra.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.materialswitch.MaterialSwitch
import com.noctra.app.BuildConfig
import com.noctra.app.R
import com.noctra.app.ui.common.BedtimePickerBottomSheet
import com.noctra.app.utils.NotificationPreferences
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()

        // Top bar
        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            findNavController().navigateUp()
        }

        // Account Management values
        val displayName = view.findViewById<TextView>(R.id.value_display_name)
        val email = view.findViewById<TextView>(R.id.value_email)

        // Routine Management
        val bedtimePill = view.findViewById<TextView>(R.id.btn_bedtime_pill)

        // App Version
        val appVersion = view.findViewById<TextView>(R.id.value_app_version)
        appVersion.text = BuildConfig.VERSION_NAME

        if (BuildConfig.DEBUG) {
            appVersion.setOnLongClickListener {
                findNavController().navigate(R.id.debug_graph)
                true
            }
        }

        // Notification switches
        val windDownSwitch = view.findViewById<MaterialSwitch>(R.id.switch_wind_down)
        val morningSwitch = view.findViewById<MaterialSwitch>(R.id.switch_morning_score)

        windDownSwitch.isChecked = NotificationPreferences.isWindDownEnabled(ctx)
        morningSwitch.isChecked = NotificationPreferences.isMorningScoreEnabled(ctx)

        windDownSwitch.setOnCheckedChangeListener { _, isChecked ->
            NotificationPreferences.setWindDownEnabled(ctx, isChecked)
        }
        morningSwitch.setOnCheckedChangeListener { _, isChecked ->
            NotificationPreferences.setMorningScoreEnabled(ctx, isChecked)
        }

        // Privacy Policy + Terms of Use (placeholder URLs)
        view.findViewById<View>(R.id.row_privacy_policy).setOnClickListener {
            openUrl("https://example.com/privacy")
        }
        view.findViewById<View>(R.id.row_terms_of_use).setOnClickListener {
            openUrl("https://example.com/terms")
        }

        // Observe profile data
        lifecycleScope.launch {
            viewModel.profileState.collect { state ->
                displayName.text = state.displayName
                email.text = state.email ?: "(demo mode)"
                bedtimePill.text = formatBedtime(state.targetBedtime)
            }
        }

        // Bedtime picker
        bedtimePill.setOnClickListener {
            showBedtimePicker(viewModel.profileState.value.targetBedtime)
        }

        viewModel.loadProfile(ctx)
    }

    private fun formatBedtime(raw: String?): String {
        if (raw.isNullOrBlank()) return "Not set"
        return try {
            val time = LocalTime.parse(raw)  // expects "HH:mm" or "HH:mm:ss"
            time.format(DateTimeFormatter.ofPattern("h:mm a"))
        } catch (e: Exception) {
            raw
        }
    }

    private fun showBedtimePicker(current: String?) {
        BedtimePickerBottomSheet()
            .configure(currentBedtime = current) { newBedtime ->
                viewModel.updateTargetBedtime(requireContext(), newBedtime)
            }
            .show(parentFragmentManager, "bedtime_picker")
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Cannot open link",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}