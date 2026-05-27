package com.noctra.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShopItem(
    @SerialName("item_id") val itemId: String,
    val label: String,
    val description: String,
    val category: String,
    @SerialName("token_cost") val tokenCost: Int,
    @SerialName("preview_asset") val previewAsset: String,
    @SerialName("item_asset") val itemAsset: String,
    @SerialName("sort_order") val sortOrder: Int
)
