package com.noctra.app.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.noctra.app.R
import com.noctra.app.data.repository.UserProfileRepository
import com.noctra.app.databinding.FragmentEmailVerificationSuccessBinding
import com.noctra.app.utils.UserSession
import kotlinx.coroutines.launch

class EmailVerificationSuccessFragment : Fragment(R.layout.fragment_email_verification_success) {

    private var _binding: FragmentEmailVerificationSuccessBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentEmailVerificationSuccessBinding.bind(view)

        // 1. Update the database flag
        updateVerificationStatus()

        // 2. Setup button
        binding.btnContinue.setOnClickListener {
            // Go back to the main app (Companion screen)
            findNavController().navigate(R.id.action_global_routineHomeFragment)
        }
    }

    private fun updateVerificationStatus() {
        val userId = UserSession.getUserId(requireContext()) ?: return
        
        lifecycleScope.launch {
            try {
                UserProfileRepository().updateEmailVerificationStatus(userId, true)
            } catch (e: Exception) {
                // If it fails, we'll try again next time they open the app
                e.printStackTrace()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}