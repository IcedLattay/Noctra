package com.noctra.app.ui.profile

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.materialswitch.MaterialSwitch
import com.noctra.app.BuildConfig
import com.noctra.app.R
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
        view.findViewById<TextView>(R.id.value_app_version).text =
            BuildConfig.VERSION_NAME

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
        val defaultTime = try {
            current?.let { LocalTime.parse(it) }
        } catch (e: Exception) { null } ?: LocalTime.of(22, 0)

        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                if (isInBedtimeRange(hour)) {
                    val formatted = String.format("%02d:%02d:00", hour, minute)
                    viewModel.updateTargetBedtime(requireContext(), formatted)
                } else {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Bedtime must be between 8:00 PM and 2:00 AM",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            },
            defaultTime.hour,
            defaultTime.minute,
            false  // false = 12-hour clock
        ).show()
    }

    /**
     * Bedtime is valid if it's between 20:00 (8 PM) and 02:00 (2 AM).
     * So either hour >= 20 OR hour <= 2.
     */
    private fun isInBedtimeRange(hour: Int): Boolean =
        hour >= 20 || hour <= 2

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                requireContext(),
                "Cannot open link",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}