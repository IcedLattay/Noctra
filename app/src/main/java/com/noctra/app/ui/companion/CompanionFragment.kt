package com.noctra.app.ui.companion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.transition.AutoTransition
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.noctra.app.R
import com.noctra.app.databinding.FragmentCompanionBinding
import com.noctra.app.ui.companion.adapters.ShopItemAdapter
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CompanionFragment : Fragment() {

    private var _binding: FragmentCompanionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CompanionViewModel by activityViewModels()
    private lateinit var shopAdapter: ShopItemAdapter

    private var currentStageLevel: Int = -1
    private var isPlayingTappedAnimation = false
    private var isCustomizeMode = false
    private var currentCategory = "Hats"
    
    // Store latest equipped items to apply when animations change
    private var currentEquippedItems: Map<String, com.noctra.app.data.model.ShopItem> = emptyMap()

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

        setupRecyclerView()
        setupCategoryTabs()
        setupListeners()
        setupBackNavigation()
        setupLottieListeners()
        observeUiState()

        val userId = UserSession.getUserId(requireContext()) ?: return
        val lastShownSleepDate = requireContext().getSharedPreferences("noctra_prefs", Context.MODE_PRIVATE)
            .getString("last_shown_sleep_date", null)
            
        viewModel.loadData(userId, lastShownSleepDate)
    }

    private fun setupLottieListeners() {
        // Single permanent listener to apply accessories whenever a new animation file is loaded
        binding.petAnimationView.addLottieOnCompositionLoadedListener {
            refreshAccessoriesVisibility()
        }
    }

    private fun setupBackNavigation() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isCustomizeMode) {
                    toggleCustomizeMode(false)
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupRecyclerView() {
        shopAdapter = ShopItemAdapter { model ->
            handleItemClick(model)
        }
        binding.rvShopItems.adapter = shopAdapter
    }

    private fun handleItemClick(model: CompanionViewModel.ShopItemUiModel) {
        val userId = UserSession.getUserId(requireContext()) ?: return
        if (model.isOwned) {
            viewModel.equipItem(userId, model.item)
        } else if (model.canAfford) {
            showPurchaseConfirmation(model.item)
        } else {
            android.widget.Toast.makeText(requireContext(), "Insufficient Dream Tokens", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPurchaseConfirmation(item: com.noctra.app.data.model.ShopItem) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Purchase Item")
            .setMessage("Buy ${item.label} for ${item.tokenCost} Dream Tokens?")
            .setPositiveButton("Purchase") { _, _ ->
                val userId = UserSession.getUserId(requireContext()) ?: return@setPositiveButton
                viewModel.purchaseAndEquip(userId, item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupCategoryTabs() {
        val tabs = mapOf(
            "Hats" to binding.tabHats,
            "Outfits" to binding.tabOutfits,
            "Accessories" to binding.tabAccessories,
            "Footwear" to binding.tabFootwear
        )

        tabs.forEach { (category, textView) ->
            textView.setOnClickListener {
                updateTabs(category, tabs)
            }
        }
    }

    private fun updateTabs(selectedCategory: String, tabs: Map<String, TextView>) {
        currentCategory = selectedCategory
        tabs.forEach { (category, textView) ->
            if (category == selectedCategory) {
                textView.setBackgroundResource(R.drawable.bg_category_pill_active)
                textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            } else {
                textView.setBackgroundResource(R.drawable.bg_category_pill_inactive)
                textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.noctra_purple_light))
            }
        }
        filterItems()
    }

    private fun filterItems() {
        val allItems = viewModel.uiState.value.shopItems
        val dbCategory = when (currentCategory) {
            "Hats" -> "HAT"
            "Outfits" -> "OUTFIT"
            "Accessories" -> "ACCESSORY"
            "Footwear" -> "FOOTWEAR"
            else -> "HAT"
        }
        val filtered = allItems.filter { it.item.category == dbCategory }
        shopAdapter.submitList(filtered)

        if (filtered.isEmpty()) {
            binding.rvShopItems.visibility = View.GONE
            binding.tvComingSoon.visibility = View.VISIBLE
        } else {
            binding.rvShopItems.visibility = View.VISIBLE
            binding.tvComingSoon.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        val userId = UserSession.getUserId(requireContext()) ?: return
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

                // 2. Update Accessories State
                currentEquippedItems = state.equippedItems
                refreshAccessoriesVisibility()
                
                // 3. Update Shop Items if in customize mode
                if (isCustomizeMode) {
                    filterItems()
                }
            }
            
            if (state.error != null) {
                android.widget.Toast.makeText(requireContext(), state.error, android.widget.Toast.LENGTH_LONG).show()
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
        if (isPlayingTappedAnimation || isCustomizeMode) return
        
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

    private fun refreshAccessoriesVisibility() {
        val petView = binding.petAnimationView
        val equippedItems = currentEquippedItems
        
        val allHatLayers = listOf("hat_sleeping_hat", "hat_propeller_hat", "hat_floral_crown")
        val otherCategoryToLayer = mapOf(
            "OUTFIT" to "outfit_layer",
            "ACCESSORY" to "accessory_layer"
        )

        val activeHatLayer = equippedItems["HAT"]?.let { item ->
            when (item.itemAsset) {
                "hat_propeller_hat" -> "hat_propeller_hat"
                "hat_sleeping_hat" -> "hat_sleeping_hat"
                "hat_floral_crown" -> "hat_floral_crown"
                else -> null
            }
        }

        // Apply mutual exclusivity for hats
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

        // Apply visibility for other categories
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

    private fun setupListeners() {
        binding.btnCustomize.setOnClickListener {
            toggleCustomizeMode(true)
        }

        binding.btnBack.setOnClickListener {
            toggleCustomizeMode(false)
        }
        
        binding.petAnimationView.setOnClickListener {
            triggerTappedAnimation()
        }
    }

    private fun toggleCustomizeMode(enabled: Boolean) {
        isCustomizeMode = enabled
        
        val activityRoot = requireActivity().findViewById<ViewGroup>(R.id.mainActivityRoot) ?: return
        val bottomNav = requireActivity().findViewById<View>(R.id.bottom_nav)

        // Single coordinated transition for EVERYTHING (Activity root + Fragment contents)
        val consolidatedTransition = TransitionSet().apply {
            // Handle Bottom Nav: Slide + Fade to prevent "white bar"
            addTransition(Slide(Gravity.BOTTOM).addTarget(bottomNav))
            addTransition(Fade().addTarget(bottomNav))
            
            // Handle Fragment internal UI: Panels
            addTransition(Slide(Gravity.BOTTOM).addTarget(binding.shopPanel))
            addTransition(Fade(Fade.IN).addTarget(binding.shopPanel))
            addTransition(Fade(Fade.OUT).addTarget(binding.shopPanel))
            addTransition(Fade(Fade.OUT).addTarget(binding.companionPanel))
            addTransition(Fade(Fade.IN).addTarget(binding.companionPanel))
            
            // Smoothly animate the expansion/movement of everything else
            addTransition(ChangeBounds())
            
            ordering = TransitionSet.ORDERING_TOGETHER
            duration = 350
        }
        
        TransitionManager.beginDelayedTransition(activityRoot, consolidatedTransition)
        
        // Update Bottom Nav Visibility
        bottomNav?.visibility = if (enabled) View.GONE else View.VISIBLE

        if (enabled) {
            // Hide companion elements
            binding.companionPanel.visibility = View.GONE
            
            // Show shop elements
            binding.shopPanel.visibility = View.VISIBLE
            binding.btnBack.visibility = View.VISIBLE
            
            // Move Shleepy up
            val params = binding.shleepyFrame.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.verticalBias = 0.02f
            binding.shleepyFrame.layoutParams = params
            
            filterItems()
        } else {
            // Show companion elements
            binding.companionPanel.visibility = View.VISIBLE
            
            // Hide shop elements
            binding.shopPanel.visibility = View.GONE
            binding.btnBack.visibility = View.GONE
            
            // Move Shleepy back to center
            val params = binding.shleepyFrame.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.verticalBias = 0.4f
            binding.shleepyFrame.layoutParams = params
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Ensure Bottom Nav is restored if we leave while in customize mode
        if (isCustomizeMode) {
            requireActivity().findViewById<View>(R.id.bottom_nav)?.visibility = View.VISIBLE
        }
        currentStageLevel = -1
        isPlayingTappedAnimation = false
        _binding = null
    }
}
