package com.noctra.app.ui.social

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noctra.app.data.repository.FriendshipRepository
import com.noctra.app.data.repository.RewardLedgerRepository
import com.noctra.app.data.repository.SleepRecordRepository
import com.noctra.app.domain.usecase.LeaderboardRankingUseCase
import com.noctra.app.domain.usecase.LeaderboardEntry
import com.noctra.app.domain.usecase.LeaderboardResult
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SocialViewModel : ViewModel() {

    private val friendshipRepository = FriendshipRepository()
    private val rewardLedgerRepository = RewardLedgerRepository()
    private val sleepRecordRepository = SleepRecordRepository()
    private val leaderboardRankingUseCase = LeaderboardRankingUseCase()

    private var currentUserId: String? = null

    // ─── Leaderboard State ──────────────────────────────────────────────────

    private val _leaderboardState = MutableStateFlow(LeaderboardUiState())
    val leaderboardState: StateFlow<LeaderboardUiState> = _leaderboardState.asStateFlow()

    // ─── My Progress State ──────────────────────────────────────────────────

    private val _myProgressState = MutableStateFlow(MyProgressUiState())
    val myProgressState: StateFlow<MyProgressUiState> = _myProgressState.asStateFlow()

    // ─── Friend Requests State ──────────────────────────────────────────────

    private val _incomingRequests = MutableStateFlow<List<FriendRequestUiModel>>(emptyList())
    val incomingRequests: StateFlow<List<FriendRequestUiModel>> = _incomingRequests.asStateFlow()

    private val _outgoingRequests = MutableStateFlow<List<FriendRequestUiModel>>(emptyList())
    val outgoingRequests: StateFlow<List<FriendRequestUiModel>> = _outgoingRequests.asStateFlow()

    private val _pendingRequestCount = MutableStateFlow(0)
    val pendingRequestCount: StateFlow<Int> = _pendingRequestCount.asStateFlow()

    // ─── Action Results ─────────────────────────────────────────────────────

    private val _actionResult = MutableSharedFlow<ActionResult>(extraBufferCapacity = 1)
    val actionResult: SharedFlow<ActionResult> = _actionResult.asSharedFlow()

    // ─── Loading State ──────────────────────────────────────────────────────

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Subscribe to Realtime changes
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            try {
                friendshipRepository.subscribeToFriendshipChanges(userId).collect {
                    // On any friendship change, re-fetch all data
                    refreshAll(userId)
                }
            } catch (e: Exception) {
                // Realtime subscription failed, rely on manual refresh
            }
        }
    }

    // Clears per-user UI state so the next login never flashes the
    // previous account's data while fresh data loads
    fun onLogout() {
        currentUserId = null
        _leaderboardState.value = LeaderboardUiState()
        _myProgressState.value = MyProgressUiState()
        _incomingRequests.value = emptyList()
        _outgoingRequests.value = emptyList()
        _pendingRequestCount.value = 0
        _isLoading.value = false
    }

    // ─── Data Loading ───────────────────────────────────────────────────────

    fun loadAll(context: Context) {
        val userId = UserSession.getUserId(context) ?: return
        currentUserId = userId

        viewModelScope.launch {
            _isLoading.value = true
            try {
                refreshAll(userId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun refreshAll(userId: String) {
        // Load all data concurrently
        coroutineScope {
            awaitAll(
                async { loadLeaderboard(userId) },
                async { loadMyProgress(userId) },
                async { loadPendingRequests(userId) }
            )
        }
    }

    private suspend fun loadLeaderboard(userId: String) {
        try {
            val friends = friendshipRepository.getAcceptedFriends(userId)
            val ownProfile = friendshipRepository.getOwnProfile(userId)
            val result = leaderboardRankingUseCase.execute(friends, userId, ownProfile)

            _leaderboardState.value = LeaderboardUiState(
                entries = result.entries.map { entry ->
                    LeaderboardEntryUiModel(
                        userId = entry.userId,
                        rank = entry.rank,
                        displayName = entry.displayName,
                        currentStreak = entry.currentStreak,
                        isCurrentUser = entry.isCurrentUser,
                        isTopThree = entry.rank <= 3,
                        friendshipId = entry.friendshipId
                    )
                },
                userRank = result.userRank,
                totalFriends = result.totalFriends,
                hasFriends = friends.isNotEmpty()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadMyProgress(userId: String) {
        try {
            val ledger = rewardLedgerRepository.getRewardLedger(userId)
            val currentStreak = ledger?.currentStreak ?: 0

            // Get last night's sleep score
            val yesterday = java.time.LocalDate.now().minusDays(1).toString()
            val lastNightRecord = sleepRecordRepository.getSleepRecordForDate(userId, yesterday)
            val lastNightScore = lastNightRecord?.compositeScore

            // Calculate this week's completion rate
            val weekStart = java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY).toString()
            val weekEnd = java.time.LocalDate.now().toString()
            val weekSessions = com.noctra.app.data.repository.RoutineSessionRepository()
                .getSessionsInRange(userId, weekStart, weekEnd)
            val completedThisWeek = weekSessions.count { it.status == "COMPLETED" }
            val completionRate = if (weekSessions.isNotEmpty()) {
                (completedThisWeek.toFloat() / 7 * 100).toInt()
            } else {
                0
            }

            _myProgressState.value = MyProgressUiState(
                currentStreak = currentStreak,
                lastNightScore = lastNightScore,
                weeklyCompletionRate = completionRate
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadPendingRequests(userId: String) {
        try {
            val incoming = friendshipRepository.getPendingIncomingRequests(userId)
            val outgoing = friendshipRepository.getPendingOutgoingRequests(userId)

            _incomingRequests.value = incoming.map { request ->
                FriendRequestUiModel(
                    friendshipId = request.friendshipId,
                    userId = request.userId,
                    displayName = request.displayName,
                    email = request.email,
                    isIncoming = true
                )
            }

            _outgoingRequests.value = outgoing.map { request ->
                FriendRequestUiModel(
                    friendshipId = request.friendshipId,
                    userId = request.userId,
                    displayName = request.displayName,
                    email = request.email,
                    isIncoming = false
                )
            }

            _pendingRequestCount.value = incoming.size
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ─── Friend Request Actions ─────────────────────────────────────────────

    // Direct call (no shared-flow event) — the bottom sheet is the only
    // caller and surfaces the result inline, so nothing should toast
    suspend fun sendFriendRequest(context: Context, email: String): Result<Unit> {
        val userId = UserSession.getUserId(context)
            ?: return Result.failure(Exception("Not logged in"))

        _isLoading.value = true
        return try {
            val result = friendshipRepository.sendFriendRequest(userId, email)
            if (result.isSuccess) {
                // Refresh outgoing requests
                loadPendingRequests(userId)
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    fun acceptRequest(friendshipId: String) {
        val userId = currentUserId ?: return

        viewModelScope.launch {
            try {
                friendshipRepository.acceptFriendRequest(friendshipId)
                _actionResult.emit(ActionResult.Success("Friend request accepted!"))
                refreshAll(userId)
            } catch (e: Exception) {
                _actionResult.emit(ActionResult.Error("Failed to accept request"))
            }
        }
    }

    fun declineRequest(friendshipId: String) {
        val userId = currentUserId ?: return

        viewModelScope.launch {
            try {
                friendshipRepository.declineFriendRequest(friendshipId)
                _actionResult.emit(ActionResult.Success("Request declined"))
                loadPendingRequests(userId)
            } catch (e: Exception) {
                _actionResult.emit(ActionResult.Error("Failed to decline request"))
            }
        }
    }

    fun cancelRequest(friendshipId: String) {
        val userId = currentUserId ?: return

        viewModelScope.launch {
            try {
                friendshipRepository.cancelFriendRequest(friendshipId)
                _actionResult.emit(ActionResult.Success("Request cancelled"))
                loadPendingRequests(userId)
            } catch (e: Exception) {
                _actionResult.emit(ActionResult.Error("Failed to cancel request"))
            }
        }
    }

    fun removeFriend(friendshipId: String) {
        val userId = currentUserId ?: return

        viewModelScope.launch {
            try {
                friendshipRepository.removeFriend(friendshipId)
                _actionResult.emit(ActionResult.Success("Friend removed"))
                refreshAll(userId)
            } catch (e: Exception) {
                _actionResult.emit(ActionResult.Error("Failed to remove friend"))
            }
        }
    }

    // ─── Encouragement ──────────────────────────────────────────────────────

    fun sendEncouragement(context: Context, friendUserId: String) {
        val userId = UserSession.getUserId(context) ?: return

        viewModelScope.launch {
            try {
                val result = friendshipRepository.sendReaction(userId, friendUserId)
                if (result.isSuccess) {
                    _actionResult.emit(ActionResult.Success("Encouragement sent!"))
                } else {
                    _actionResult.emit(ActionResult.Error(
                        result.exceptionOrNull()?.message ?: "Already sent today"
                    ))
                }
            } catch (e: Exception) {
                _actionResult.emit(ActionResult.Error("Failed to send encouragement"))
            }
        }
    }

    // ─── UI State Classes ───────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        currentUserId?.let { userId ->
            viewModelScope.launch {
                try {
                    friendshipRepository.unsubscribeFromChannels(userId)
                } catch (e: Exception) {
                    // Ignore cleanup errors
                }
            }
        }
    }
}

// ─── UI Models ──────────────────────────────────────────────────────────────

data class LeaderboardUiState(
    val entries: List<LeaderboardEntryUiModel> = emptyList(),
    val userRank: Int = 0,
    val totalFriends: Int = 0,
    val hasFriends: Boolean = false
)

data class LeaderboardEntryUiModel(
    val userId: String = "",
    val rank: Int,
    val displayName: String,
    val currentStreak: Int,
    val isCurrentUser: Boolean = false,
    val isTopThree: Boolean = false,
    val isPlaceholder: Boolean = false,
    val friendshipId: String = ""
)

data class MyProgressUiState(
    val currentStreak: Int = 0,
    val lastNightScore: Int? = null,
    val weeklyCompletionRate: Int = 0
)

data class FriendRequestUiModel(
    val friendshipId: String,
    val userId: String,
    val displayName: String,
    val email: String,
    val isIncoming: Boolean
)

sealed class ActionResult {
    data class Success(val message: String) : ActionResult()
    data class Error(val message: String) : ActionResult()
}
