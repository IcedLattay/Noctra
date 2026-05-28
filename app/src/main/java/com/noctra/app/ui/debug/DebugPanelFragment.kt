package com.noctra.app.ui.debug

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.noctra.app.R

interface DebugPanelListener {
    fun onResetOnboarding()
    fun onForceRoutineWindowOpen()
    fun onFireWindDownNotification()
    fun onSimulateMorningSync()
    fun onSimulateMissedNight()
    fun onTriggerEvolution()
    fun onSeedDemoData()
    fun onClearDemoData()
}

class DebugPanelFragment : Fragment() {

    private var listener: DebugPanelListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is DebugPanelListener) {
            listener = context
        } else {
            // Optional: fallback to parent fragment if needed, or throw error
            // throw RuntimeException("$context must implement DebugPanelListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_debug_panel, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.btn_close).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // Section 1: Onboarding
        view.findViewById<MaterialButton>(R.id.btn_reset_onboarding).setOnClickListener {
            listener?.onResetOnboarding()
        }

        // Section 2: Routine
        view.findViewById<MaterialButton>(R.id.btn_force_window).setOnClickListener {
            listener?.onForceRoutineWindowOpen()
        }
        view.findViewById<MaterialButton>(R.id.btn_fire_notif).setOnClickListener {
            listener?.onFireWindDownNotification()
        }

        // Section 3: Sleep & Morning Sync
        view.findViewById<MaterialButton>(R.id.btn_simulate_morning).setOnClickListener {
            listener?.onSimulateMorningSync()
        }
        view.findViewById<MaterialButton>(R.id.btn_simulate_missed).setOnClickListener {
            listener?.onSimulateMissedNight()
        }

        // Section 4: Gamification
        view.findViewById<MaterialButton>(R.id.btn_trigger_evolution).setOnClickListener {
            listener?.onTriggerEvolution()
        }

        // Section 5: Data
        view.findViewById<MaterialButton>(R.id.btn_seed_data).setOnClickListener {
            listener?.onSeedDemoData()
        }
        view.findViewById<MaterialButton>(R.id.btn_clear_data).setOnClickListener {
            listener?.onClearDemoData()
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }
}