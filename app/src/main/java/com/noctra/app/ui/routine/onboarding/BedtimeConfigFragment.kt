package com.noctra.app.ui.routine.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.noctra.app.R
import com.noctra.app.databinding.FragmentBedtimeConfigBinding
import com.noctra.app.ui.common.BedtimePickerBottomSheet
import kotlinx.coroutines.launch
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

        // Bold the "asleep by" segment of the hint card
        val hint = "💡 This is the time you want to be asleep by — not when you start your routine."
        val boldStart = hint.indexOf("asleep by")
        binding.tvHint.text = android.text.SpannableString(hint).apply {
            setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                boldStart,
                boldStart + "asleep by".length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Restore from ViewModel if user navigated back, else from a
        // saved draft (fresh-process resume), else keep the default
        val userId = com.noctra.app.utils.UserSession.getUserId(requireContext())
        if (userId != null) {
            lifecycleScope.launch {
                viewModel.restoreDraftIfEmpty(userId)
                currentBedtime = viewModel.targetBedtime.value
                updateTimeDisplay()
            }
        } else {
            currentBedtime = viewModel.targetBedtime.value
            updateTimeDisplay()
        }

        // Tap the card to open the custom bottom sheet time picker
        binding.cardTimePicker.setOnClickListener {
            showTimePicker()
        }

        binding.btnContinue.setOnClickListener {
            viewModel.setBedtime(currentBedtime)

            val userId = com.noctra.app.utils.UserSession.getUserId(requireContext())
            if (userId != null) {
                viewModel.updateStep(userId, 1)
                viewModel.saveDraft(userId)
            }

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

        // Display card: big "10:00 PM" style text
        binding.tvBedtimeDisplay.text = time.format(DateTimeFormatter.ofPattern("h:mm a"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
