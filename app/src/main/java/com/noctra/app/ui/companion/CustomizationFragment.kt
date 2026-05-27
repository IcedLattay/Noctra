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
                    binding.tvTokenBalance.text = state.tokenBalance.toString()
                    filterItems()
                    
                    // Update base Shleepy image based on stage
                    val shleepyResId = when (state.stageLevel) {
                        1 -> R.drawable.shleepydepleted
                        2 -> R.drawable.shleepyawakening
                        3 -> R.drawable.shleepycharged
                        4 -> R.drawable.shleepyoverdrive
                        5 -> R.drawable.shleepyzenmaster
                        else -> R.drawable.shleepy
                    }
                    binding.ivShleepyBase.setImageResource(shleepyResId)

                    updateShleepyPreview(state.equippedItems)
                }
            }
        }
    }

    private fun updateShleepyPreview(equippedItems: Map<String, com.noctra.app.data.model.ShopItem>) {
        val categories = mapOf(
            "HAT" to binding.ivEquippedHat,
            "OUTFIT" to binding.ivEquippedOutfit,
            "ACCESSORY" to binding.ivEquippedAccessory
        )

        categories.forEach { (category, imageView) ->
            val item = equippedItems[category]
            if (item != null) {
                val resId = requireContext().resources.getIdentifier(
                    item.itemAsset, "drawable", requireContext().packageName
                )
                if (resId != 0) {
                    imageView.setImageResource(resId)
                    imageView.visibility = View.VISIBLE
                } else {
                    imageView.visibility = View.GONE
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
