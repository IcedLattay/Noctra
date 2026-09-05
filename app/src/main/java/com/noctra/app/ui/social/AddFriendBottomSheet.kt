package com.noctra.app.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.noctra.app.R
import kotlinx.coroutines.launch

class AddFriendBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: SocialViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_add_friend, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val inputLayoutEmail = view.findViewById<TextInputLayout>(R.id.input_layout_email)
        val inputEmail = view.findViewById<TextInputEditText>(R.id.input_email)
        val textError = view.findViewById<TextView>(R.id.text_error)
        val btnSendRequest = view.findViewById<MaterialButton>(R.id.btn_send_request)
        val successState = view.findViewById<LinearLayout>(R.id.success_state)

        btnSendRequest.setOnClickListener {
            val email = inputEmail.text.toString().trim()

            if (email.isEmpty()) {
                textError.text = "Please enter an email address"
                textError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                textError.text = "Please enter a valid email address"
                textError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            textError.visibility = View.GONE
            btnSendRequest.isEnabled = false

            viewModel.sendFriendRequest(requireContext(), email)

            // Show success state
            successState.visibility = View.VISIBLE
            inputLayoutEmail.visibility = View.GONE
            btnSendRequest.visibility = View.GONE
            textError.visibility = View.GONE

            // Dismiss after delay
            view.postDelayed({
                dismiss()
            }, 2000)
        }

        // Observe action results for errors
        lifecycleScope.launch {
            viewModel.actionResult.collect { result ->
                when (result) {
                    is ActionResult.Error -> {
                        textError.text = result.message
                        textError.visibility = View.VISIBLE
                        btnSendRequest.isEnabled = true
                        viewModel.clearActionResult()
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Make the bottom sheet full width
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            it.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }
}
