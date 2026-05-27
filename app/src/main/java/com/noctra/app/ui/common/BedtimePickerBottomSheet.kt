package com.noctra.app.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.NumberPicker
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.noctra.app.R

/**
 * Bottom sheet for picking a target bedtime.
 * Only allows times between 8:00 PM and 2:00 AM (inclusive), in 5-minute increments.
 *
 * Usage:
 *   BedtimePickerBottomSheet()
 *       .configure(currentBedtime = "22:00:00") { newBedtime ->
 *           // newBedtime is "HH:mm:ss" e.g. "22:30:00"
 *           viewModel.updateTargetBedtime(context, newBedtime)
 *       }
 *       .show(parentFragmentManager, "bedtime_picker")
 */
class BedtimePickerBottomSheet : BottomSheetDialogFragment() {

    private var currentBedtime: String? = null
    private var onSave: ((String) -> Unit)? = null

    fun configure(
        currentBedtime: String?,
        onSave: (String) -> Unit
    ): BedtimePickerBottomSheet {
        this.currentBedtime = currentBedtime
        this.onSave = onSave
        return this
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_bedtime_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val hourPicker = view.findViewById<NumberPicker>(R.id.picker_hour)
        val minutePicker = view.findViewById<NumberPicker>(R.id.picker_minute)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)
        val btnSave = view.findViewById<Button>(R.id.btn_save)

        // HOURS: 7 valid options in display order
        // Index → 24-hour clock value
        // 0 → 20 (8 PM)
        // 1 → 21 (9 PM)
        // 2 → 22 (10 PM)
        // 3 → 23 (11 PM)
        // 4 → 0  (12 AM)
        // 5 → 1  (1 AM)
        // 6 → 2  (2 AM)
        val hourLabels = arrayOf("8 PM", "9 PM", "10 PM", "11 PM", "12 AM", "1 AM", "2 AM")
        val hourValues = intArrayOf(20, 21, 22, 23, 0, 1, 2)

        hourPicker.minValue = 0
        hourPicker.maxValue = hourLabels.size - 1
        hourPicker.displayedValues = hourLabels
        hourPicker.wrapSelectorWheel = false

        // MINUTES: 5-minute increments
        val minuteLabels = (0..55 step 5).map { String.format("%02d", it) }.toTypedArray()
        val minuteValues = (0..55 step 5).toList()

        minutePicker.minValue = 0
        minutePicker.maxValue = minuteLabels.size - 1
        minutePicker.displayedValues = minuteLabels
        minutePicker.wrapSelectorWheel = true

        // Pre-select the current bedtime (or default to 10 PM if not set / invalid)
        val (defaultHourIndex, defaultMinuteIndex) = parseInitialSelection(
            currentBedtime, hourValues, minuteValues
        )
        hourPicker.value = defaultHourIndex
        minutePicker.value = defaultMinuteIndex

        btnCancel.setOnClickListener { dismiss() }

        btnSave.setOnClickListener {
            val selectedHour = hourValues[hourPicker.value]
            val selectedMinute = minuteValues[minutePicker.value]
            val formatted = String.format("%02d:%02d:00", selectedHour, selectedMinute)
            onSave?.invoke(formatted)
            dismiss()
        }
    }

    /**
     * Given a stored bedtime string and our valid options, figure out which
     * indices to pre-select on the wheels.
     * Falls back to (10 PM, :00) — index (2, 0) — if the value is missing or invalid.
     */
    private fun parseInitialSelection(
        bedtime: String?,
        hourValues: IntArray,
        minuteValues: List<Int>
    ): Pair<Int, Int> {
        if (bedtime.isNullOrBlank()) return 2 to 0

        return try {
            // bedtime format is "HH:mm:ss" or "HH:mm"
            val parts = bedtime.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()

            val hourIndex = hourValues.indexOf(hour).takeIf { it >= 0 } ?: 2
            // Snap minute to nearest 5-min increment
            val snappedMinute = (minute / 5) * 5
            val minuteIndex = minuteValues.indexOf(snappedMinute).takeIf { it >= 0 } ?: 0

            hourIndex to minuteIndex
        } catch (e: Exception) {
            2 to 0  // default 10 PM
        }
    }
}