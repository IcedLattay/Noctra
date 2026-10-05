package com.noctra.app.ui.companion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.noctra.app.databinding.DialogEvolutionBinding

class EvolutionDialogFragment : DialogFragment() {

    private var _binding: DialogEvolutionBinding? = null
    private val binding get() = _binding!!

    private val companionViewModel: CompanionViewModel by activityViewModels()

    private var onDismissListener: (() -> Unit)? = null

    private val stageStates = mapOf(
        1 to "feeling drained",
        2 to "waking up",
        3 to "full of energy",
        4 to "in overdrive",
        5 to "completely zen"
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
        _binding = DialogEvolutionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val oldLevel = arguments?.getInt(ARG_OLD_LEVEL, 1) ?: 1
        val newLevel = arguments?.getInt(ARG_NEW_LEVEL, 2) ?: 2

        binding.tvEvolutionTitle.text = "Shleepy just moved up an energy level!"

        val oldState = stageStates[oldLevel] ?: "feeling drained"
        val newState = stageStates[newLevel] ?: "waking up"
        binding.tvEvolutionDescription.text = if (newLevel >= 5) {
            "Shleepy went from $oldState to $newState! That's the highest energy level — amazing work keeping it up!"
        } else {
            val nextState = stageStates[newLevel + 1] ?: "completely zen"
            "Shleepy went from $oldState to $newState! Keep up the good work and Shleepy will be $nextState in no time!"
        }

        renderShleepy(newLevel)

        binding.btnAwesome.setOnClickListener {
            dismiss()
        }
    }

    private fun renderShleepy(level: Int) {
        val state = companionViewModel.uiState.value
        // Animation needs the loaded outfit; texts above show regardless.
        val outfitAsset = state.equippedOutfit?.itemAsset ?: "default"

        val resId = resources.getIdentifier(
            "shleepy_${stageAssets[level] ?: "charged"}_${outfitAsset}",
            "raw", requireContext().packageName
        )

        if (resId != 0) {
            binding.lottieShleepy.apply {
                setAnimation(resId)
                repeatCount = -1
                playAnimation()
            }
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

    fun setOnDismissCallback(listener: () -> Unit) {
        this.onDismissListener = listener
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.invoke()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_OLD_LEVEL = "old_level"
        private const val ARG_NEW_LEVEL = "new_level"

        fun newInstance(oldLevel: Int, newLevel: Int): EvolutionDialogFragment {
            return EvolutionDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_OLD_LEVEL, oldLevel)
                    putInt(ARG_NEW_LEVEL, newLevel)
                }
            }
        }
    }
}
