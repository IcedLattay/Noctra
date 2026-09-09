package com.noctra.app.ui.social

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.noctra.app.R
import kotlinx.coroutines.launch

class SocialFragment : Fragment(R.layout.fragment_social) {

    private val viewModel: SocialViewModel by activityViewModels()
    private lateinit var friendAdapter: FriendAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<View>(R.id.btn_back)
        val btnAddByEmail = view.findViewById<TextView>(R.id.btn_add_by_email)
        val recyclerFriends = view.findViewById<RecyclerView>(R.id.recycler_friends)

        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        btnAddByEmail.setOnClickListener {
            AddFriendBottomSheet().show(parentFragmentManager, "add_friend")
        }

        friendAdapter = FriendAdapter(
            onRemoveClick = { friend -> confirmRemoveFriend(friend) },
            onBannerClick = {
                findNavController().navigate(R.id.action_social_to_friendRequests)
            }
        )
        recyclerFriends.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = friendAdapter
        }

        val swipeRefresh = view.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh)
        swipeRefresh.setOnRefreshListener {
            viewModel.loadAll(requireContext())
        }

        viewModel.loadAll(requireContext())

        lifecycleScope.launch {
            viewModel.isLoading.collect { loading ->
                swipeRefresh.isRefreshing = loading
            }
        }

        // Avatar equipment -> adapter
        lifecycleScope.launch {
            viewModel.avatarEquipment.collect { map ->
                friendAdapter.equipment = map
                friendAdapter.notifyDataSetChanged()
            }
        }

        // Friend list (exclude own card and leaderboard placeholders).
        // The list always shows: the banner header must stay reachable
        // even with zero friends.
        lifecycleScope.launch {
            viewModel.leaderboardState.collect { state ->
                val friends = state.entries.filter { !it.isCurrentUser && !it.isPlaceholder }
                friendAdapter.submitList(friends)
            }
        }

        // Pending request badge on the banner (capped at 9+)
        lifecycleScope.launch {
            viewModel.pendingRequestCount.collect { count ->
                friendAdapter.setPendingBadge(count)
            }
        }

        // Action result toasts
        lifecycleScope.launch {
            viewModel.actionResult.collect { result ->
                when (result) {
                    is ActionResult.Success -> {
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    }
                    is ActionResult.Error -> {
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun confirmRemoveFriend(friend: LeaderboardEntryUiModel) {
        if (friend.friendshipId.isEmpty()) return
        val dialog = android.app.Dialog(requireContext())
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_remove_friend)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.findViewById<TextView>(R.id.text_title).text =
            getString(R.string.remove_friend_title, friend.displayName)
        dialog.findViewById<TextView>(R.id.text_message).text =
            getString(R.string.remove_friend_message, friend.displayName)
        dialog.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            dialog.dismiss()
            viewModel.removeFriend(friend.friendshipId)
        }
        dialog.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAll(requireContext())
    }
}
