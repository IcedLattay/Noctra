package com.noctra.app.ui.companion

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.noctra.app.R
import com.noctra.app.databinding.DialogEvolutionBinding

class EvolutionDialogFragment : DialogFragment() {

    private var _binding: DialogEvolutionBinding? = null
    private val binding get() = _binding!!

    private val companionViewModel: CompanionViewModel by activityViewModels()

    private val shleepyStates = mapOf(
        1 to ShleepyState("DEPRIVED", R.raw.deprived_idle, R.raw.deprived_tapped),
        2 to ShleepyState("AWAKENING", R.raw.awakening_idle, R.raw.awakening_tapped),
        3 to ShleepyState("CHARGED", R.raw.charged_idle, R.raw.charged_tapped),
        4 to ShleepyState("OVERDRIVE", R.raw.overdrive_idle, R.raw.overdrive_tapped),
        5 to ShleepyState("ZEN", R.raw.zen_idle, R.raw.zen_tapped)
    )

    data class ShleepyState(val name: String, val idleRes: Int, val tappedRes: Int)

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

        // 1. Base Shleepy Animation
        val shleepyState = shleepyStates[evolution.stageLevel] ?: shleepyStates[1]!!
        binding.petAnimationView.apply {
            setAnimation(shleepyState.idleRes)
            repeatCount = -1
            playAnimation()
        }

        // 2. Equipped Items
        applyAccessoriesVisibility(state.equippedItems)
    }

    private fun applyAccessoriesVisibility(equippedItems: Map<String, com.noctra.app.data.model.ShopItem>) {
        val petView = binding.petAnimationView
        
        // Define all possible hat layers
        val allHatLayers = listOf("hat_sleeping_hat", "hat_propeller_hat", "hat_floral_crown")
        
        // Map category to Lottie layer name
        val otherCategoryToLayer = mapOf(
            "OUTFIT" to "outfit_layer",
            "ACCESSORY" to "accessory_layer"
        )

        // Identify active hat
        val activeHatLayer = equippedItems["HAT"]?.let { item ->
            when (item.itemAsset) {
                "hat_propeller_hat" -> "hat_propeller_hat"
                "hat_sleeping_hat" -> "hat_sleeping_hat"
                "hat_floral_crown" -> "hat_floral_crown"
                else -> null
            }
        }

        val applyOpacity = {
            // Handle Hats
            allHatLayers.forEach { layerName ->
                val opacity = if (layerName == activeHatLayer) 100 else 0
                try {
                    petView.addValueCallback(
                        KeyPath("**", layerName, "**"),
                        LottieProperty.TRANSFORM_OPACITY,
                        LottieValueCallback(opacity)
                    )
                } catch (e: Exception) {}
            }

            // Handle Other Categories
            otherCategoryToLayer.forEach { (category, layerName) ->
                val isEquipped = equippedItems.containsKey(category)
                val opacity = if (isEquipped) 100 else 0
                try {
                    petView.addValueCallback(
                        KeyPath("**", layerName, "**"),
                        LottieProperty.TRANSFORM_OPACITY,
                        LottieValueCallback(opacity)
                    )
                } catch (e: Exception) {}
            }
        }

        // Ensure visibility is updated whenever a new composition is loaded
        petView.addLottieOnCompositionLoadedListener { applyOpacity() }
        
        // Also trigger it immediately
        applyOpacity()
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
