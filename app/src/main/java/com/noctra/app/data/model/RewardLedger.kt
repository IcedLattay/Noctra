package com.noctra.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RewardLedger(
    @SerialName("user_id") val userId: String,
    @SerialName("token_balance") val tokenBalance: Int = 0,
    @SerialName("total_xp") val totalXp: Int = 0,
    @SerialName("current_streak") val currentStreak: Int = 0,
    @SerialName("longest_streak") val longestStreak: Int = 0,
    // RENAMED per DB Migration 2 (8/28/26): devolution_pending -> has_first_miss.
    // Old column no longer exists — any code still referencing
    // devolutionPending will fail to compile, which is intentional (forces
    // every call site to be checked rather than silently pointing at a
    // dropped column).
    @SerialName("has_first_miss") val hasFirstMiss: Boolean = false,
    @SerialName("last_session_date") val lastSessionDate: String? = null,
    @SerialName("last_updated") val lastUpdated: String
)