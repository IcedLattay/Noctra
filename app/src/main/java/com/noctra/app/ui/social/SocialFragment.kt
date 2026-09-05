package com.noctra.app.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.noctra.app.R
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class SocialFragment : Fragment(R.layout.fragment_social) {

    private val viewModel: SocialViewModel by viewModels()
    private lateinit var leaderboardAdapter: LeaderboardAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<ImageView>(R.id.btn_back)
        val btnFriendRequestsBell = view.findViewById<ImageView>(R.id.btn_friend_requests_bell)
        val badgeContainer = view.findViewById<LinearLayout>(R.id.badge_container)
        val textBadgeCount = view.findViewById<TextView>(R.id.text_badge_count)
        val recyclerLeaderboard = view.findViewById<RecyclerView>(R.id.recycler_leaderboard)
        val emptyState = view.findViewById<LinearLayout>(R.id.empty_state)
        val btnAddByEmail = view.findViewById<MaterialButton>(R.id.btn_add_by_email)
        val btnFriendRequestsBottom = view.findViewById<MaterialButton>(R.id.btn_friend_requests)

        // Setup back button
        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // Setup friend requests button
        btnFriendRequestsBell.setOnClickListener {
            findNavController().navigate(R.id.action_social_to_friendRequests)
        }
        btnFriendRequestsBottom.setOnClickListener {
            findNavController().navigate(R.id.action_social_to_friendRequests)
        }

        // Setup add by email button
        btnAddByEmail.setOnClickListener {
            val bottomSheet = AddFriendBottomSheet()
            bottomSheet.show(parentFragmentManager, "add_friend")
        }

        // Setup leaderboard RecyclerView
        leaderboardAdapter = LeaderboardAdapter { entry ->
            viewModel.sendEncouragement(requireContext(), entry.userId)
        }
        recyclerLeaderboard.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = leaderboardAdapter
        }

        // Load data
        viewModel.loadAll(requireContext())

        // Observe leaderboard state
        lifecycleScope.launch {
            viewModel.leaderboardState.collect { state ->
                if (state.hasFriends) {
                    recyclerLeaderboard.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE
                    leaderboardAdapter.submitList(state.entries)
                } else {
                    recyclerLeaderboard.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                }
            }
        }

        // Observe pending request count for badge
        lifecycleScope.launch {
            viewModel.pendingRequestCount.collect { count ->
                if (count > 0) {
                    badgeContainer.visibility = View.VISIBLE
                    textBadgeCount.text = count.toString()
                } else {
                    badgeContainer.visibility = View.GONE
                }
            }
        }

        // Observe action results
        lifecycleScope.launch {
            viewModel.actionResult.collect { result ->
                when (result) {
                    is ActionResult.Success -> {
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                        viewModel.clearActionResult()
                    }
                    is ActionResult.Error -> {
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                        viewModel.clearActionResult()
                    }
                    null -> {}
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAll(requireContext())
    }
}
