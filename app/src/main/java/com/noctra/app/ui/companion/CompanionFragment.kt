package com.noctra.app.ui.companion

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.noctra.app.R
import com.noctra.app.databinding.FragmentCompanionBinding
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CompanionFragment : Fragment() {

    private var _binding: FragmentCompanionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CompanionViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompanionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userId = UserSession.getUserId(requireContext())
        val lastShownSleepDate = requireContext().getSharedPreferences("noctra_prefs", Context.MODE_PRIVATE)
            .getString("last_shown_sleep_date", null)
            
        viewModel.loadData(userId, lastShownSleepDate)

        observeUiState()
        setupListeners()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        updateUi(state)
                    }
                }
                launch {
                    viewModel.showMorningPopup.collectLatest { (score, xp) ->
                        showMorningPopup(score, xp)
                    }
                }
                launch {
                    viewModel.showEvolutionPopup.collectLatest { evolution ->
                        showEvolutionPopup(evolution.stageName)
                    }
                }
            }
        }
    }

    private fun showMorningPopup(score: Int, xp: Int) {
        val dialog = MorningSleepPopupDialog.newInstance(score, xp)
        dialog.show(childFragmentManager, "MorningSleepPopup")
        
        // Save today as the last shown date to prevent repeated popups today
        val today = java.time.LocalDate.now().toString()
        requireContext().getSharedPreferences("noctra_prefs", Context.MODE_PRIVATE)
            .edit(commit = false) {
                putString("last_shown_sleep_date", today)
            }
    }

    private fun showEvolutionPopup(stageName: String) {
        val dialog = EvolutionDialogFragment.newInstance(stageName)
        dialog.show(childFragmentManager, "EvolutionPopup")
    }

    private fun showDevolutionPenalty() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Shleepy looks tired...")
            .setMessage("Your companion needs you — complete tonight's routine to start recovering.")
            .setPositiveButton("I will!") { _, _ -> }
            .show()
    }

    private fun updateUi(state: CompanionViewModel.CompanionUiState) {
        with(binding) {
            tvTokenBalance.text = state.tokenBalance.toString()
            
            state.evolutionState?.let { evolution ->
                tvStageLabel.text = evolution.stageName
                tvXpValue.text = getString(R.string.companion_xp_unit, evolution.totalXp)
                pbXpProgress.progress = (evolution.progressPercent * 100).toInt()

                // Update Shleepy image based on stage level
                val shleepyResId = when (evolution.stageLevel) {
                    1 -> R.drawable.shleepydepleted
                    2 -> R.drawable.shleepyawakening
                    3 -> R.drawable.shleepycharged
                    4 -> R.drawable.shleepyoverdrive
                    5 -> R.drawable.shleepyzenmaster
                    else -> R.drawable.shleepy
                }
                ivShleepy.setImageResource(shleepyResId)
            }

            // Sleep Quality Card Logic
            if (state.lastSleepScore != null) {
                groupSleepQualitySuccess.visibility = View.VISIBLE
                tvSleepQualityNoData.visibility = View.GONE
                tvSleepScoreMini.text = state.lastSleepScore.toString()
                pbSleepGaugeMini.progress = state.lastSleepScore
                // Update status text based on score
                tvSleepQualityStatus.text = when {
                    state.lastSleepScore >= 75 -> getString(R.string.companion_good_sleep_quality)
                    state.lastSleepScore >= 50 -> "Fair Sleep Quality"
                    else -> "Poor Sleep Quality"
                }
            } else {
                groupSleepQualitySuccess.visibility = View.GONE
                tvSleepQualityNoData.visibility = View.VISIBLE
            }

            if (state.devolutionPending) {
                showDevolutionPenalty()
            }

            // TODO: Update Shleepy Lottie based on stage
            
            // isLoading state handled here
            // binding.loadingOverlay.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun setupListeners() {
        binding.btnCustomize.setOnClickListener {
            findNavController().navigate(R.id.action_companion_to_customization)
        }

        binding.btnSeedData.setOnClickListener {
            val userId = UserSession.getUserId(requireContext())
            viewModel.seedDemoData(userId)
        }

        binding.btnXpPlus.setOnClickListener {
            val userId = UserSession.getUserId(requireContext())
            viewModel.addXp(userId, 500)
        }

        binding.btnXpMinus.setOnClickListener {
            val userId = UserSession.getUserId(requireContext())
            viewModel.addXp(userId, -500)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
