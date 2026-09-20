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
            binding.priceContainer.visibility = View.GONE
            binding.shopItemContent.setBackgroundResource(R.drawable.bg_shop_item_card)

            // 2. Set outfit preview drawable
            val drawableName = "outfit_${model.item.label.lowercase()}"
            val ctx = binding.root.context
            val drawableRes = ctx.resources.getIdentifier(
                drawableName, "drawable", ctx.packageName
            )
            if (drawableRes != 0) {
                binding.ivOutfitPreview.setImageResource(drawableRes)
                binding.ivOutfitPreview.visibility = View.VISIBLE
            } else {
                binding.ivOutfitPreview.visibility = View.GONE
            }

            // 3. Set state based on ownership
            if (model.isEquipped) {
                binding.shopItemContent.setBackgroundResource(R.drawable.bg_shop_item_card_equipped)
            } else if (!model.isOwned) {
                binding.priceContainer.visibility = View.VISIBLE
                binding.tvTokenCost.text = model.item.tokenCost.toString()
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
