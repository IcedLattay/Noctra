package com.noctra.app.data.repository

import com.noctra.app.data.model.Friendship
import com.noctra.app.data.model.UserProfile
import com.noctra.app.data.model.RoutineSession
import com.noctra.app.data.model.EncouragementReaction
import com.noctra.app.data.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

class FriendshipRepository {

    private val client = SupabaseClient.client

    // ─── Friend Request CRUD ────────────────────────────────────────────────

    suspend fun sendFriendRequest(requesterId: String, receiverEmail: String): Result<Unit> {
        return try {
            // 1. Look up receiver by email
            val receiver = client.from("user_profiles")
                .select {
                    filter { eq("email", receiverEmail) }
                    limit(1)
                }
                .decodeSingleOrNull<UserProfile>()

            if (receiver == null) {
                return Result.failure(Exception("No user found with that email address"))
            }

            if (receiver.userId == requesterId) {
                return Result.failure(Exception("You cannot add yourself as a friend"))
            }

            // 2. Check for existing friendship
            val existing = client.from("friendships")
                .select {
                    filter {
                        or {
                            and {
                                eq("requester_id", requesterId)
                                eq("receiver_id", receiver.userId)
                            }
                            and {
                                eq("requester_id", receiver.userId)
                                eq("receiver_id", requesterId)
                            }
                        }
                    }
                }
                .decodeList<Friendship>()

            val hasActiveFriendship = existing.any {
                it.status == "PENDING" || it.status == "ACCEPTED"
            }

            if (hasActiveFriendship) {
                return Result.failure(Exception("A friend request already exists with this user"))
            }

            // 3. Insert friend request
            client.from("friendships").insert(
                Friendship(
                    requesterId = requesterId,
                    receiverId = receiver.userId,
                    status = "PENDING"
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptFriendRequest(friendshipId: String) {
        client.from("friendships").update(
            mapOf("status" to "ACCEPTED")
        ) { filter { eq("id", friendshipId) } }
    }

    suspend fun declineFriendRequest(friendshipId: String) {
        client.from("friendships").update(
            mapOf("status" to "DECLINED")
        ) { filter { eq("id", friendshipId) } }
    }

    suspend fun cancelFriendRequest(friendshipId: String) {
        client.from("friendships").delete {
            filter { eq("id", friendshipId) }
        }
    }

    suspend fun removeFriend(friendshipId: String) {
        client.from("friendships").delete {
            filter { eq("id", friendshipId) }
        }
    }

    // ─── Friend Queries ─────────────────────────────────────────────────────

    suspend fun getAcceptedFriends(userId: String): List<FriendWithProfile> {
        val friendships = client.from("friendships")
            .select {
                filter {
                    or {
                        and {
                            eq("requester_id", userId)
                            eq("status", "ACCEPTED")
                        }
                        and {
                            eq("receiver_id", userId)
                            eq("status", "ACCEPTED")
                        }
                    }
                }
            }
            .decodeList<Friendship>()

        val friendIds = friendships.map { friendship ->
            if (friendship.requesterId == userId) friendship.receiverId else friendship.requesterId
        }

        if (friendIds.isEmpty()) return emptyList()

        // Fetch profiles for all friends
        val profiles = client.from("user_profiles")
            .select {
                filter {
                    isIn("user_id", friendIds)
                }
            }
            .decodeList<UserProfile>()

        // Fetch latest routine session for each friend to get streak
        val friendWithProfiles = mutableListOf<FriendWithProfile>()
        for (friendId in friendIds) {
            val profile = profiles.find { it.userId == friendId }
            val latestSession = client.from("routine_sessions")
                .select {
                    filter {
                        eq("user_id", friendId)
                        eq("status", "COMPLETED")
                    }
                    order("completion_timestamp", Order.DESCENDING)
                    limit(1)
                }
                .decodeSingleOrNull<RoutineSession>()

            // Calculate streak for this friend
            val streak = calculateStreak(friendId)

            friendWithProfiles.add(
                FriendWithProfile(
                    userId = friendId,
                    displayName = profile?.displayName ?: "Unknown",
                    currentStreak = streak,
                    lastCompletedTimestamp = latestSession?.completionTimestamp
                )
            )
        }

        return friendWithProfiles
    }

    private suspend fun calculateStreak(userId: String): Int {
        val allSessions = client.from("routine_sessions")
            .select {
                filter {
                    eq("user_id", userId)
                    eq("status", "COMPLETED")
                }
                order("session_date", Order.DESCENDING)
            }
            .decodeList<RoutineSession>()

        if (allSessions.isEmpty()) return 0

        val completedDates = allSessions.map { it.sessionDate }.toSet()
        val today = LocalDate.now().toString()
        var expectedDate = if (completedDates.contains(today)) {
            today
        } else {
            LocalDate.parse(today).minusDays(1).toString()
        }

        var streak = 0
        while (completedDates.contains(expectedDate)) {
            streak++
            expectedDate = LocalDate.parse(expectedDate).minusDays(1).toString()
        }

        return streak
    }

    suspend fun getPendingIncomingRequests(userId: String): List<FriendRequestWithProfile> {
        val friendships = client.from("friendships")
            .select {
                filter {
                    eq("receiver_id", userId)
                    eq("status", "PENDING")
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<Friendship>()

        val requesterIds = friendships.map { it.requesterId }
        if (requesterIds.isEmpty()) return emptyList()

        val profiles = client.from("user_profiles")
            .select {
                filter { isIn("user_id", requesterIds) }
            }
            .decodeList<UserProfile>()

        return friendships.map { friendship ->
            val profile = profiles.find { it.userId == friendship.requesterId }
            FriendRequestWithProfile(
                friendshipId = friendship.id ?: "",
                userId = friendship.requesterId,
                displayName = profile?.displayName ?: "Unknown",
                email = profile?.email ?: "",
                createdAt = friendship.createdAt ?: ""
            )
        }
    }

    suspend fun getPendingOutgoingRequests(userId: String): List<FriendRequestWithProfile> {
        val friendships = client.from("friendships")
            .select {
                filter {
                    eq("requester_id", userId)
                    eq("status", "PENDING")
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<Friendship>()

        val receiverIds = friendships.map { it.receiverId }
        if (receiverIds.isEmpty()) return emptyList()

        val profiles = client.from("user_profiles")
            .select {
                filter { isIn("user_id", receiverIds) }
            }
            .decodeList<UserProfile>()

        return friendships.map { friendship ->
            val profile = profiles.find { it.userId == friendship.receiverId }
            FriendRequestWithProfile(
                friendshipId = friendship.id ?: "",
                userId = friendship.receiverId,
                displayName = profile?.displayName ?: "Unknown",
                email = profile?.email ?: "",
                createdAt = friendship.createdAt ?: ""
            )
        }
    }

    suspend fun getPendingRequestCount(userId: String): Int {
        val result = client.from("friendships")
            .select {
                filter {
                    eq("receiver_id", userId)
                    eq("status", "PENDING")
                }
            }
            .decodeList<Friendship>()
        return result.size
    }

    // ─── Encouragement Reactions ────────────────────────────────────────────

    suspend fun sendReaction(senderId: String, receiverId: String): Result<Unit> {
        return try {
            val today = LocalDate.now().toString()

            // Check if already reacted today
            val existing = client.from("encouragement_reactions")
                .select {
                    filter {
                        eq("sender_id", senderId)
                        eq("receiver_id", receiverId)
                        eq("reaction_date", today)
                    }
                }
                .decodeList<EncouragementReaction>()

            if (existing.isNotEmpty()) {
                return Result.failure(Exception("You've already sent encouragement today"))
            }

            client.from("encouragement_reactions").insert(
                EncouragementReaction(
                    senderId = senderId,
                    receiverId = receiverId,
                    reactionDate = today
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun hasReactedToday(senderId: String, receiverId: String): Boolean {
        val today = LocalDate.now().toString()
        val result = client.from("encouragement_reactions")
            .select {
                filter {
                    eq("sender_id", senderId)
                    eq("receiver_id", receiverId)
                    eq("reaction_date", today)
                }
            }
            .decodeList<EncouragementReaction>()
        return result.isNotEmpty()
    }

    // ─── Realtime Subscriptions ─────────────────────────────────────────────

    fun subscribeToFriendshipChanges(userId: String): Flow<FriendshipChangeEvent> {
        val channel = client.realtime.channel("friendships:$userId")

        val friendshipFlow = channel.postgresChangeFlow<PostgrestAction>() {
            table = "friendships"
            filter = "requester_id=eq.$userId OR receiver_id=eq.$userId"
        }

        return friendshipFlow.map { event ->
            FriendshipChangeEvent(
                type = when (event) {
                    is PostgrestAction.Insert -> ChangeType.INSERT
                    is PostgrestAction.Update -> ChangeType.UPDATE
                    is PostgrestAction.Delete -> ChangeType.DELETE
                    else -> ChangeType.UPDATE
                },
                friendshipId = extractId(event)
            )
        }
    }

    fun subscribeToRoutineSessionChanges(friendIds: List<String>): Flow<RoutineSessionChangeEvent> {
        if (friendIds.isEmpty()) return kotlinx.coroutines.flow.emptyFlow()

        val channel = client.realtime.channel("leaderboard:${friendIds.joinToString(",")}")

        val sessionFlow = channel.postgresChangeFlow<PostgrestAction>() {
            table = "routine_sessions"
            filter = "user_id=in.(${friendIds.joinToString(",")})"
        }

        return sessionFlow.map { event ->
            RoutineSessionChangeEvent(
                type = when (event) {
                    is PostgrestAction.Insert -> ChangeType.INSERT
                    is PostgrestAction.Update -> ChangeType.UPDATE
                    else -> ChangeType.UPDATE
                },
                userId = extractUserId(event)
            )
        }
    }

    private fun extractId(event: PostgrestAction): String {
        return when (event) {
            is PostgrestAction.Insert -> event.record["id"]?.toString()?.trim('"') ?: ""
            is PostgrestAction.Update -> event.record["id"]?.toString()?.trim('"') ?: ""
            is PostgrestAction.Delete -> event.oldRecord["id"]?.toString()?.trim('"') ?: ""
            else -> ""
        }
    }

    private fun extractUserId(event: PostgrestAction): String {
        return when (event) {
            is PostgrestAction.Insert -> event.record["user_id"]?.toString()?.trim('"') ?: ""
            is PostgrestAction.Update -> event.record["user_id"]?.toString()?.trim('"') ?: ""
            else -> ""
        }
    }

    suspend fun subscribeToChannels(userId: String) {
        client.realtime.connect()
        client.realtime.channel("friendships:$userId").join()
        client.realtime.channel("leaderboard:$userId").join()
    }

    suspend fun unsubscribeFromChannels(userId: String) {
        try {
            client.realtime.removeChannel("friendships:$userId")
            client.realtime.removeChannel("leaderboard:$userId")
        } catch (e: Exception) {
            // Channel might not exist
        }
    }

    // ─── Data Classes ───────────────────────────────────────────────────────

    @Serializable
    data class FriendWithProfile(
        val userId: String,
        val displayName: String,
        val currentStreak: Int,
        val lastCompletedTimestamp: String?
    )

    @Serializable
    data class FriendRequestWithProfile(
        val friendshipId: String,
        val userId: String,
        val displayName: String,
        val email: String,
        val createdAt: String
    )

    data class FriendshipChangeEvent(
        val type: ChangeType,
        val friendshipId: String
    )

    data class RoutineSessionChangeEvent(
        val type: ChangeType,
        val userId: String
    )

    enum class ChangeType {
        INSERT, UPDATE, DELETE
    }
}
