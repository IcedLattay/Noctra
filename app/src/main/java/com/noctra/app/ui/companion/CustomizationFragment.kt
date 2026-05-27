package com.noctra.app.ui.companion

import android.os.Bundle
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

        val userId = UserSession.getUserId(requireContext())
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
        val userId = UserSession.getUserId(requireContext())
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
                val userId = UserSession.getUserId(requireContext())
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
                    
                    // Update base Shleepy image
                    val shleepyResId = when (state.stageLevel) {
                        1 -> R.drawable.shleepy_depleted
                        2 -> R.drawable.shleepy_awakening
                        3 -> R.drawable.shleepy_charged
                        4 -> R.drawable.shleepy_overdrive
                        5 -> R.drawable.shleepy_zenmaster
                        else -> R.drawable.shleepy_depleted
                    }
                    binding.ivShleepyBase.setImageResource(shleepyResId)

                    updateShleepyPreview(state.equippedItems, state.stageLevel)
                }
            }
        }
    }

    private fun updateShleepyPreview(equippedItems: Map<String, com.noctra.app.data.model.ShopItem>, stageLevel: Int) {
        val categories = mapOf(
            "HAT" to binding.ivEquippedHat,
            "OUTFIT" to binding.ivEquippedOutfit,
            "ACCESSORY" to binding.ivEquippedAccessory
        )

        val stageSuffix = when (stageLevel) {
            1 -> "depleted"
            2 -> "awakening"
            3 -> "charged"
            4 -> "overdrive"
            5 -> "zenmaster"
            else -> "depleted"
        }

        categories.forEach { (category, imageView) ->
            val item = equippedItems[category]
            if (item != null) {
                val cleanAsset = item.itemAsset.removeSuffix(".png").removeSuffix(".jpg").removeSuffix(".webp")
                val assetName = "${cleanAsset}_$stageSuffix"
                val resId = requireContext().resources.getIdentifier(
                    assetName, "drawable", requireContext().packageName
                )
                if (resId != 0) {
                    imageView.setImageResource(resId)
                    imageView.visibility = View.VISIBLE
                } else {
                    // fallback to base asset
                    val fallbackId = requireContext().resources.getIdentifier(
                        cleanAsset, "drawable", requireContext().packageName
                    )
                    if (fallbackId != 0) {
                        imageView.setImageResource(fallbackId)
                        imageView.visibility = View.VISIBLE
                    } else {
                        imageView.visibility = View.GONE
                    }
                }
            } else {
                imageView.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
