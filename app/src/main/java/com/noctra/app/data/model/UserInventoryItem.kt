package com.noctra.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserInventoryItem(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("purchased_at") val purchasedAt: String,
    @SerialName("is_equipped") val isEquipped: Boolean = false
)
