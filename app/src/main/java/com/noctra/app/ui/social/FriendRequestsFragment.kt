package com.noctra.app.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.noctra.app.R
import kotlinx.coroutines.launch

class FriendRequestsFragment : Fragment(R.layout.fragment_friend_requests) {

    private val viewModel: SocialViewModel by activityViewModels()
    private lateinit var receivedAdapter: FriendRequestAdapter
    private lateinit var sentAdapter: FriendRequestAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<ImageView>(R.id.btn_back)
        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)
        val recyclerReceived = view.findViewById<RecyclerView>(R.id.recycler_received)
        val recyclerSent = view.findViewById<RecyclerView>(R.id.recycler_sent)
        val emptyState = view.findViewById<LinearLayout>(R.id.empty_state)

        // Setup back button
        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // Setup adapters
        receivedAdapter = FriendRequestAdapter(
            isIncoming = true,
            onAcceptClick = { request ->
                viewModel.acceptRequest(request.friendshipId)
            },
            onDeclineClick = { request ->
                viewModel.declineRequest(request.friendshipId)
            }
        )

        sentAdapter = FriendRequestAdapter(
            isIncoming = false,
            onDeclineClick = { request ->
                viewModel.cancelRequest(request.friendshipId)
            }
        )

        // Setup RecyclerViews
        recyclerReceived.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = receivedAdapter
        }

        recyclerSent.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = sentAdapter
        }

        // Setup tabs
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        recyclerReceived.visibility = View.VISIBLE
                        recyclerSent.visibility = View.GONE
                    }
                    1 -> {
                        recyclerReceived.visibility = View.GONE
                        recyclerSent.visibility = View.VISIBLE
                    }
                }
                updateEmptyState()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Observe incoming requests
        lifecycleScope.launch {
            viewModel.incomingRequests.collect { requests ->
                receivedAdapter.submitList(requests)
                updateEmptyState()
            }
        }

        // Observe outgoing requests
        lifecycleScope.launch {
            viewModel.outgoingRequests.collect { requests ->
                sentAdapter.submitList(requests)
                updateEmptyState()
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

    private fun updateEmptyState() {
        val tabPosition = view?.findViewById<TabLayout>(R.id.tab_layout)?.selectedTabPosition ?: 0
        val hasItems = when (tabPosition) {
            0 -> viewModel.incomingRequests.value.isNotEmpty()
            1 -> viewModel.outgoingRequests.value.isNotEmpty()
            else -> false
        }

        view?.findViewById<LinearLayout>(R.id.empty_state)?.visibility =
            if (hasItems) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAll(requireContext())
    }
}
