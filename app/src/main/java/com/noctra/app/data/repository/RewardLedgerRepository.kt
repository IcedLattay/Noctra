package com.noctra.app.data.repository

import com.noctra.app.data.model.RewardLedger
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

    /**
     * Writes the ledger and, if the write actually moved the streak or XP,
     * appends the matching `app_log` row. Every path in the app funnels
     * through here (routine completion, morning settlement, the miss
     * penalty, debug tools), so this is the single place that has to know
     * the wording.
     *
     * @param logParentId id of the parent log row when this change was
     *   caused by something else being logged (e.g. the 9:30 checker run),
     *   so the panel nests it underneath.
     */
    suspend fun updateRewardLedger(
        rewardLedger: RewardLedger,
        logParentId: String? = null
    ) {
        // Read the "before" picture first — deltas are the whole point of
        // the log line, and there is no way to recover them after the write.
        val previous = runCatching { getRewardLedger(rewardLedger.userId) }.getOrNull()

        client.from("reward_ledger").update(rewardLedger) {
            filter {
                eq("user_id", rewardLedger.userId)
            }
        }

        if (previous == null) return
        val userId = rewardLedger.userId

        if (rewardLedger.currentStreak != previous.currentStreak) {
            // 0 = wiped; any decrease = the chain broke and restarted.
            val reset = rewardLedger.currentStreak < previous.currentStreak ||
                rewardLedger.currentStreak == 0
            AppLogRepository.log(
                userId = userId,
                runType = "streak_change",
                eventDetail = if (reset) {
                    "System reset the streak."
                } else {
                    "System bumped the streak to ${rewardLedger.currentStreak}."
                },
                details = buildJsonObject {
                    put("streak_before", previous.currentStreak)
                    put("streak_after", rewardLedger.currentStreak)
                },
                parentId = logParentId
            )
        }

        if (rewardLedger.totalXp != previous.totalXp) {
            val delta = rewardLedger.totalXp - previous.totalXp
            AppLogRepository.log(
                userId = userId,
                runType = "xp_change",
                eventDetail = if (delta > 0) {
                    "System added ${delta}xp, new total is ${rewardLedger.totalXp}xp."
                } else {
                    "System deducted ${-delta}xp. new total is ${rewardLedger.totalXp}xp."
                },
                details = buildJsonObject {
                    put("xp_before", previous.totalXp)
                    put("xp_after", rewardLedger.totalXp)
                    put("xp_delta", delta)
                },
                parentId = logParentId
            )
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
