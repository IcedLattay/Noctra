package com.noctra.app.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.noctra.app.R
import com.noctra.app.databinding.FragmentPasswordResetConfirmationBinding

class PasswordResetConfirmationFragment : Fragment(R.layout.fragment_password_reset_confirmation) {

    private var _binding: FragmentPasswordResetConfirmationBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPasswordResetConfirmationBinding.bind(view)

        binding.btnBackToLogin.setOnClickListener {
            // Pop back to Login screen
            findNavController().popBackStack(R.id.loginFragment, false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
