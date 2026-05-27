package com.noctra.app.data.repository

import com.noctra.app.data.model.UserInventoryItem
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import java.time.OffsetDateTime

class InventoryRepository {
    private val client = SupabaseClient.client

    suspend fun getUserInventory(userId: String): List<UserInventoryItem> {
        return client.from("user_inventory")
            .select { filter { eq("user_id", userId) } }
            .decodeList<UserInventoryItem>()
    }

    suspend fun purchaseItem(userId: String, itemId: String) {
        val newItem = mapOf(
            "user_id" to userId,
            "item_id" to itemId,
            "purchased_at" to OffsetDateTime.now().toString(),
            "is_equipped" to false
        )
        client.from("user_inventory").insert(newItem)
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
    }
}
