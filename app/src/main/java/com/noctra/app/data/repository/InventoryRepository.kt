package com.noctra.app.data.repository

import com.noctra.app.data.model.UserInventoryItem
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import java.time.OffsetDateTime

class InventoryRepository {
    private val client = SupabaseClient.client

    companion object {
        private var cachedUserId: String? = null
        private var cachedInventory: List<UserInventoryItem>? = null

        fun clearCache() {
            cachedUserId = null
            cachedInventory = null
        }
    }

    suspend fun getUserInventory(userId: String): List<UserInventoryItem> {
        if (cachedUserId == userId) {
            cachedInventory?.let { return it }
        }

        return try {
            val inventory = client.from("user_inventory")
                .select { filter { eq("user_id", userId) } }
                .decodeList<UserInventoryItem>()
            cachedUserId = userId
            cachedInventory = inventory
            inventory
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun purchaseItem(userId: String, itemId: String) {
        val newItem = UserInventoryItem(
            id = java.util.UUID.randomUUID().toString(),
            userId = userId,
            itemId = itemId,
            purchasedAt = OffsetDateTime.now().toString()
        )
        client.from("user_inventory").insert(newItem)
        cachedInventory = null
    }

    /**
     * Read the currently equipped outfit from user_profiles.outfit_equipped.
     * Returns Map<userId, itemId> for batch friend-avatar lookups.
     */
    suspend fun getEquippedOutfits(userIds: List<String>): Map<String, String> {
        if (userIds.isEmpty()) return emptyMap()
        return try {
            val result = client.from("user_profiles")
                .select {
                    filter { isIn("user_id", userIds) }
                }
                .decodeList<Map<String, Any?>>()
            result.mapNotNull { row ->
                val uid = row["user_id"] as? String ?: return@mapNotNull null
                val outfitId = row["outfit_equipped"] as? String ?: return@mapNotNull null
                uid to outfitId
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Set the equipped outfit on user_profiles.outfit_equipped.
     */
    suspend fun setEquippedOutfit(userId: String, itemId: String) {
        client.from("user_profiles").update({
            set("outfit_equipped", itemId)
        }) {
            filter { eq("user_id", userId) }
        }
    }
}
