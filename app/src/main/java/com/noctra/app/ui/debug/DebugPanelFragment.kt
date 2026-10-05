package com.noctra.app.ui.debug

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.noctra.app.R
import kotlinx.coroutines.launch

interface DebugPanelListener {
    fun onResetOnboarding()
    fun onForceRoutineWindowOpen()
    fun onFireWindDownNotification()
    fun onSimulateMorningSync()
    fun onSimulateMissedNight()
    fun onTriggerEvolution()
    fun onPreviewEvolution()
    fun onPreviewStageAnimations()
    fun onPreviewMorningRecap()
    fun onPreviewStreakRestored()
    fun onPreviewStreakLost()
    fun onPreviewStreakWarning()
    fun onPreviewResumeDialog()
    fun onDumpSleepSession()
    fun onDumpHeartRate()
    fun onResyncLastNight()
    fun onBackfillNow()
    fun onPreviewSequencing()
    fun onPreviewActivity(label: String)
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
        view.findViewById<MaterialButton>(R.id.btn_preview_evolution).setOnClickListener {
            listener?.onPreviewEvolution()
        }
        view.findViewById<MaterialButton>(R.id.btn_preview_stages).setOnClickListener {
            listener?.onPreviewStageAnimations()
        }

        // Section 6: Dialog Previews
        view.findViewById<MaterialButton>(R.id.btn_preview_morning).setOnClickListener {
            listener?.onPreviewMorningRecap()
        }
        view.findViewById<MaterialButton>(R.id.btn_preview_streak_restored).setOnClickListener {
            listener?.onPreviewStreakRestored()
        }
        view.findViewById<MaterialButton>(R.id.btn_preview_streak_lost).setOnClickListener {
            listener?.onPreviewStreakLost()
        }
        view.findViewById<MaterialButton>(R.id.btn_preview_streak_warning).setOnClickListener {
            listener?.onPreviewStreakWarning()
        }
        view.findViewById<MaterialButton>(R.id.btn_preview_resume).setOnClickListener {
            listener?.onPreviewResumeDialog()
        }
        view.findViewById<MaterialButton>(R.id.btn_dump_sleep).setOnClickListener {
            listener?.onDumpSleepSession()
        }
        view.findViewById<MaterialButton>(R.id.btn_dump_heartrate).setOnClickListener {
            listener?.onDumpHeartRate()
        }
        view.findViewById<MaterialButton>(R.id.btn_resync_night).setOnClickListener {
            listener?.onResyncLastNight()
        }
        view.findViewById<MaterialButton>(R.id.btn_backfill_now).setOnClickListener {
            listener?.onBackfillNow()
        }
        view.findViewById<MaterialButton>(R.id.btn_preview_sequencing).setOnClickListener {
            listener?.onPreviewSequencing()
        }
        // One preview button per library activity, built from the DB so new
        // activities appear with no code change.
        val previewList = view.findViewById<LinearLayout>(R.id.preview_activity_list)
        viewLifecycleOwner.lifecycleScope.launch {
            val library = try {
                com.noctra.app.data.repository.RoutineRepository().getActivityLibrary()
            } catch (e: Exception) {
                emptyList()
            }
            library.forEach { activity ->
                val button = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonStyle)
                button.text = activity.label
                button.setOnClickListener { listener?.onPreviewActivity(activity.label) }
                previewList.addView(button)
            }
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