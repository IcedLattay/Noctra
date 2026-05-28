package com.noctra.app.ui.routine.onboarding

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.noctra.app.R
import com.noctra.app.databinding.FragmentBedtimeConfigBinding

class BedtimeConfigFragment : Fragment() {

    private var _binding: FragmentBedtimeConfigBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by navGraphViewModels(R.id.nav_graph)

    // Current selection — default 10:00 PM
    private var selectedHour = 22
    private var selectedMinute = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBedtimeConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Restore from ViewModel if user navigated back
        val saved = viewModel.targetBedtime.value
        val parts = saved.split(":")
        selectedHour = parts[0].toInt()
        selectedMinute = parts[1].toInt()

        updateTimeDisplay()

        // Tap the card to open time picker dialog
        binding.cardTimePicker.setOnClickListener {
            showTimePicker()
        }

        binding.btnContinue.setOnClickListener {
            val hhmm = "%02d:%02d".format(selectedHour, selectedMinute)
            viewModel.setBedtime(hhmm)
            findNavController().navigate(R.id.action_bedtimeConfig_to_activityLibrary)
        }
    }

    private fun showTimePicker() {
        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                // Enforce 8 PM–2 AM range
                if (isInAllowedRange(hourOfDay, minute)) {
                    selectedHour = hourOfDay
                    selectedMinute = minute
                    updateTimeDisplay()
                } else {
                    // Snap to nearest valid boundary
                    if (hourOfDay in 3..19) {
                        selectedHour = 20; selectedMinute = 0  // snap to 8 PM
                    } else {
                        selectedHour = 2; selectedMinute = 0   // snap to 2 AM
                    }
                    updateTimeDisplay()
                    showRangeWarning()
                }
            },
            selectedHour,
            selectedMinute,
            false // 12-hour format
        ).show()
    }

    private fun isInAllowedRange(hour: Int, minute: Int): Boolean {
        // Valid: 20:00–23:59 OR 00:00–02:00
        return when {
            hour in 20..23 -> true
            hour == 0 || hour == 1 -> true
            hour == 2 && minute == 0 -> true
            else -> false
        }
    }

    private fun updateTimeDisplay() {
        // Display card: "10:00 PM" style large text
        val hour12 = when {
            selectedHour == 0 -> 12
            selectedHour > 12 -> selectedHour - 12
            else -> selectedHour
        }
        val amPm = if (selectedHour < 12) "AM" else "PM"
        binding.tvBedtimeDisplay.text = "%d:%02d".format(hour12, selectedMinute)

        // Estimated start = bedtime - 30 min (placeholder until Step 3 total is known)
        val totalBedtimeMinutes = selectedHour * 60 + selectedMinute
        val startMinutes = totalBedtimeMinutes - 30
        val startHour = (startMinutes / 60).mod(24)
        val startMin = startMinutes.mod(60)

        val startHour12 = when {
            startHour == 0 -> 12
            startHour > 12 -> startHour - 12
            else -> startHour
        }
        val startAmPm = if (startHour < 12) "AM" else "PM"
        binding.tvEstimatedStart.text = "%d:%02d".format(startHour12, startMin)
        binding.tvEstimatedStartHint.text = "Based on a ~30 minute routine"
    }

    private fun showRangeWarning() {
        com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            "Bedtime must be between 8:00 PM and 2:00 AM",
            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}