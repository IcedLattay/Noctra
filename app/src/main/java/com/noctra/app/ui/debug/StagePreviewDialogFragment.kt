package com.noctra.app.ui.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.noctra.app.databinding.DialogStagePreviewBinding

/**
 * Debug-only stage animation viewer: plays the default-outfit Lottie
 * for each of the 5 stages. Zero writes, purely visual.
 */
class StagePreviewDialogFragment : DialogFragment() {

    private var _binding: DialogStagePreviewBinding? = null
    private val binding get() = _binding!!

    private val stages = listOf(
        1 to "Deprived",
        2 to "Awakening",
        3 to "Charged",
        4 to "Overdrive",
        5 to "Zen Master"
    )

    private val stageAssets = mapOf(
        1 to "deprived",
        2 to "awakening",
        3 to "charged",
        4 to "overdrive",
        5 to "zen"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogStagePreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val buttons = listOf(
            binding.btnStage1,
            binding.btnStage2,
            binding.btnStage3,
            binding.btnStage4,
            binding.btnStage5
        )
        buttons.forEachIndexed { index, button ->
            button.setOnClickListener { playStage(index + 1) }
        }
        playStage(3)
    }

    private fun playStage(level: Int) {
        val asset = stageAssets[level] ?: "charged"
        binding.textStageName.text = stages.firstOrNull { it.first == level }?.second ?: ""
        val resId = resources.getIdentifier(
            "shleepy_${asset}_default", "raw", requireContext().packageName
        )
        if (resId != 0) {
            binding.previewAnimation.apply {
                setAnimation(resId)
                repeatCount = -1
                playAnimation()
            }
        } else {
            binding.previewAnimation.cancelAnimation()
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
}
