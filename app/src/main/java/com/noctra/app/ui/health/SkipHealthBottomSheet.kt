package com.noctra.app.ui.health

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.noctra.app.R

// Skip-confirmation sheet for the Grant screen (step 5): "Go back" just
// dismisses, "Continue" proceeds without Health Connect via the callback.
class SkipHealthBottomSheet : BottomSheetDialogFragment() {

    var onContinue: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_skip_health, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btn_go_back).setOnClickListener {
            dismiss()
        }
        view.findViewById<View>(R.id.btn_continue).setOnClickListener {
            dismiss()
            onContinue?.invoke()
        }
    }
}
