package com.noctra.app.domain.usecase

import com.noctra.app.data.repository.FriendshipRepository

class LeaderboardRankingUseCase {

    fun execute(
        friends: List<FriendshipRepository.FriendWithProfile>,
        userId: String,
        userProfile: FriendshipRepository.FriendWithProfile? = null
    ): LeaderboardResult {
        // Build entries from friends
        val allEntries = friends.toMutableList()

        // If the user isn't in the friends list (e.g. no friends yet),
        // add their own profile so they still appear at their natural rank
        if (userProfile != null && allEntries.none { it.userId == userId }) {
            allEntries.add(userProfile)
        }

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
                lastCompletedTimestamp = friend.lastCompletedTimestamp,
                friendshipId = friend.friendshipId
            )
        }

        // Find user's own entry (now part of the ranked list)
        val userEntry = rankedEntries.find { it.isCurrentUser }

        // SDD: natural position inside the top 10; pinned at the bottom
        // (top 9 friends + user) only when ranked outside the top 10
        val displayEntries = if (userEntry != null && userEntry.rank > 10) {
            rankedEntries.filter { !it.isCurrentUser }.take(9) + userEntry
        } else {
            rankedEntries.take(10)
        }

        return LeaderboardResult(
            entries = displayEntries,
            userRank = userEntry?.rank ?: 0,
            totalFriends = friends.size
        )
    }
}

data class LeaderboardEntry(
    val rank: Int,
    val userId: String,
    val displayName: String,
    val currentStreak: Int,
    val isCurrentUser: Boolean = false,
    val lastCompletedTimestamp: String? = null,
    val friendshipId: String = ""
)

data class LeaderboardResult(
    val entries: List<LeaderboardEntry>,
    val userRank: Int,
    val totalFriends: Int
)
