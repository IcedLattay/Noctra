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
    @SerialName("devolution_pending") val devolutionPending: Boolean = false,
    @SerialName("last_session_date") val lastSessionDate: String? = null,
    @SerialName("last_updated") val lastUpdated: String
)
