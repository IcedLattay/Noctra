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

    /**
     * Seeds the audit anchor at onboarding completion so the audit range
     * covers onboarding day instead of collapsing to [today].
     * Insert-or-fill-only: never rewinds an existing stamp (rewinding
     * would re-audit judged dates and double-count streaks/XP).
     */
    suspend fun ensureAuditAnchor(userId: String, anchorDate: String) {
        val now = java.time.OffsetDateTime.now().toString()
        val ledger = getRewardLedger(userId)
        if (ledger == null) {
            client.from("reward_ledger").insert(
                RewardLedger(
                    userId = userId,
                    lastSessionDate = anchorDate,
                    lastUpdated = now
                )
            )
        } else if (ledger.lastSessionDate == null) {
            updateRewardLedger(
                ledger.copy(lastSessionDate = anchorDate, lastUpdated = now)
            )
        }
    }

    suspend fun addXp(userId: String, amount: Int) {
        val ledger = getRewardLedger(userId) ?: return
        val updated = ledger.copy(totalXp = ledger.totalXp + amount)
        updateRewardLedger(updated)
    }
}
