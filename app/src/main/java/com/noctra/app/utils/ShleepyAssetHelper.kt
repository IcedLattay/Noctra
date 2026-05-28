package com.noctra.app.utils

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.noctra.app.data.model.ShopItem

/**
 * Centralized logic for rendering Shleepy and his accessories.
 * Implements "Possibility A": assets are full-frame and pre-aligned.
 */
object ShleepyAssetHelper {

    /**
     * Applies an accessory (like a hat) to an ImageView.
     * Since assets are full-frame, we use 0 offset.
     */
    fun applyAccessory(imageView: ImageView, item: ShopItem, stageSuffix: String) {
        val context = imageView.context
        val cleanAsset = item.itemAsset.removeSuffix(".png").removeSuffix(".jpg").removeSuffix(".webp")
        val assetName = "${cleanAsset}_$stageSuffix"
        
        // 1. Try to find stage-specific variant, fallback to base asset
        var resId = context.resources.getIdentifier(assetName, "drawable", context.packageName)
        if (resId == 0) {
            resId = context.resources.getIdentifier(cleanAsset, "drawable", context.packageName)
        }

        if (resId != 0) {
            imageView.setImageResource(resId)
            imageView.visibility = View.VISIBLE
            
            // 2. Clear any old offsets (Possibility A uses natural 0,0 alignment)
            imageView.translationX = 0f
            imageView.translationY = 0f
        } else {
            imageView.visibility = View.GONE
        }
    }
}
