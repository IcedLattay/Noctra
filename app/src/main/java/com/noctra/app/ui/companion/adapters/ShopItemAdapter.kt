package com.noctra.app.ui.companion.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noctra.app.R
import com.noctra.app.databinding.ItemShopBinding
import com.noctra.app.ui.companion.CompanionViewModel.ShopItemUiModel

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
            
            // 1. Reset defaults for recycling
            binding.ivEquippedCheck.visibility = View.GONE
            binding.priceContainer.visibility = View.GONE
            
            // Reset the internal layout border
            binding.shopItemContent.background = null
            
            // Reset card elevation and shadow color
            binding.btnItemCard.apply {
                elevation = 2f * resources.displayMetrics.density
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    outlineAmbientShadowColor = android.graphics.Color.BLACK
                    outlineSpotShadowColor = android.graphics.Color.BLACK
                }
            }

            // 2. Set State based on ownership
            if (model.isEquipped) {
                // Owned & Equipped: Show Checkmark + Purple Border + Purple Glow
                binding.ivEquippedCheck.visibility = View.VISIBLE
                
                // Set the purple border on the INNER content
                binding.shopItemContent.setBackgroundResource(R.drawable.bg_shop_item_card_equipped)
                
                // Set the purple glow on the OUTER card
                binding.btnItemCard.apply {
                    elevation = 6f * resources.displayMetrics.density
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        val purple = ContextCompat.getColor(context, R.color.noctra_purple_soft)
                        outlineAmbientShadowColor = purple
                        outlineSpotShadowColor = purple
                    }
                }
            } else if (model.isOwned) {
                // Owned but not equipped: Standard clean card
            } else {
                // NOT OWNED: Show Price
                binding.priceContainer.visibility = View.VISIBLE
                binding.tvTokenCost.text = model.item.tokenCost.toString()
            }

            // 3. Icon Loading
            val context = binding.root.context
            val assetName = model.item.previewAsset.removeSuffix(".png").removeSuffix(".jpg").removeSuffix(".webp")
            val resId = context.resources.getIdentifier(
                assetName, "drawable", context.packageName
            )
            
            if (resId != 0) {
                binding.ivItemPreview.setImageResource(resId)
            } else {
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
