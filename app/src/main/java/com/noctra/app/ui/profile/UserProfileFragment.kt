package com.noctra.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.noctra.app.R
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController

class UserProfileFragment : Fragment(R.layout.fragment_user_profile) {

    private val viewModel: UserProfileViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val displayName = view.findViewById<TextView>(R.id.text_display_name)
        val email = view.findViewById<TextView>(R.id.text_email)
        val currentStreak = view.findViewById<TextView>(R.id.stat_current_streak)
        val longestStreak = view.findViewById<TextView>(R.id.stat_longest_streak)
        val routinesCompleted = view.findViewById<TextView>(R.id.stat_routines_completed)
        val stageLabel = view.findViewById<TextView>(R.id.status_stage_label)
        val stageNumber = view.findViewById<TextView>(R.id.status_stage_number)
        val xpMessage = view.findViewById<TextView>(R.id.status_xp_message)

        val editIcon = view.findViewById<ImageView>(R.id.icon_edit_name)
        val settingsIcon = view.findViewById<ImageView>(R.id.icon_settings)
        val addFriendIcon = view.findViewById<ImageView>(R.id.icon_add_friend)

        val avatarShleepy = view.findViewById<ImageView>(R.id.avatar_shleepy)
        val statusAvatar = view.findViewById<ImageView>(R.id.status_avatar)

        // Observe profile data
        lifecycleScope.launch {
            viewModel.profileData.collect { state ->
                displayName.text = state.displayName
                email.text = state.email ?: "(demo mode)"
                currentStreak.text = state.currentStreak.toString()
                longestStreak.text = state.longestStreak.toString()
                routinesCompleted.text = state.routinesCompleted.toString()
                stageLabel.text = state.stageName
                stageNumber.text = "Stage ${state.stageNumber}"
                xpMessage.text = state.xpToNextStageMessage

                // Update avatars
                avatarShleepy.setImageResource(state.mainAvatarRes) // Detailed artwork
                statusAvatar.setImageResource(state.stageAvatarRes) // Expression face
            }
        }

        // Click handlers - placeholders for now
        editIcon.setOnClickListener {
            val currentName = viewModel.profileData.value.displayName
            EditDisplayNameDialog().apply {
                configure(currentName) { newName ->
                    viewModel.updateDisplayName(requireContext(), newName)
                }
            }.show(parentFragmentManager, "edit_display_name")
        }

        settingsIcon.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_settings)
        }

        addFriendIcon.setOnClickListener {
            // Out of MVP scope
        }

        viewModel.loadProfile(requireContext())
    }
}