package com.noctra.app.ui.auth

import android.os.Bundle
import android.text.InputType
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
import com.noctra.app.databinding.FragmentResetPasswordBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ResetPasswordFragment : Fragment(R.layout.fragment_reset_password) {

    private val viewModel: AuthViewModel by viewModels()
    private var _binding: FragmentResetPasswordBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentResetPasswordBinding.bind(view)

        setupInputs()
        setupListeners()
        observeViewModel()
    }

    private fun setupInputs() {
        binding.etNewPassword.doAfterTextChanged { 
            viewModel.password.value = it.toString() 
        }
        binding.etConfirmPassword.doAfterTextChanged { 
            viewModel.confirmPassword.value = it.toString() 
        }
    }

    private fun setupListeners() {
        binding.btnResetPassword.setOnClickListener {
            viewModel.updatePassword(binding.etNewPassword.text.toString())
        }

        binding.btnToggleNewPassword.setOnClickListener {
            togglePasswordVisibility(binding.etNewPassword)
        }

        binding.btnToggleConfirmPassword.setOnClickListener {
            togglePasswordVisibility(binding.etConfirmPassword)
        }
    }

    private fun togglePasswordVisibility(editText: android.widget.EditText) {
        val selection = editText.selectionEnd
        if (editText.inputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        editText.setSelection(selection)
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
                    viewModel.password.collectLatest { pass ->
                        updateValidationUI()
                    }
                }

                launch {
                    viewModel.confirmPassword.collectLatest { confirm ->
                        updateValidationUI()
                    }
                }
            }
        }
    }

    private fun updateValidationUI() {
        val pass = viewModel.password.value
        val confirm = viewModel.confirmPassword.value
        
        val passValid = pass.length >= 8
        val confirmValid = pass == confirm && confirm.isNotEmpty()
        
        binding.btnResetPassword.isEnabled = passValid && confirmValid
        binding.btnResetPassword.alpha = if (binding.btnResetPassword.isEnabled) 1.0f else 0.5f
    }

    private fun handleAuthState(state: AuthViewModel.AuthState) {
        when (state) {
            is AuthViewModel.AuthState.Loading -> {
                binding.btnResetPassword.isEnabled = false
                binding.btnResetPassword.text = "Updating..."
            }
            is AuthViewModel.AuthState.PasswordUpdated -> {
                Toast.makeText(requireContext(), "Password updated successfully!", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.loginFragment, null,
                    androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.nav_graph, true)
                        .build())
            }
            is AuthViewModel.AuthState.Error -> {
                binding.btnResetPassword.isEnabled = true
                binding.btnResetPassword.text = "Reset Password"
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {
                binding.btnResetPassword.text = "Reset Password"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
