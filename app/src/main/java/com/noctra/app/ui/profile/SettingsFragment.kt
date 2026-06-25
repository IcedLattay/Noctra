package com.noctra.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.materialswitch.MaterialSwitch
import com.noctra.app.BuildConfig
import com.noctra.app.R
import com.noctra.app.ui.common.BedtimePickerBottomSheet
import com.noctra.app.utils.NotificationPreferences
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val viewModel: SettingsViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(requireContext(), "Notifications enabled", Toast.LENGTH_SHORT).show()
        }
    }

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
            // Immediately apply the change: schedule or cancel the alarm
            lifecycleScope.launch {
                com.noctra.app.workers.WindDownNotificationScheduler.scheduleNext(ctx)
            }
        }
        morningSwitch.setOnCheckedChangeListener { _, isChecked ->
            NotificationPreferences.setMorningScoreEnabled(ctx, isChecked)
        }

        // Notification Permissions row (Facebook style)
        view.findViewById<View>(R.id.row_notification_permissions).setOnClickListener {
            requestNotificationPermission()
        }

        // Alarm Permissions row
        view.findViewById<View>(R.id.row_alarm_permissions).setOnClickListener {
            requestAlarmPermissionManually()
        }

        // Privacy Policy + Terms of Use (placeholder URLs)
        view.findViewById<View>(R.id.row_privacy_policy).setOnClickListener {
            openUrl("https://example.com/privacy")
        }
        view.findViewById<View>(R.id.row_terms_of_use).setOnClickListener {
            openUrl("https://example.com/terms")
        }

        // Sign Out button at the bottom
        view.findViewById<View>(R.id.btn_sign_out).setOnClickListener {
            showLogoutConfirmation()
        }

        // Change Password
        view.findViewById<View>(R.id.row_change_password).setOnClickListener {
            Toast.makeText(ctx, "Change Password feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Delete Account
        view.findViewById<View>(R.id.row_delete_account).setOnClickListener {
            showDeleteAccountConfirmation()
        }

        // Observe profile data
        lifecycleScope.launch {
            viewModel.profileState.collect { state ->
                displayName.text = state.displayName
                email.text = state.email ?: com.noctra.app.data.supabase.SupabaseClient.client.auth.currentUserOrNull()?.email ?: "(demo mode)"
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

    private fun showLogoutConfirmation() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_alert, null)
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.dialog_title).apply {
            text = "Logout?"
            setTextColor(ContextCompat.getColor(context, R.color.noctra_purple_dark))
        }
        dialogView.findViewById<TextView>(R.id.dialog_message).text = "Are you sure you want to logout of your account?"
        
        val btnPositive = dialogView.findViewById<Button>(R.id.btn_positive)
        btnPositive.text = "Yes, Logout"
        btnPositive.setOnClickListener {
            dialog.dismiss()
            performLogout()
        }

        dialogView.findViewById<Button>(R.id.btn_negative).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        
        // Ensure the dialog is centered and respects the custom width
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setGravity(android.view.Gravity.CENTER)
    }

    private fun showDeleteAccountConfirmation() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_alert, null)
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.dialog_title).text = "Delete Account?"
        dialogView.findViewById<TextView>(R.id.dialog_message).text = "This action cannot be undone. All your data will be permanently deleted."
        
        val btnPositive = dialogView.findViewById<Button>(R.id.btn_positive)
        btnPositive.text = "Delete Account"
        btnPositive.setOnClickListener {
            // Non-functional for now as requested
            Toast.makeText(requireContext(), "Account deletion is disabled for safety", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_negative).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        // Ensure the dialog is centered and respects the custom width
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setGravity(android.view.Gravity.CENTER)
    }

    private fun performLogout() {
        lifecycleScope.launch {
            com.noctra.app.data.supabase.SupabaseClient.client.auth.signOut()
            // Navigate specifically to the Auth Group and clear the backstack
            findNavController().navigate(R.id.auth_graph, null,
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.nav_graph, true)
                    .build())
        }
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

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                requireContext(), android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!granted) {
                // If not granted, show the standard popup
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // If already granted, open the system settings so the user can toggle it OFF if they want
                openAppNotificationSettings()
            }
        } else {
            // On older versions, just open the settings page
            openAppNotificationSettings()
        }
    }

    private fun openAppNotificationSettings() {
        val intent = Intent().apply {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                }
                else -> {
                    action = "android.settings.APP_NOTIFICATION_SETTINGS"
                    putExtra("app_package", requireContext().packageName)
                    putExtra("app_uid", requireContext().applicationInfo.uid)
                }
            }
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot open settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestAlarmPermissionManually() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Always open the settings page regardless of current state
            // so the user can toggle it ON or OFF.
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Cannot open alarm settings", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "Exact alarms are not restricted on this version", Toast.LENGTH_SHORT).show()
        }
    }
}