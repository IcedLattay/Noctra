package com.noctra.app.domain.usecase

import com.noctra.app.data.repository.FriendshipRepository

class LeaderboardRankingUseCase {

    fun execute(
        friends: List<FriendshipRepository.FriendWithProfile>,
        userId: String
    ): LeaderboardResult {
        // Add user to the list with their own data
        val allEntries = friends.toMutableList()

        // Sort by current streak descending, then by last completed timestamp (most recent first)
        val sorted = allEntries.sortedWith(
            compareByDescending<FriendshipRepository.FriendWithProfile> { it.currentStreak }
                .thenByDescending { it.lastCompletedTimestamp ?: "" }
        )

        // Assign ranks
        val rankedEntries = sorted.mapIndexed { index, friend ->
            LeaderboardEntry(
                rank = index + 1,
                userId = friend.userId,
                displayName = friend.displayName,
                currentStreak = friend.currentStreak,
                isCurrentUser = friend.userId == userId,
                lastCompletedTimestamp = friend.lastCompletedTimestamp
            )
        }

        // Get top 10
        val top10 = rankedEntries.take(10)

        // Find user's own rank
        val userEntry = rankedEntries.find { it.isCurrentUser }

        // If user is outside top 10, pin them at position 10
        val displayEntries = if (userEntry != null && userEntry.rank > 10) {
            top10 + userEntry.copy(rank = userEntry.rank)
        } else {
            top10
        }

        return LeaderboardResult(
            entries = displayEntries,
            userRank = userEntry?.rank ?: 0,
            totalFriends = rankedEntries.size
        )
    }
}

data class LeaderboardEntry(
    val rank: Int,
    val userId: String,
    val displayName: String,
    val currentStreak: Int,
    val isCurrentUser: Boolean = false,
    val lastCompletedTimestamp: String? = null
)

data class LeaderboardResult(
    val entries: List<LeaderboardEntry>,
    val userRank: Int,
    val totalFriends: Int
)
