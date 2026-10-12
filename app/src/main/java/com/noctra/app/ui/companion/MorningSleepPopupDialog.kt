package com.noctra.app.ui.companion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import com.noctra.app.R
import com.noctra.app.databinding.DialogMorningSleepBinding

class MorningSleepPopupDialog : DialogFragment() {

    private var _binding: DialogMorningSleepBinding? = null
    private val binding get() = _binding!!

    private var onDismissListener: (() -> Unit)? = null
    private var onViewDetailsListener: (() -> Unit)? = null

    fun setOnDismissCallback(listener: () -> Unit) {
        this.onDismissListener = listener
    }

    fun setOnViewDetailsCallback(listener: () -> Unit) {
        this.onViewDetailsListener = listener
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.invoke()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMorningSleepBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val score = arguments?.getInt(ARG_SCORE) ?: 0
        val xpBonus = arguments?.getInt(ARG_XP_BONUS) ?: 0
        val xpTotal = arguments?.getInt(ARG_XP_TOTAL) ?: xpBonus
        val xpNights = arguments?.getInt(ARG_XP_NIGHTS) ?: 1

        binding.tvScoreValue.text = score.toString()
        binding.pbSleepGauge.progress = score

        // Portion row always shows; total row (double star) only when the
        // tally bundles more than last night — repeating it would read as a bug.
        binding.tvXpPortion.text = signedPts(xpBonus)
        if (xpNights > 1) {
            binding.tvXpTotal.text = signedPts(xpTotal)
            binding.tvXpTotal.visibility = View.VISIBLE
            binding.ivTotalStar1.visibility = View.VISIBLE
            binding.ivTotalStar2.visibility = View.VISIBLE
        }

        // Full sentence multi-night, short form single-night. Words, no symbols.
        val portionVerb = if (xpBonus >= 0) "earned" else "lost"
        val portionPts = kotlin.math.abs(xpBonus)
        binding.tvXpSentence.text = if (xpNights <= 1) {
            "You $portionVerb $portionPts sleep points from last night."
        } else {
            val totalPart = if (xpTotal >= 0) "a total of $xpTotal points"
            else "a total loss of ${kotlin.math.abs(xpTotal)} points"
            "You $portionVerb $portionPts sleep points from last night " +
                "and $totalPart across $xpNights mornings."
        }

        binding.tvSleepQualityLabel.text = when {
            score >= 75 -> getString(R.string.companion_good_sleep_quality)
            score >= 50 -> "Fair Sleep Quality"
            else -> "Poor Sleep Quality"
        }

        binding.btnViewDetails.setOnClickListener {
            onViewDetailsListener?.invoke()
            dismiss()
        }

        binding.btnNotNow.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun signedPts(v: Int) = (if (v >= 0) "+" else "-") + kotlin.math.abs(v) + " pts"

    companion object {
        private const val ARG_SCORE = "score"
        private const val ARG_XP_BONUS = "xp_bonus"
        private const val ARG_XP_TOTAL = "xp_total"
        private const val ARG_XP_NIGHTS = "xp_nights"

        fun newInstance(
            score: Int,
            xpBonus: Int,
            xpTotal: Int = xpBonus,
            xpNights: Int = 1
        ): MorningSleepPopupDialog {
            val args = Bundle().apply {
                putInt(ARG_SCORE, score)
                putInt(ARG_XP_BONUS, xpBonus)
                putInt(ARG_XP_TOTAL, xpTotal)
                putInt(ARG_XP_NIGHTS, xpNights)
            }
            return MorningSleepPopupDialog().apply {
                arguments = args
            }
        }
    }
}
