package com.noctra.app.ui.routine.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.noctra.app.R
import com.noctra.app.data.repository.RoutineRepository
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.databinding.FragmentOnboardingSummaryBinding
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.launch

class OnboardingSummaryFragment : Fragment() {

    private var _binding: FragmentOnboardingSummaryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by navGraphViewModels(R.id.nav_graph)
    private val routineRepository = RoutineRepository()
    private val profileRepository = UserProfileRepository()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // After the notification popup is dealt with, check for alarm permission
        requestAlarmPermission()
        
        // Final navigation — re-initialize the Activity's nav graph
        // (nested graphs can't navigate to destinations in other nested graphs)
        reinitNavGraphToMain()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        populateSummary()
        loadHealthStatus()

        if (viewModel.isEditMode) {
            binding.tvTitle.text = "Review Changes"
            binding.btnStartNoctra.text = "Save Changes"
        }

        binding.btnStartNoctra.setOnClickListener {
            saveAndFinish()
        }
    }

    private fun populateSummary() {
        val activities = viewModel.orderedActivities.value
        val bedtime = viewModel.targetBedtime.value
        val totalDuration = viewModel.getTotalDurationMinutes()

        // Format bedtime: "22:00" → "10:00 PM"
        binding.tvBedtime.text = formatBedtime(bedtime)
        binding.tvDuration.text = "$totalDuration minutes"

        // Activity rows
        activities.getOrNull(0)?.let {
            binding.tvActivity1Name.text = it.label
            binding.tvActivity1Duration.text = "${it.defaultDurationMinutes}m"
        }
        activities.getOrNull(1)?.let {
            binding.tvActivity2Name.text = it.label
            binding.tvActivity2Duration.text = "${it.defaultDurationMinutes}m"
        }
        activities.getOrNull(2)?.let {
            binding.tvActivity3Name.text = it.label
            binding.tvActivity3Duration.text = "${it.defaultDurationMinutes}m"
        }
    }

    // Live Health Connect status so users see exactly what was granted
    // (stages mirrors sleep — same permission) and where to change it
    private fun loadHealthStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            val b = _binding ?: return@launch
            try {
                val context = requireContext()
                if (!com.noctra.app.utils.HealthConnectPermissionHelper.isAvailable(context)) {
                    return@launch
                }
                val client = androidx.health.connect.client.HealthConnectClient
                    .getOrCreate(context)
                val granted = com.noctra.app.utils.HealthConnectPermissionHelper
                    .getGrantedPermissions(client)
                val hasSleep = com.noctra.app.utils.HealthConnectPermissionHelper
                    .hasSleepPermission(granted)
                val hasHr = com.noctra.app.utils.HealthConnectPermissionHelper
                    .hasHeartRatePermission(granted)
                if (_binding == null) return@launch
                setHealthRow(b.badgeSummarySleep, hasSleep)
                setHealthRow(b.badgeSummaryHeart, hasHr)
                setHealthRow(b.badgeSummaryStages, hasSleep)
            } catch (e: Exception) {
                // Leave placeholder text on failure
            }
        }
    }

    // Same Granted / Not Granted pill styling as the Health Connect settings screen
    private fun setHealthRow(badge: android.widget.TextView, granted: Boolean) {
        if (granted) {
            badge.text = "Granted"
            badge.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.granted_green))
            badge.setBackgroundResource(R.drawable.bg_badge_green)
        } else {
            badge.text = "Not Granted"
            badge.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.pill_text_grey))
            badge.setBackgroundResource(R.drawable.bg_badge_grey)
        }
    }

    override fun onResume() {
        super.onResume()
        loadHealthStatus()
    }

    private fun formatBedtime(hhmm: String): String {
        val parts = hhmm.split(":")
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        val hour12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        val amPm = if (h < 12) "AM" else "PM"
        return "%d:%02d %s".format(hour12, m, amPm)
    }

    private fun saveAndFinish() {
        val userId = UserSession.getUserId(requireContext()) ?: return

        // Disable button to prevent double-tap
        binding.btnStartNoctra.isEnabled = false
        binding.btnStartNoctra.text = "Saving..."

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1. Save target bedtime to user_profiles
                profileRepository.updateTargetBedtime(
                    userId = userId,
                    bedtime = viewModel.targetBedtime.value
                )

                // 2. Save routine_configurations row
                routineRepository.saveRoutineConfiguration(
                    userId = userId,
                    activitySequence = viewModel.getActivitySequence(),
                    totalDurationMinutes = viewModel.getTotalDurationMinutes()
                )

                // 2b. Update local cache for notifications
                com.noctra.app.utils.NotificationPreferences.updateCachedSettings(
                    requireContext(),
                    bedtime = viewModel.targetBedtime.value,
                    durationMinutes = viewModel.getTotalDurationMinutes()
                )

                // 3. Mark onboarding complete
                profileRepository.markOnboardingComplete(userId)

                // 3b. Clear the onboarding draft (best-effort, never blocks)
                try {
                    profileRepository.clearDraft(userId)
                } catch (e: Exception) {
                    android.util.Log.e("OnboardingSummary", "Draft clear failed", e)
                }

                // 4. Schedule the first notification immediately
                com.noctra.app.workers.WindDownNotificationScheduler.scheduleNext(requireContext())

                // 5. Start sequential permission request
                requestPermissionsSequentially()

            }  catch (e: Exception) {
            // Log the actual stacktrace
            android.util.Log.e("OnboardingSummary", "Save failed", e)

            // Re-enable button on failure
            binding.btnStartNoctra.isEnabled = true
            binding.btnStartNoctra.text = "Start Using Noctra"

            Snackbar.make(
                binding.root,
                "Error: ${e.message ?: e.javaClass.simpleName}",
                Snackbar.LENGTH_LONG
            ).show()
        }
        }
    }

    private fun requestPermissionsSequentially() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!granted) {
                // This triggers the popup, and the callback above handles alarms and navigation
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Already granted, check alarms next
                requestAlarmPermission()
                reinitNavGraphToMain()
            }
        } else {
            // Older version, check alarms and then navigate
            requestAlarmPermission()
            reinitNavGraphToMain()
        }
    }

    private fun requestAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
        }
    }

    // Re-initialize the Activity's nav graph to the main flow.
    // Nested graphs can't navigate to destinations in other nested graphs,
    // so the only clean way out is to rebuild the graph from the root —
    // the same thing MainActivity does on startup.
    private fun reinitNavGraphToMain() {
        try {
            val navController = findNavController()
            val graph = navController.navInflater.inflate(R.navigation.nav_graph)
            graph.setStartDestination(R.id.main_graph)
            navController.graph = graph
        } catch (e: Exception) {
            android.util.Log.e("OnboardingSummary", "Graph reinit failed", e)
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}