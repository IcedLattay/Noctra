package com.noctra.app.ui.routine.onboarding

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.noctra.app.R
import com.noctra.app.databinding.FragmentHealthConnectSetupBinding

/**
 * HealthConnectSetupFragment — onboarding Step 4 of 4.
 *
 * ⚠️ UI SHELL ONLY — DOES NOT REQUEST REAL PERMISSIONS.
 *
 * The androidx.health.connect.client dependency is not in build.gradle.kts,
 * and no HealthConnectManager exists yet. Both are specified in the SDD but
 * belong to Module 3 (Sleep Quality Monitoring), so they're deliberately not
 * added here to avoid colliding with that work.
 *
 * Tapping "Grant Permissions" flips the UI to the granted state and writes a
 * local flag to SharedPreferences. Nothing is actually requested from the
 * system, and the app's sleep data remains mock (see
 * MainActivity.onSimulateMorningSync()).
 *
 * TO WIRE UP FOR REAL, replace the body of grantPermissions() with a
 * HealthConnectClient permission request, and replace readGrantedFlag() with
 * a query of the client's actual granted permissions. The rest of this
 * fragment — the UI states, navigation, skip path — stays as-is.
 */
class HealthConnectSetupFragment : Fragment() {

    private var _binding: FragmentHealthConnectSetupBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val PREFS_NAME = "noctra_prefs"
        private const val KEY_HEALTH_CONNECT_GRANTED = "health_connect_granted"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHealthConnectSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Reflect whatever state we left this screen in previously.
        renderState(isGranted = readGrantedFlag())

        binding.btnGrantPermissions.setOnClickListener { grantPermissions() }
        binding.btnSkipForNow.setOnClickListener { proceedToSummary() }
        binding.btnContinue.setOnClickListener { proceedToSummary() }
    }

    /**
     * SHELL BEHAVIOR — marks permissions granted locally without asking the
     * system for anything. See the class doc for how to make this real.
     */
    private fun grantPermissions() {
        writeGrantedFlag(true)
        renderState(isGranted = true)
    }

    /** Swaps every pill, icon badge, and button between the two states. */
    private fun renderState(isGranted: Boolean) {
        val pillBg = if (isGranted)
            R.drawable.bg_permission_pill_granted else R.drawable.bg_permission_pill_required
        val pillText = if (isGranted) "Granted" else "Required"
        val pillColor = if (isGranted) "#1B5E20" else "#B3261E"
        val badgeBg = if (isGranted)
            R.drawable.bg_permission_icon_green else R.drawable.bg_permission_icon_grey

        listOf(binding.pillSleep, binding.pillHeartRate, binding.pillSteps).forEach { pill ->
            pill.setBackgroundResource(pillBg)
            pill.text = pillText
            pill.setTextColor(android.graphics.Color.parseColor(pillColor))
        }

        listOf(binding.badgeSleep, binding.badgeHeartRate, binding.badgeSteps).forEach { badge ->
            badge.setBackgroundResource(badgeBg)
        }

        binding.cardAllGranted.visibility = if (isGranted) View.VISIBLE else View.GONE

        // Grant + Skip are the pre-grant pair; Continue replaces both after.
        binding.btnGrantPermissions.visibility = if (isGranted) View.GONE else View.VISIBLE
        binding.btnSkipForNow.visibility = if (isGranted) View.GONE else View.VISIBLE
        binding.btnContinue.visibility = if (isGranted) View.VISIBLE else View.GONE
    }

    private fun proceedToSummary() {
        findNavController().navigate(R.id.action_healthConnectSetup_to_onboardingSummary)
    }

    // ─── Local flag (stands in for a real permission query) ───────────────────

    private fun readGrantedFlag(): Boolean =
        requireContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HEALTH_CONNECT_GRANTED, false)

    private fun writeGrantedFlag(granted: Boolean) {
        requireContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HEALTH_CONNECT_GRANTED, granted)
            .apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}