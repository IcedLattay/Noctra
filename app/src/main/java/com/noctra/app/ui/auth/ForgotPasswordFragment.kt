package com.noctra.app.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.noctra.app.R
import com.noctra.app.databinding.FragmentForgotPasswordBinding
import kotlinx.coroutines.launch

class ForgotPasswordFragment : Fragment(R.layout.fragment_forgot_password) {

    private val viewModel: AuthViewModel by viewModels()
    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentForgotPasswordBinding.bind(view)

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.etEmail.doAfterTextChanged {
            val email = it.toString()
            val isValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
            binding.btnSendReset.isEnabled = isValid
            binding.btnSendReset.alpha = if (isValid) 1.0f else 0.5f
        }

        binding.btnSendReset.setOnClickListener {
            val email = binding.etEmail.text.toString()
            viewModel.sendPasswordReset(email)
        }

        binding.btnBackToLogin.setOnClickListener {
            findNavController().navigateUp()
        }
        
        // Initial state
        binding.btnSendReset.isEnabled = false
        binding.btnSendReset.alpha = 0.5f
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.authState.collect { state ->
                when (state) {
                    is AuthViewModel.AuthState.Loading -> {
                        binding.btnSendReset.text = "Sending..."
                        binding.btnSendReset.isEnabled = false
                    }
                    is AuthViewModel.AuthState.PasswordResetSent -> {
                        findNavController().navigate(R.id.action_forgotPassword_to_resetConfirmation)
                    }
                    is AuthViewModel.AuthState.Error -> {
                        binding.btnSendReset.text = "Send Reset Link"
                        binding.btnSendReset.isEnabled = true
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        viewModel.resetState()
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
