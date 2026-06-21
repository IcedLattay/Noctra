package com.noctra.app.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.noctra.app.R
import com.noctra.app.databinding.FragmentRegisterBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RegisterFragment : Fragment(R.layout.fragment_register) {

    private val viewModel: AuthViewModel by viewModels()
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentRegisterBinding.bind(view)

        setupInputs()
        setupListeners()
        observeViewModel()
    }

    private fun setupInputs() {
        binding.etDisplayName.doAfterTextChanged { viewModel.displayName.value = it.toString().trim() }
        binding.etEmail.doAfterTextChanged { viewModel.email.value = it.toString().trim() }
        binding.etPassword.doAfterTextChanged { viewModel.password.value = it.toString().trim() }
        binding.etConfirmPassword.doAfterTextChanged { viewModel.confirmPassword.value = it.toString().trim() }

        // SDD: Show/Hide password strength indicator on focus with transition
        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // Fade in and show
                binding.passwordStrengthIndicator.visibility = View.VISIBLE
                binding.passwordStrengthIndicator.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .withEndAction(null)
                    .start()
            } else {
                // Fade out and then hide
                binding.passwordStrengthIndicator.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        binding.passwordStrengthIndicator.visibility = View.GONE
                    }
                    .start()
            }
        }

        // SDD: Validate email format on focus loss
        binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val email = binding.etEmail.text.toString().trim()
                if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    binding.tvErrorEmail.visibility = View.VISIBLE
                    binding.tvErrorEmail.text = "Invalid email format"
                } else {
                    updateInlineErrors()
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnCreateAccount.setOnClickListener {
            viewModel.signUp()
        }

        binding.btnGoToLogin.setOnClickListener {
            findNavController().navigateUp()
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
                    viewModel.formIsValid.collectLatest { isValid ->
                        binding.btnCreateAccount.isEnabled = isValid
                        binding.btnCreateAccount.alpha = if (isValid) 1.0f else 0.5f
                        
                        // Inline validation visibility
                        updateInlineErrors()
                    }
                }

                launch {
                    viewModel.passwordStrength.collectLatest { strength ->
                        binding.passwordStrengthIndicator.setStrength(strength)
                    }
                }
            }
        }
    }

    private fun updateInlineErrors() {
        val name = viewModel.displayName.value
        val email = viewModel.email.value
        val pass = viewModel.password.value
        val confirm = viewModel.confirmPassword.value

        binding.tvErrorDisplayName.visibility = if (name.isNotEmpty() && name.length < 2) View.VISIBLE else View.GONE
        binding.tvErrorEmail.visibility = if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) View.VISIBLE else View.GONE
        binding.tvErrorPassword.visibility = if (pass.isNotEmpty() && pass.length < 8) View.VISIBLE else View.GONE
        binding.tvErrorConfirmPassword.visibility = if (confirm.isNotEmpty() && pass != confirm) View.VISIBLE else View.GONE
    }

    private fun handleAuthState(state: AuthViewModel.AuthState) {
        when (state) {
            is AuthViewModel.AuthState.Loading -> {
                binding.btnCreateAccount.text = "Creating account..."
            }
            is AuthViewModel.AuthState.Success -> {
                Toast.makeText(requireContext(), "Account created! Proceeding to onboarding.", Toast.LENGTH_LONG).show()
                // Directly to onboarding as per SDD
                findNavController().navigate(R.id.action_register_to_onboarding)
            }
            is AuthViewModel.AuthState.Error -> {
                binding.btnCreateAccount.text = "Create Account"
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {
                binding.btnCreateAccount.text = "Create Account"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}