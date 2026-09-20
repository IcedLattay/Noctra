package com.noctra.app.ui.social

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.airbnb.lottie.LottieAnimationView
import com.noctra.app.R

/**
 * Friend/request avatar: a frozen frame of the Shleepy idle animation
 * for the given stage + outfit. No layer opacity — each outfit is a
 * complete, self-contained Lottie JSON.
 */
class ShleepyAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val zoom = 1.75f
    private val yOffsetDp = 4f

    private val lottieView = LottieAnimationView(context).apply {
        repeatCount = 0
    }

    init {
        setBackgroundResource(R.drawable.bg_avatar_circle_purple)
        clipToOutline = true
        addView(lottieView)
        post {
            val size = (width * zoom).toInt().coerceAtLeast(width)
            lottieView.layoutParams = LayoutParams(size, size, android.view.Gravity.CENTER)
            lottieView.translationY = yOffsetDp * resources.displayMetrics.density
            // Show a default frame if no animation set yet
            setOutfit(3, "default")
        }
    }

    /**
     * Load the idle animation for [stageLevel] + [outfitAsset].
     * Frozen at frame 0 — no playback.
     */
    fun setOutfit(stageLevel: Int, outfitAsset: String) {
        val stageName = stageNameForLevel(stageLevel)
        val resId = resources.getIdentifier(
            "shleepy_${stageName}_${outfitAsset}", "raw", context.packageName
        )
        if (resId != 0) {
            lottieView.setAnimation(resId)
            lottieView.progress = 0f
        } else {
            // Fallback to default outfit
            val fallbackId = resources.getIdentifier(
                "shleepy_${stageName}_default", "raw", context.packageName
            )
            if (fallbackId != 0) {
                lottieView.setAnimation(fallbackId)
                lottieView.progress = 0f
            }
        }
    }

    private fun stageNameForLevel(level: Int): String = when (level) {
        1 -> "deprived"
        2 -> "awakening"
        3 -> "charged"
        4 -> "overdrive"
        5 -> "zen"
        else -> "charged"
    }
}
