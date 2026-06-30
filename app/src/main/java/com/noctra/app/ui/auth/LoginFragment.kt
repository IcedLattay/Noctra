package com.noctra.app.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.noctra.app.R
import com.noctra.app.databinding.FragmentLoginBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LoginFragment : Fragment(R.layout.fragment_login) {

    private val viewModel: AuthViewModel by viewModels()
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLoginBinding.bind(view)

        setupInputs()
        setupListeners()
        observeViewModel()
    }

    private fun setupInputs() {
        binding.etEmail.doAfterTextChanged { viewModel.email.value = it.toString() }
        binding.etPassword.doAfterTextChanged { viewModel.password.value = it.toString() }

        // SDD: Validate email format on focus loss
        binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val email = binding.etEmail.text.toString()
                if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(requireContext(), "Invalid email format", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnSignIn.setOnClickListener {
            viewModel.signIn(viewModel.email.value, viewModel.password.value)
        }

        binding.btnGoToRegister.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        binding.btnForgotPassword.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_forgotPassword)
        }

        // Password visibility toggle
        binding.btnTogglePassword.setOnClickListener {
            val inputType = binding.etPassword.inputType
            if (inputType == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                binding.etPassword.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                binding.etPassword.inputType = android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            }
            binding.etPassword.setSelection(binding.etPassword.text.length)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.authState.collect { state ->
                        handleAuthState(state)
                    }
                }

                launch {
                    // Combine validation logic for Login (email + password not empty)
                    // You can move this logic to ViewModel later if you want it more centralized
                    viewModel.email.collectLatest { email ->
                        val isValid = email.isNotEmpty() && viewModel.password.value.isNotEmpty()
                        updateButtonState(isValid)
                    }
                }
                launch {
                    viewModel.password.collectLatest { pass ->
                        val isValid = pass.isNotEmpty() && viewModel.email.value.isNotEmpty()
                        updateButtonState(isValid)
                    }
                }
            }
        }
    }

    private fun updateButtonState(isValid: Boolean) {
        binding.btnSignIn.isEnabled = isValid
        binding.btnSignIn.alpha = if (isValid) 1.0f else 0.5f
    }

    private fun handleAuthState(state: AuthViewModel.AuthState) {
        when (state) {
            is AuthViewModel.AuthState.Loading -> {
                binding.btnSignIn.isEnabled = false
                binding.btnSignIn.text = "Signing in..."
            }
            is AuthViewModel.AuthState.Success -> {
                if (state.onboardingCompleted) {
                    findNavController().navigate(R.id.action_login_to_home)
                } else {
                    findNavController().navigate(R.id.action_login_to_onboarding)
                }
            }
            is AuthViewModel.AuthState.UnverifiedEmail -> {
                Toast.makeText(requireContext(), "Email not verified. Sending fresh code...", Toast.LENGTH_LONG).show()
                val bundle = Bundle().apply {
                    putString("email", state.email)
                    putBoolean("auto_resend", true)
                }
                findNavController().navigate(R.id.action_login_to_verifyOtp, bundle)
                viewModel.resetState() // Clear state so we don't loop if we come back
            }
            is AuthViewModel.AuthState.Error -> {
                binding.btnSignIn.isEnabled = true
                binding.btnSignIn.text = "Sign In"
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {
                // Initial check for button state based on current input
                val isValid = viewModel.email.value.isNotEmpty() && viewModel.password.value.isNotEmpty()
                updateButtonState(isValid)
                binding.btnSignIn.text = "Sign In"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}