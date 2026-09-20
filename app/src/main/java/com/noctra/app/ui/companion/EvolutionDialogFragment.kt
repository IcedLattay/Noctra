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

class EvolutionDialogFragment : DialogFragment() {

    private var _binding: DialogEvolutionBinding? = null
    private val binding get() = _binding!!

    private val companionViewModel: CompanionViewModel by activityViewModels()

    private var onDismissListener: (() -> Unit)? = null

    fun setOnDismissCallback(listener: () -> Unit) {
        this.onDismissListener = listener
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.invoke()
    }

    private val shleepyStates = mapOf(
        1 to ShleepyState("DEPRIVED"),
        2 to ShleepyState("AWAKENING"),
        3 to ShleepyState("CHARGED"),
        4 to ShleepyState("OVERDRIVE"),
        5 to ShleepyState("ZEN")
    )

    data class ShleepyState(val name: String)

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

        val stageName = shleepyStates[evolution.stageLevel]?.name?.lowercase() ?: "charged"
        val outfitAsset = state.equippedOutfit?.itemAsset ?: "default"

        val resId = resources.getIdentifier(
            "shleepy_${stageName}_${outfitAsset}", "raw", requireContext().packageName
        )

        if (resId != 0) {
            binding.petAnimationView.apply {
                setAnimation(resId)
                repeatCount = -1
                playAnimation()
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
