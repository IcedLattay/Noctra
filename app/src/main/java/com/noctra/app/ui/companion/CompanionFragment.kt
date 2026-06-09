package com.noctra.app.ui.companion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.os.Bundle
import android.util.Log
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
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.noctra.app.R
import com.noctra.app.databinding.FragmentCompanionBinding
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CompanionFragment : Fragment() {

    private var _binding: FragmentCompanionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CompanionViewModel by activityViewModels()

    private var currentStageLevel: Int = -1
    private var isPlayingTappedAnimation = false

    private val shleepyStates = mapOf(
        1 to ShleepyState("DEPRIVED", R.raw.deprived_idle, R.raw.deprived_tapped),
        2 to ShleepyState("AWAKENING", R.raw.awakening_idle, R.raw.awakening_tapped),
        3 to ShleepyState("CHARGED", R.raw.charged_idle, R.raw.charged_tapped),
        4 to ShleepyState("OVERDRIVE", R.raw.overdrive_idle, R.raw.overdrive_tapped),
        5 to ShleepyState("ZEN", R.raw.zen_idle, R.raw.zen_tapped)
    )

    data class ShleepyState(val name: String, val idleRes: Int, val tappedRes: Int)

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

                // 1. Update Shleepy Animation if stage changed
                if (currentStageLevel != evolution.stageLevel) {
                    currentStageLevel = evolution.stageLevel
                    if (!isPlayingTappedAnimation) {
                        applyIdleAnimation()
                    }
                }

                // 2. Update Accessories
                applyAccessoriesVisibility(state.equippedItems)
            }
        }
    }

    private fun applyIdleAnimation() {
        val state = shleepyStates[currentStageLevel] ?: shleepyStates[1]!!
        binding.petAnimationView.apply {
            setAnimation(state.idleRes)
            repeatCount = -1
            playAnimation()
        }
    }

    private fun triggerTappedAnimation() {
        if (isPlayingTappedAnimation) return
        
        val state = shleepyStates[currentStageLevel] ?: shleepyStates[1]!!
        isPlayingTappedAnimation = true
        
        binding.petAnimationView.apply {
            removeAllAnimatorListeners()
            setAnimation(state.tappedRes)
            repeatCount = 0
            playAnimation()
            
            addAnimatorListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isPlayingTappedAnimation = false
                    removeAnimatorListener(this)
                    applyIdleAnimation()
                }
            })
        }
    }

    private fun applyAccessoriesVisibility(equippedItems: Map<String, com.noctra.app.data.model.ShopItem>) {
        val petView = binding.petAnimationView
        
        // Define all possible hat layers that exist in your Lottie files
        val allHatLayers = listOf("hat_sleeping_hat", "hat_propeller_hat", "hat_floral_crown")
        
        // Map category to Lottie layer name (for single-item categories)
        val otherCategoryToLayer = mapOf(
            "OUTFIT" to "outfit_layer",
            "ACCESSORY" to "accessory_layer"
        )

        // Identify which hat layer should be active
        val activeHatLayer = equippedItems["HAT"]?.let { item ->
            when (item.itemAsset) {
                "hat_propeller_hat" -> "hat_propeller_hat"
                "hat_sleeping_hat" -> "hat_sleeping_hat"
                "hat_floral_crown" -> "hat_floral_crown"
                else -> null // Hide hats if asset doesn't have a layer yet
            }
        }

        val applyOpacity = {
            // 1. Handle Hats (Mutual Exclusivity)
            allHatLayers.forEach { layerName ->
                val opacity = if (layerName == activeHatLayer) 100 else 0
                try {
                    petView.addValueCallback(
                        KeyPath("**", layerName, "**"),
                        LottieProperty.TRANSFORM_OPACITY,
                        LottieValueCallback(opacity)
                    )
                } catch (e: Exception) {}
            }

            // 2. Handle Other Categories
            otherCategoryToLayer.forEach { (category, layerName) ->
                val isEquipped = equippedItems.containsKey(category)
                val opacity = if (isEquipped) 100 else 0
                try {
                    petView.addValueCallback(
                        KeyPath("**", layerName, "**"),
                        LottieProperty.TRANSFORM_OPACITY,
                        LottieValueCallback(opacity)
                    )
                } catch (e: Exception) {}
            }
        }

        // Ensure visibility is updated whenever a new composition is loaded
        petView.addLottieOnCompositionLoadedListener { applyOpacity() }
        
        // Also trigger it immediately for the current composition
        applyOpacity()
    }

    private fun setupListeners() {
        binding.btnCustomize.setOnClickListener {
            findNavController().navigate(R.id.action_companion_to_customization)
        }
        
        binding.petAnimationView.setOnClickListener {
            triggerTappedAnimation()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentStageLevel = -1
        isPlayingTappedAnimation = false
        _binding = null
    }
}
