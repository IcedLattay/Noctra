package com.noctra.app.data.repository

import com.noctra.app.data.model.RewardLedger
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

class RewardLedgerRepository {
    private val client = SupabaseClient.client

    suspend fun getRewardLedger(userId: String): RewardLedger? {
        return client.from("reward_ledger")
            .select {
                filter {
                    eq("user_id", userId)
                }
            }.decodeSingleOrNull<RewardLedger>()
    }

    suspend fun updateRewardLedger(rewardLedger: RewardLedger) {
        client.from("reward_ledger").update(rewardLedger) {
            filter {
                eq("user_id", rewardLedger.userId)
            }
        }
    }

    suspend fun addXp(userId: String, amount: Int) {
        val ledger = getRewardLedger(userId) ?: return
        val updated = ledger.copy(totalXp = ledger.totalXp + amount)
        updateRewardLedger(updated)
    }
}
