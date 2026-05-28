package com.noctra.app.ui.routine.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.noctra.app.R
import com.noctra.app.databinding.FragmentBedtimeConfigBinding
import com.noctra.app.ui.common.BedtimePickerBottomSheet
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class BedtimeConfigFragment : Fragment() {

    private var _binding: FragmentBedtimeConfigBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by navGraphViewModels(R.id.nav_graph)

    // Current selection — stored as HH:mm string to match picker expectation
    private var currentBedtime = "22:00"

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
        currentBedtime = viewModel.targetBedtime.value
        updateTimeDisplay()

        // Tap the card to open the custom bottom sheet time picker
        binding.cardTimePicker.setOnClickListener {
            showTimePicker()
        }

        binding.btnContinue.setOnClickListener {
            viewModel.setBedtime(currentBedtime)
            findNavController().navigate(R.id.action_bedtimeConfig_to_activityLibrary)
        }
    }

    private fun showTimePicker() {
        BedtimePickerBottomSheet()
            .configure(currentBedtime = currentBedtime) { newBedtime ->
                currentBedtime = newBedtime
                updateTimeDisplay()
            }
            .show(parentFragmentManager, "bedtime_picker")
    }

    private fun updateTimeDisplay() {
        // Parse currentBedtime (HH:mm or HH:mm:ss) for display
        val time = try {
            LocalTime.parse(currentBedtime)
        } catch (e: Exception) {
            LocalTime.of(22, 0)
        }

        // Display card: "10:00 PM" style large text
        binding.tvBedtimeDisplay.text = time.format(DateTimeFormatter.ofPattern("h:mm a"))

        // Estimated start = bedtime - 30 min (placeholder until Step 3 total is known)
        val startTime = time.minusMinutes(30)
        binding.tvEstimatedStart.text = startTime.format(DateTimeFormatter.ofPattern("h:mm a"))
        binding.tvEstimatedStartHint.text = "Based on a ~30 minute routine"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
