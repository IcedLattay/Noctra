package com.noctra.app.ui.companion.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noctra.app.databinding.ItemShopBinding
import com.noctra.app.ui.companion.CustomizationViewModel.ShopItemUiModel

class ShopItemAdapter(
    private val onItemClick: (ShopItemUiModel) -> Unit
) : ListAdapter<ShopItemUiModel, ShopItemAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemShopBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemShopBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(model: ShopItemUiModel) {
            
            // Handle Equipped state
            if (model.isEquipped) {
                binding.ivEquippedCheck.visibility = View.VISIBLE
                binding.priceContainer.visibility = View.GONE
            } else if (model.isOwned) {
                binding.ivEquippedCheck.visibility = View.GONE
                binding.priceContainer.visibility = View.GONE
            } else {
                binding.ivEquippedCheck.visibility = View.GONE
                binding.priceContainer.visibility = View.VISIBLE
                binding.tvTokenCost.text = model.item.tokenCost.toString()
                
                binding.btnItemCard.alpha = if (model.canAfford) 1.0f else 0.5f
            }

            // Robust Icon Loading
            val context = binding.root.context
            val assetName = model.item.previewAsset.removeSuffix(".png").removeSuffix(".jpg").removeSuffix(".webp")
            val resId = context.resources.getIdentifier(
                assetName, "drawable", context.packageName
            )
            
            if (resId != 0) {
                binding.ivItemPreview.setImageResource(resId)
            } else {
                // Set a placeholder if the specific icon is missing to avoid a blank card
                binding.ivItemPreview.setImageResource(android.R.drawable.ic_menu_help)
            }

            binding.root.setOnClickListener { onItemClick(model) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<ShopItemUiModel>() {
        override fun areItemsTheSame(oldItem: ShopItemUiModel, newItem: ShopItemUiModel): Boolean {
            return oldItem.item.itemId == newItem.item.itemId
        }
        override fun areContentsTheSame(oldItem: ShopItemUiModel, newItem: ShopItemUiModel): Boolean {
            return oldItem == newItem
        }
    }
}
