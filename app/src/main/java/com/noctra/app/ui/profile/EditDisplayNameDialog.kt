package com.noctra.app.ui.profile

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.noctra.app.R

/**
 * Dialog for editing the user's display name.
 * Pre-fills with the current name, validates input, and reports the new name
 * via a callback set by the caller.
 */
class EditDisplayNameDialog : DialogFragment() {

    private var currentName: String = ""
    private var onSave: ((String) -> Unit)? = null

    /**
     * Configure the dialog before showing it.
     * @param currentName the user's current display name to pre-fill
     * @param onSave callback invoked with the new, trimmed name when the user taps Save
     */
    fun configure(currentName: String, onSave: (String) -> Unit) {
        this.currentName = currentName
        this.onSave = onSave
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_edit_display_name, null)
        val inputLayout = view.findViewById<TextInputLayout>(R.id.input_layout_name)
        val input = view.findViewById<TextInputEditText>(R.id.input_display_name)

        input.setText(currentName)
        input.setSelection(currentName.length)  // cursor at end

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .setPositiveButton("Save", null)  // override below so we can validate before closing
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            // Initially disable Save (current name = pre-filled value, so no change yet)
            saveButton.isEnabled = false

            // Enable/disable Save as the user types, and show validation errors
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val text = s?.toString().orEmpty().trim()
                    when {
                        text.isEmpty() -> {
                            inputLayout.error = "Display name cannot be empty"
                            saveButton.isEnabled = false
                        }
                        text == currentName -> {
                            inputLayout.error = null
                            saveButton.isEnabled = false  // no change to save
                        }
                        else -> {
                            inputLayout.error = null
                            saveButton.isEnabled = true
                        }
                    }
                }
            })

            saveButton.setOnClickListener {
                val newName = input.text?.toString().orEmpty().trim()
                if (newName.isNotEmpty() && newName != currentName) {
                    onSave?.invoke(newName)
                    dialog.dismiss()
                }
            }
        }

        return dialog
    }
}