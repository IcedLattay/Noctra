package com.noctra.app.ui.auth

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.noctra.app.R
import com.noctra.app.databinding.FragmentVerifyOtpBinding
import kotlinx.coroutines.launch

class VerifyOtpFragment : Fragment(R.layout.fragment_verify_otp) {

    private val viewModel: AuthViewModel by viewModels()
    private var _binding: FragmentVerifyOtpBinding? = null
    private val binding get() = _binding!!

    // Retrieve email and resend flag from arguments
    private val emailArg: String by lazy { arguments?.getString("email") ?: "" }
    private val autoResendArg: Boolean by lazy { arguments?.getBoolean("auto_resend", false) ?: false }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentVerifyOtpBinding.bind(view)

        setupOtpInputs()
        setupListeners()
        observeViewModel()

        // If we came from Login (unverified), we need to request a fresh code.
        // If we came from Register, Supabase already sent one, so we just start the timer.
        if (autoResendArg) {
            viewModel.resendVerificationCode(emailArg)
        }

        startResendTimer()
    }

    private fun startResendTimer() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.btnResend.isEnabled = false
            binding.btnResend.alpha = 0.5f
            for (i in 60 downTo 1) {
                binding.btnResend.text = "Resend code in ${i}s"
                kotlinx.coroutines.delay(1000)
            }
            binding.btnResend.text = "Didn't receive a code? Resend"
            binding.btnResend.isEnabled = true
            binding.btnResend.alpha = 1.0f
        }
    }

    private fun setupOtpInputs() {
        val boxes = arrayOf(
            binding.otpDigit1, binding.otpDigit2, binding.otpDigit3,
            binding.otpDigit4, binding.otpDigit5, binding.otpDigit6
        )

        for (i in boxes.indices) {
            boxes[i].addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1) {
                        if (i < boxes.size - 1) {
                            boxes[i + 1].requestFocus()
                        }
                    } else if (s?.length == 0) {
                        if (i > 0) {
                            boxes[i - 1].requestFocus()
                        }
                    }
                    updateVerifyButtonState()
                }
            })

            // Handle backspace on empty field
            boxes[i].setOnKeyListener { v, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_DEL && 
                    event.action == android.view.KeyEvent.ACTION_DOWN &&
                    boxes[i].text.isEmpty() && i > 0) {
                    boxes[i - 1].requestFocus()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            if (!findNavController().popBackStack()) {
                findNavController().navigate(R.id.loginFragment)
            }
        }

        binding.btnVerify.setOnClickListener {
            val code = getOtpCode()
            viewModel.verifyOtp(emailArg, code)
        }

        binding.btnResend.setOnClickListener {
            viewModel.resendVerificationCode(emailArg)
            startResendTimer()
        }
        
        binding.btnVerify.isEnabled = false
        binding.btnVerify.alpha = 0.5f
    }


    private fun getOtpCode(): String {
        return binding.otpDigit1.text.toString() +
               binding.otpDigit2.text.toString() +
               binding.otpDigit3.text.toString() +
               binding.otpDigit4.text.toString() +
               binding.otpDigit5.text.toString() +
               binding.otpDigit6.text.toString()
    }

    private fun updateVerifyButtonState() {
        val isComplete = getOtpCode().length == 6
        binding.btnVerify.isEnabled = isComplete
        binding.btnVerify.alpha = if (isComplete) 1.0f else 0.5f
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.authState.collect { state ->
                when (state) {
                    is AuthViewModel.AuthState.Loading -> {
                        binding.btnVerify.text = "Verifying..."
                        binding.btnVerify.isEnabled = false
                    }
                    is AuthViewModel.AuthState.OtpVerified -> {
                        // After verifying, we always take the user to onboarding
                        // as unverified accounts haven't completed their setup yet.
                        findNavController().navigate(R.id.action_verifyOtp_to_onboarding)
                    }
                    is AuthViewModel.AuthState.VerificationCodeSent -> {
                        Toast.makeText(requireContext(), "A fresh passcode has been sent!", Toast.LENGTH_SHORT).show()
                        viewModel.resetState()
                    }
                    is AuthViewModel.AuthState.Error -> {
                        binding.btnVerify.text = "Verify Code"
                        binding.btnVerify.isEnabled = true
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