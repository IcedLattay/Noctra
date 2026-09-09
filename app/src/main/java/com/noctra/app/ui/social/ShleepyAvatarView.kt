package com.noctra.app.ui.social

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.noctra.app.R
import com.noctra.app.data.model.ShopItem

// Friend/request avatar: the Charged-stage Shleepy, frozen on frame 0, with
// the given user's equipped wearables applied via layer opacity — the same
// mechanism CompanionFragment uses, minus the animation.
class ShleepyAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // How much bigger than the frame the animation renders (edges crop)
    private val zoom = 1.75f
    // Nudge the animation down inside the frame (dp)
    private val yOffsetDp = 4f

    private val lottieView = LottieAnimationView(context).apply {
        setAnimation(R.raw.charged_idle)
        repeatCount = 0
    }

    init {
        // Purple circle frame — clips the animation to a circle
        setBackgroundResource(R.drawable.bg_avatar_circle_purple)
        clipToOutline = true
        addView(lottieView)
        // Oversize + center so Shleepy fills the frame; the parent clips overflow
        post {
            val size = (width * zoom).toInt().coerceAtLeast(width)
            lottieView.layoutParams = LayoutParams(size, size, android.view.Gravity.CENTER)
            lottieView.translationY = yOffsetDp * resources.displayMetrics.density
        }
    }

    fun setEquipped(equippedItems: Map<String, ShopItem>) {
        lottieView.addLottieOnCompositionLoadedListener {
            lottieView.progress = 0f
            applyAccessoriesVisibility(equippedItems)
        }
    }

    // Mirrors CompanionFragment.refreshAccessoriesVisibility
    private fun applyAccessoriesVisibility(equippedItems: Map<String, ShopItem>) {
        val allHatLayers = listOf("hat_sleeping_hat", "hat_propeller_hat", "hat_floral_crown")
        val otherCategoryToLayer = mapOf(
            "OUTFIT" to "outfit_layer",
            "ACCESSORY" to "accessory_layer"
        )

        val activeHatLayer = equippedItems["HAT"]?.let { item ->
            when (item.itemAsset) {
                "hat_propeller_hat" -> "hat_propeller_hat"
                "hat_sleeping_hat" -> "hat_sleeping_hat"
                "hat_floral_crown" -> "hat_floral_crown"
                else -> null
            }
        }

        allHatLayers.forEach { layerName ->
            setLayerOpacity(layerName, if (layerName == activeHatLayer) 100 else 0)
        }

        otherCategoryToLayer.forEach { (category, layerName) ->
            setLayerOpacity(layerName, if (equippedItems.containsKey(category)) 100 else 0)
        }
    }

    private fun setLayerOpacity(layerName: String, opacity: Int) {
        try {
            lottieView.addValueCallback(
                KeyPath("**", layerName, "**"),
                LottieProperty.TRANSFORM_OPACITY,
                LottieValueCallback(opacity)
            )
        } catch (e: Exception) {
            // Layer absent in this composition — ignore
        }
    }
}
