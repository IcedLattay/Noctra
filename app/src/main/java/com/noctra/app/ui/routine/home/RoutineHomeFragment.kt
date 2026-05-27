package com.noctra.app.ui.routine.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class RoutineHomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Placeholder - Person A will replace this with the real layout
        return TextView(requireContext()).apply {
            text = "Routine Home (placeholder)"
            textSize = 24f
            gravity = android.view.Gravity.CENTER
        }
    }
}