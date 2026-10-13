package com.noctra.app.data.repository

import com.noctra.app.data.model.ShopItem
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

class ShopRepository {
    private val client = SupabaseClient.client

    companion object {
        private var cachedItems: List<ShopItem>? = null
    }

    suspend fun getAllShopItems(): List<ShopItem> {
        cachedItems?.let { return it }
        
        return try {
            val items = client.from("shop_items")
                .select()
                .decodeList<ShopItem>()
            cachedItems = items
            items
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getShopItemsByCategory(category: String): List<ShopItem> {
        val allItems = getAllShopItems()
        return allItems.filter { it.category == category }
    }
}
