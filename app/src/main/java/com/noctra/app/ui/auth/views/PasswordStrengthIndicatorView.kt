package com.noctra.app.ui.auth.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.noctra.app.R
import com.noctra.app.databinding.ViewPasswordStrengthIndicatorBinding
import com.noctra.app.utils.PasswordStrength

class PasswordStrengthIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewPasswordStrengthIndicatorBinding.inflate(
        LayoutInflater.from(context), this
    )

    init {
        orientation = VERTICAL
        setStrength(PasswordStrength.EMPTY) // Initial state
    }

    fun setStrength(strength: PasswordStrength) {
        val colorGray = ContextCompat.getColor(context, R.color.noctra_gray_track)
        val colorWeak = ContextCompat.getColor(context, android.R.color.holo_red_dark)
        val colorFair = ContextCompat.getColor(context, android.R.color.holo_orange_dark)
        val colorStrong = ContextCompat.getColor(context, android.R.color.holo_green_dark)

        when (strength) {
            PasswordStrength.EMPTY -> {
                binding.segment1.setBackgroundColor(colorGray)
                binding.segment2.setBackgroundColor(colorGray)
                binding.segment3.setBackgroundColor(colorGray)
                binding.tvStrengthLabel.text = ""
            }
            PasswordStrength.WEAK -> {
                binding.segment1.setBackgroundColor(colorWeak)
                binding.segment2.setBackgroundColor(colorGray)
                binding.segment3.setBackgroundColor(colorGray)
                binding.tvStrengthLabel.text = "Weak"
                binding.tvStrengthLabel.setTextColor(colorWeak)
            }
            PasswordStrength.FAIR -> {
                binding.segment1.setBackgroundColor(colorFair)
                binding.segment2.setBackgroundColor(colorFair)
                binding.segment3.setBackgroundColor(colorGray)
                binding.tvStrengthLabel.text = "Fair"
                binding.tvStrengthLabel.setTextColor(colorFair)
            }
            PasswordStrength.STRONG -> {
                binding.segment1.setBackgroundColor(colorStrong)
                binding.segment2.setBackgroundColor(colorStrong)
                binding.segment3.setBackgroundColor(colorStrong)
                binding.tvStrengthLabel.text = "Strong"
                binding.tvStrengthLabel.setTextColor(colorStrong)
            }
        }
    }
}
