package com.noctra.app.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin
import kotlin.random.Random

/**
 * WaveformView
 *
 * A decorative animated audio waveform — a row of rounded vertical bars that
 * rise and fall continuously while audio plays.
 *
 * IMPORTANT: this is NOT audio-reactive. It does not read the actual audio
 * stream. True reactivity would require android.media.audiofx.Visualizer,
 * which needs the RECORD_AUDIO runtime permission — a heavy privacy ask for
 * a sleep app. This view instead animates on a smooth pseudo-random sine
 * pattern that reads as "audio is playing" without any permission.
 *
 * Usage: call start() when playback begins, stop() when it ends or the view
 * is destroyed. Safe to call either repeatedly.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barCount = 28
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7B6FE0")
        style = Paint.Style.FILL
    }

    // Per-bar phase offsets so bars don't move in lockstep
    private val phaseOffsets = FloatArray(barCount) { Random.nextFloat() * 6.28f }
    private val speeds = FloatArray(barCount) { 0.7f + Random.nextFloat() * 0.9f }

    private var animationTime = 0f
    private var animator: ValueAnimator? = null
    private val barRect = RectF()

    /** Starts the animation loop. Safe to call if already running. */
    fun start() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                animationTime += 0.08f
                invalidate()
            }
            start()
        }
    }

    /** Stops the animation and settles the bars flat. Safe to call if already stopped. */
    fun stop() {
        animator?.cancel()
        animator = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val totalGapRatio = 0.4f
        val barSlot = width.toFloat() / barCount
        val barWidth = barSlot * (1f - totalGapRatio)
        val gap = barSlot * totalGapRatio
        val centerY = height / 2f
        val maxBarHeight = height * 0.9f
        val minBarHeight = height * 0.12f
        val isAnimating = animator?.isRunning == true

        for (i in 0 until barCount) {
            val barHeight = if (isAnimating) {
                // Two summed sines at different rates give an organic, non-repeating feel
                val wave = sin(animationTime * speeds[i] + phaseOffsets[i])
                val wave2 = sin(animationTime * speeds[i] * 0.43f + phaseOffsets[i] * 1.7f)
                val normalized = ((wave + wave2) / 2f + 1f) / 2f // 0..1
                minBarHeight + normalized * (maxBarHeight - minBarHeight)
            } else {
                minBarHeight
            }

            val left = i * barSlot + gap / 2f
            barRect.set(
                left,
                centerY - barHeight / 2f,
                left + barWidth,
                centerY + barHeight / 2f
            )
            val radius = barWidth / 2f
            canvas.drawRoundRect(barRect, radius, radius, barPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }
}