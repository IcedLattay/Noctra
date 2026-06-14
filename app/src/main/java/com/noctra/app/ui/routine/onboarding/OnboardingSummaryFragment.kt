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
        // After the notification popup is dealt with, trigger the Alarm permission
        requestAlarmPermission()
        
        // Final navigation
        findNavController().navigate(R.id.action_onboardingSummary_to_routineHome)
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
        val userId = UserSession.getUserId(requireContext())

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

                // 3. Mark onboarding complete — Person C's routing reads this
                profileRepository.markOnboardingComplete(userId)

                // 4. Start sequential permission request
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
                // This triggers the popup, and the callback above handles the rest
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Already granted, skip to next step
                requestAlarmPermission()
                findNavController().navigate(R.id.action_onboardingSummary_to_routineHome)
            }
        } else {
            // Older version, skip notifications and check alarms
            requestAlarmPermission()
            findNavController().navigate(R.id.action_onboardingSummary_to_routineHome)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}