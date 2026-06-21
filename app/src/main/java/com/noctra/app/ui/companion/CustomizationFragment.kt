package com.noctra.app.ui.companion

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.noctra.app.R
import com.noctra.app.databinding.FragmentCustomizationBinding
import com.noctra.app.ui.companion.adapters.ShopItemAdapter
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.launch

class CustomizationFragment : Fragment() {
    
    private var _binding: FragmentCustomizationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CustomizationViewModel by viewModels()
    private lateinit var adapter: ShopItemAdapter
    private var currentCategory = "Hats"
    private var currentStageLevel: Int = -1

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
        _binding = FragmentCustomizationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupCategoryTabs()
        setupListeners()
        observeUiState()

        val userId = UserSession.getUserId(requireContext()) ?: return
        viewModel.loadData(userId)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = ShopItemAdapter { model ->
            handleItemClick(model)
        }
        binding.rvShopItems.adapter = adapter
    }

    private fun handleItemClick(model: CustomizationViewModel.ShopItemUiModel) {
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
        val allItems = viewModel.uiState.value.items
        val dbCategory = when (currentCategory) {
            "Hats" -> "HAT"
            "Outfits" -> "OUTFIT"
            "Accessories" -> "ACCESSORY"
            "Footwear" -> "FOOTWEAR"
            else -> "HAT"
        }
        val filtered = allItems.filter { it.item.category == dbCategory }
        adapter.submitList(filtered)

        // Show/hide Coming Soon placeholder
        if (filtered.isEmpty()) {
            binding.rvShopItems.visibility = View.GONE
            binding.tvComingSoon.visibility = View.VISIBLE
        } else {
            binding.rvShopItems.visibility = View.VISIBLE
            binding.tvComingSoon.visibility = View.GONE
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.error != null) {
                        android.widget.Toast.makeText(requireContext(), state.error, android.widget.Toast.LENGTH_LONG).show()
                    }
                    binding.tvTokenBalance.text = state.tokenBalance.toString()
                    filterItems()
                    
                    // Update Shleepy Animation if stage changed
                    if (currentStageLevel != state.stageLevel) {
                        currentStageLevel = state.stageLevel
                        applyIdleAnimation()
                    }

                    updateShleepyPreview(state.equippedItems)
                }
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

    private fun updateShleepyPreview(equippedItems: Map<String, com.noctra.app.data.model.ShopItem>) {
        val petView = binding.petAnimationView
        
        // 1. Define all possible hat layers
        val allHatLayers = listOf("hat_sleeping_hat", "hat_propeller_hat", "hat_floral_crown")
        
        // 2. Map other categories
        val otherCategoryToLayer = mapOf(
            "OUTFIT" to "outfit_layer",
            "ACCESSORY" to "accessory_layer"
        )

        // 3. Identify active hat
        val activeHatLayer = equippedItems["HAT"]?.let { item ->
            when (item.itemAsset) {
                "hat_propeller_hat" -> "hat_propeller_hat"
                "hat_sleeping_hat" -> "hat_sleeping_hat"
                "hat_floral_crown" -> "hat_floral_crown"
                else -> null
            }
        }

        val applyOpacity = {
            // Handle Hats
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

            // Handle Other Categories
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

        // Apply on load and immediately
        petView.addLottieOnCompositionLoadedListener { applyOpacity() }
        applyOpacity()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentStageLevel = -1
        _binding = null
    }
}
