package com.noctra.app.ui.companion

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.noctra.app.R
import com.noctra.app.databinding.FragmentCompanionBinding
import com.noctra.app.utils.ShleepyAssetHelper
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CompanionFragment : Fragment() {

    private var _binding: FragmentCompanionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CompanionViewModel by activityViewModels()

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

    override fun onResume() {
        super.onResume()
        val userId = UserSession.getUserId(requireContext())
        val lastShownSleepDate = requireContext().getSharedPreferences("noctra_prefs", Context.MODE_PRIVATE)
            .getString("last_shown_sleep_date", null)
        viewModel.loadData(userId, lastShownSleepDate)
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
                launch {
                    viewModel.showDevolutionPopup.collectLatest {
                        showDevolutionPenalty()
                    }
                }
            }
        }
    }

    private fun showMorningPopup(score: Int, xp: Int) {
        val dialog = MorningSleepPopupDialog.newInstance(score, xp)
        dialog.show(childFragmentManager, "MorningSleepPopup")
        
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

                // 1. Update Shleepy Base
                val shleepyResId = when (evolution.stageLevel) {
                    1 -> R.drawable.shleepy_depleted
                    2 -> R.drawable.shleepy_awakening
                    3 -> R.drawable.shleepy_charged
                    4 -> R.drawable.shleepy_overdrive
                    5 -> R.drawable.shleepy_zenmaster
                    else -> R.drawable.shleepy_depleted
                }
                ivShleepy.setImageResource(shleepyResId)

                // 2. Update Equipped Items (Hats, Outfits, Accessories)
                val categories = mapOf(
                    "HAT" to ivEquippedHatCompanion,
                    "OUTFIT" to ivEquippedOutfitCompanion,
                    "ACCESSORY" to ivEquippedAccessoryCompanion
                )

                val stageSuffix = when (evolution.stageLevel) {
                    1 -> "depleted"
                    2 -> "awakening"
                    3 -> "charged"
                    4 -> "overdrive"
                    5 -> "zenmaster"
                    else -> "depleted"
                }

                categories.forEach { (category, imageView) ->
                    val item = state.equippedItems[category]
                    if (item != null) {
                        // Using helper for ALL accessories now since they are full-frame
                        ShleepyAssetHelper.applyAccessory(imageView, item, stageSuffix)
                    } else {
                        imageView.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnCustomize.setOnClickListener {
            findNavController().navigate(R.id.action_companion_to_customization)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
