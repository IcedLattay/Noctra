package com.noctra.app.ui.companion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.noctra.app.R
import com.noctra.app.databinding.DialogEvolutionBinding
import com.noctra.app.utils.ShleepyAssetHelper

class EvolutionDialogFragment : DialogFragment() {

    private var _binding: DialogEvolutionBinding? = null
    private val binding get() = _binding!!

    private val companionViewModel: CompanionViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEvolutionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val stageName = arguments?.getString(ARG_STAGE_NAME) ?: "THE AWAKENING"
        binding.tvNewStageName.text = stageName

        renderShleepy()

        // Simple scale and fade animations
        val anim = AlphaAnimation(0.2f, 1.0f).apply {
            duration = 1000
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        binding.ivGlow.startAnimation(anim)

        binding.btnContinue.setOnClickListener {
            dismiss()
        }
    }

    private fun renderShleepy() {
        val state = companionViewModel.uiState.value
        val evolution = state.evolutionState ?: return

        // 1. Base Shleepy
        val shleepyResId = when (evolution.stageLevel) {
            1 -> R.drawable.shleepy_depleted
            2 -> R.drawable.shleepy_awakening
            3 -> R.drawable.shleepy_charged
            4 -> R.drawable.shleepy_overdrive
            5 -> R.drawable.shleepy_zenmaster
            else -> R.drawable.shleepy_depleted
        }
        binding.ivEvolutionShleepy.setImageResource(shleepyResId)

        // 2. Equipped Items
        val stageSuffix = when (evolution.stageLevel) {
            1 -> "depleted"
            2 -> "awakening"
            3 -> "charged"
            4 -> "overdrive"
            5 -> "zenmaster"
            else -> "depleted"
        }

        val categories = mapOf(
            "HAT" to binding.ivEquippedHat,
            "OUTFIT" to binding.ivEquippedOutfit,
            "ACCESSORY" to binding.ivEquippedAccessory
        )

        categories.forEach { (category, imageView) ->
            val item = state.equippedItems[category]
            if (item != null) {
                ShleepyAssetHelper.applyAccessory(imageView, item, stageSuffix)
            } else {
                imageView.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_STAGE_NAME = "stage_name"

        fun newInstance(stageName: String): EvolutionDialogFragment {
            return EvolutionDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_STAGE_NAME, stageName)
                }
            }
        }
    }
}
