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

        binding.tvScoreValue.text = score.toString()
        binding.pbSleepGauge.progress = score
        binding.tvXpBonus.text = getString(R.string.morning_dialog_sleep_points_bonus, xpBonus)

        binding.tvSleepQualityLabel.text = when {
            score >= 75 -> getString(R.string.companion_good_sleep_quality)
            score >= 50 -> "Fair Sleep Quality"
            else -> "Poor Sleep Quality"
        }

        binding.btnViewDetails.setOnClickListener {
            // Coordinate with Person C: navigate to analytics
            // findNavController().navigate(R.id.action_companion_to_analytics)
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

    companion object {
        private const val ARG_SCORE = "score"
        private const val ARG_XP_BONUS = "xp_bonus"

        fun newInstance(score: Int, xpBonus: Int): MorningSleepPopupDialog {
            val args = Bundle().apply {
                putInt(ARG_SCORE, score)
                putInt(ARG_XP_BONUS, xpBonus)
            }
            return MorningSleepPopupDialog().apply {
                arguments = args
            }
        }
    }
}
