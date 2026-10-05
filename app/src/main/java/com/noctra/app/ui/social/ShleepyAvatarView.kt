package com.noctra.app.ui.social

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.noctra.app.R

/**
 * Friend/request avatar: static Shleepy vector asset, stretched to
 * cover the whole circle (backgrounds fill their view bounds).
 */
class ShleepyAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        setBackgroundResource(R.drawable.avatar_shleepy)
        clipToOutline = true
    }
}
