package com.noctra.app.ui.social

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.noctra.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LeaderboardFragment : Fragment(R.layout.fragment_leaderboard) {

    private val viewModel: SocialViewModel by activityViewModels()
    private lateinit var leaderboardAdapter: LeaderboardAdapter
    private var scrolledToTopOnLoad = false
    private var refreshTimeoutJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<View>(R.id.btn_back)
        val recyclerLeaderboard = view.findViewById<RecyclerView>(R.id.recycler_leaderboard)
        val emptyState = view.findViewById<LinearLayout>(R.id.empty_state)

        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        leaderboardAdapter = LeaderboardAdapter()
        recyclerLeaderboard.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = leaderboardAdapter
        }

        val swipeRefresh = view.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh)
        swipeRefresh.setOnRefreshListener {
            viewModel.loadAll(requireContext())
            // Safety cap: never spin longer than 10s on a stalled load
            refreshTimeoutJob?.cancel()
            refreshTimeoutJob = lifecycleScope.launch {
                delay(10_000)
                swipeRefresh.isRefreshing = false
            }
        }

        viewModel.loadAll(requireContext())

        lifecycleScope.launch {
            viewModel.isLoading.collect { loading ->
                swipeRefresh.isRefreshing = loading
                if (!loading) refreshTimeoutJob?.cancel()
            }
        }

        lifecycleScope.launch {
            viewModel.leaderboardState.collect { state ->
                val base = state.entries
                if (base.isNotEmpty()) {
                    recyclerLeaderboard.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE
                    leaderboardAdapter.submitList(padWithPlaceholders(base)) {
                        // Data arrives in bursts while the list is still doing its
                        // first layout, which can leave it anchored at the bottom.
                        // Pin to top once; later refreshes keep the user's scroll.
                        if (!scrolledToTopOnLoad) {
                            scrolledToTopOnLoad = true
                            recyclerLeaderboard.scrollToPosition(0)
                        }
                    }
                } else {
                    recyclerLeaderboard.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAll(requireContext())
    }

    // Fill empty slots up to 10 with grey placeholder cards
    private fun padWithPlaceholders(
        entries: List<LeaderboardEntryUiModel>
    ): List<LeaderboardEntryUiModel> {
        val missing = (10 - entries.size).coerceAtLeast(0)
        if (missing == 0) return entries
        val placeholders = List(missing) { index ->
            LeaderboardEntryUiModel(
                userId = "placeholder-$index",
                rank = 0,
                displayName = "",
                currentStreak = 0,
                isPlaceholder = true
            )
        }
        return entries + placeholders
    }
}
