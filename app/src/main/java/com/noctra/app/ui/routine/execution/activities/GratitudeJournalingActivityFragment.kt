package com.noctra.app.ui.routine.execution.activities

import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.noctra.app.R
import com.noctra.app.databinding.FragmentGratitudeJournalingActivityBinding
import com.noctra.app.ui.routine.RoutineViewModel
import kotlinx.coroutines.launch

/**
 * GratitudeJournalingActivityFragment
 *
 * Serves: Gratitude Journaling only.
 *
 * FIXED: previously called routineViewModel.startCurrentActivityTimer() with
 * no argument, silently defaulting to the VM's 15s demo value permanently —
 * not a test-mode choice, just never wired to the real DB duration (5min)
 * at all. Now passes the real duration (or the temporary 15s test override),
 * same pattern as AudioscapeActivityFragment and GenericTimerActivityFragment.
 *
 * FIXED: previous PRE_COUNTDOWN_SECONDS was 10L, not the standard 15L every
 * other activity uses — inconsistent with the wireframe. Corrected to 15L.
 *
 * Session 5: keyboard-inset handling (R3), and typing is locked once the
 * journal timer reaches 0 (R7).
 */
class GratitudeJournalingActivityFragment : Fragment() {

    private var _binding: FragmentGratitudeJournalingActivityBinding? = null
    private val binding get() = _binding!!

    private val routineViewModel: RoutineViewModel by activityViewModels()

    private var preCountdownTimer: CountDownTimer? = null

    // Session 5 (R7): true once the real journal timer starts, so the
    // "timer = 0" lock doesn't fire on the VM's leftover 0 value.
    private var journalTimerStarted = false

    companion object {
        private const val PRE_COUNTDOWN_SECONDS = 15L

        // TEMPORARY FOR TESTING — set to false once all activities are
        // manually verified, to restore the real 5-minute DB duration.
        private const val TEST_MODE_SHORT_DURATION = false
        private const val TEST_DURATION_SECONDS = 15
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGratitudeJournalingActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showPreCountdownPanel()
        setupKeyboardInsets()
        setupListeners()
        observeVm()
        startPreCountdown()
    }

    private fun setupListeners() {
        binding.btnCompleteRoutine.setOnClickListener {
            routineViewModel.onCompleteRoutineTapped()
        }
    }

    /**
     * Session 5 (R3): keep the journal box above the keyboard.
     * On Android 15+ (edge-to-edge), adjustResize no longer shrinks the
     * screen, so the keyboard covered the journal. This pushes content up
     * by the covered amount and hides Shleepy while typing.
     */
    private fun setupKeyboardInsets() {
        val panel = binding.journalPanel
        val baseBottomPadding = panel.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(panel) { v, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val keyboardOpen = insets.isVisible(WindowInsetsCompat.Type.ime())

            // If the system already resized the screen, this gap = keyboard
            // height and extra = 0 (no double padding on older Android).
            val location = IntArray(2)
            v.getLocationInWindow(location)
            val gapBelowPanel = (v.rootView.height - (location[1] + v.height)).coerceAtLeast(0)
            val extra = (imeBottom - gapBelowPanel).coerceAtLeast(0)

            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, baseBottomPadding + extra)

            val headerVisibility = if (keyboardOpen) View.GONE else View.VISIBLE
            binding.ivShleepyLogo.visibility = headerVisibility
            binding.ivShleepyBody.visibility = headerVisibility
            // Title + subtitle go too: the input card is the only flexible
            // box, so everything fixed must leave while typing or the card
            // absorbs the whole keyboard squeeze and crops its own text.
            binding.tvTitle.visibility = headerVisibility
            binding.tvSubtitle.visibility = headerVisibility

            insets
        }
        ViewCompat.requestApplyInsets(panel)
    }

    private fun showPreCountdownPanel() {
        binding.preCountdownPanel.visibility = View.VISIBLE
        binding.journalPanel.visibility = View.GONE
        updatePreTimer(PRE_COUNTDOWN_SECONDS)
    }

    private fun showJournalPanel() {
        binding.preCountdownPanel.visibility = View.GONE
        binding.journalPanel.visibility = View.VISIBLE
        // Auto-focus the EditText so keyboard comes up
        binding.etJournalEntry.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etJournalEntry, InputMethodManager.SHOW_IMPLICIT)
        val durationSeconds = if (TEST_MODE_SHORT_DURATION) TEST_DURATION_SECONDS
        else (routineViewModel.currentActivity?.defaultDurationMinutes ?: 0) * 60
        journalTimerStarted = true
        routineViewModel.startCurrentActivityTimer(durationSeconds)
    }

    private fun startPreCountdown() {
        preCountdownTimer = object : CountDownTimer((PRE_COUNTDOWN_SECONDS * 1000L) + 500L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = (millisUntilFinished / 1000L).coerceAtMost(PRE_COUNTDOWN_SECONDS)
                updatePreTimer(secs)
                val colorRes = if (secs <= 5) R.color.timer_red else R.color.timer_green
                binding.tvPreTimer.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
            }
            override fun onFinish() {
                updatePreTimer(0)
                showJournalPanel()
            }
        }.start()
    }

    private fun updatePreTimer(seconds: Long) {
        val mins = seconds / 60
        val secs = seconds % 60
        binding.tvPreTimer.text = String.format("%02d : %02d", mins, secs)
    }

    private fun observeVm() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    routineViewModel.activitySecondsRemaining.collect { secs ->
                        updateTimerDisplay(secs.toLong())
                        updateTimerColor(secs.toLong())

                        // Session 5 (R7): time's up — no more typing.
                        if (secs == 0 && journalTimerStarted) lockJournalEntry()

                        // Show "Complete Routine" button if timer is 0 AND it's the last step
                        if (secs == 0 && routineViewModel.isLastStep) {
                            binding.btnCompleteRoutine.visibility = View.VISIBLE
                        } else {
                            binding.btnCompleteRoutine.visibility = View.GONE
                        }
                    }
                }
                launch {
                    routineViewModel.navigationEvent.collect { handleNavigationEvent(it) }
                }
            }
        }
    }

    private fun handleNavigationEvent(event: RoutineViewModel.NavigationEvent) {
        when (event) {
            is RoutineViewModel.NavigationEvent.GoToTransition -> {
                clearEntryAndDismissKeyboard()
                findNavController().navigate(R.id.timesUpTransitionFragment)
            }
            is RoutineViewModel.NavigationEvent.GoToCompletion -> {
                clearEntryAndDismissKeyboard()
                findNavController().navigate(R.id.routineCompletionOverlayFragment)
            }
            else -> {}
        }
    }

    /** Session 5 (R7): time's up — stop typing and close the keyboard. */
    private fun lockJournalEntry() {
        if (_binding == null) return
        binding.etJournalEntry.isEnabled = false
        binding.etJournalEntry.clearFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etJournalEntry.windowToken, 0)
    }

    private fun clearEntryAndDismissKeyboard() {
        binding.etJournalEntry.setText("")
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etJournalEntry.windowToken, 0)
    }

    private fun updateTimerDisplay(seconds: Long) {
        if (_binding == null) return
        val mins = seconds / 60
        val secs = seconds % 60
        binding.tvTimer.text = String.format("%02d : %02d", mins, secs)
    }

    private fun updateTimerColor(seconds: Long) {
        if (_binding == null) return
        // Wireframe only shows default color and red near the end — no green phase.
        val colorRes = if (seconds <= 5) R.color.timer_red else R.color.timer_default
        binding.tvTimer.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        preCountdownTimer?.cancel()
        preCountdownTimer = null
        _binding = null
    }
}