package com.noctra.app.data.repository

import com.noctra.app.data.model.ShopItem
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

class ShopRepository {
    private val client = SupabaseClient.client

    suspend fun getAllShopItems(): List<ShopItem> {
        return client.from("shop_items")
            .select()
            .decodeList<ShopItem>()
    }

    suspend fun getShopItemsByCategory(category: String): List<ShopItem> {
        return client.from("shop_items")
            .select {
                filter {
                    eq("category", category)
                }
            }.decodeList<ShopItem>()
    }
}
