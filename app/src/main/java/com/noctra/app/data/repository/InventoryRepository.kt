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

    // Uncached batch read for friend avatars — never touches cachedInventory,
    // which strictly holds the current user's rows
    suspend fun getEquippedItemIds(userIds: List<String>): Map<String, List<String>> {
        if (userIds.isEmpty()) return emptyMap()
        return try {
            client.from("user_inventory")
                .select {
                    filter {
                        isIn("user_id", userIds)
                        eq("is_equipped", true)
                    }
                }
                .decodeList<UserInventoryItem>()
                .groupBy({ it.userId }, { it.itemId })
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun purchaseItem(userId: String, itemId: String) {
        val newItem = UserInventoryItem(
            id = java.util.UUID.randomUUID().toString(),
            userId = userId,
            itemId = itemId,
            purchasedAt = OffsetDateTime.now().toString(),
            isEquipped = false
        )
        client.from("user_inventory").insert(newItem)
        cachedInventory = null // Invalidate cache
    }

    suspend fun equipItem(userId: String, itemId: String, itemIdsInCategory: List<String>) {
        // 1. Unequip all items in this category for this user
        client.from("user_inventory").update({
            set("is_equipped", false)
        }) {
            filter {
                eq("user_id", userId)
                isIn("item_id", itemIdsInCategory)
            }
        }

        // 2. Equip the target item
        client.from("user_inventory").update({
            set("is_equipped", true)
        }) {
            filter {
                eq("user_id", userId)
                eq("item_id", itemId)
            }
        }
        cachedInventory = null // Invalidate cache
    }

    suspend fun unequipItem(userId: String, itemId: String) {
        client.from("user_inventory").update({
            set("is_equipped", false)
        }) {
            filter {
                eq("user_id", userId)
                eq("item_id", itemId)
            }
        }
        cachedInventory = null // Invalidate cache
    }
}
