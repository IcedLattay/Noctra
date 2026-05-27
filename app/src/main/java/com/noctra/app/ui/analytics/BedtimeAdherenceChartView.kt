package com.noctra.app.ui.analytics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.noctra.app.R
import com.noctra.app.domain.usecase.BedtimeAdherenceCalculator
import com.noctra.app.domain.usecase.BedtimeAdherenceCalculator.Adherence

/**
 * Custom chart showing target bedtime vs actual sleep onset for 7 days.
 *
 * Layout (per day column):
 *   ● Target (faded purple, fixed Y)
 *   │
 *   │  ← vertical connector encoding delay
 *   │
 *   ● Actual (color-coded, Y depends on delay)
 *
 *   Mon  (day label below)
 */
class BedtimeAdherenceChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<BedtimeAdherenceCalculator.NightAdherence> = emptyList()
    private val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dpToPx(1.5f)
        strokeCap = Paint.Cap.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dpToPx(13f)
        color = ContextCompat.getColor(context, R.color.noctra_purple_dark)
    }

    private val dotRadiusPx = dpToPx(7f)
    private val labelHeightPx = dpToPx(28f)
    private val verticalPadding = dpToPx(8f)

    // Fractional positions within the dot-drawing area (between top padding and label area).
    // These are clamped so dots never escape the chart bounds regardless of height.
    private val targetYFraction = 0.10f       // target dots at 10% down from top of drawable area
    private val onTimeYFraction = 0.30f       // on-time actual dots at 30%
    private val slightDelayYFraction = 0.55f  // slight delay at 55%
    private val lateYFraction = 0.85f         // late at 85% — still has room above labels

    fun setData(weekAdherence: List<BedtimeAdherenceCalculator.NightAdherence>) {
        require(weekAdherence.size == 7) { "Expected 7 days, got ${weekAdherence.size}" }
        data = weekAdherence
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val columnWidth = w / 7f

        val isEmpty = data.all { it.adherence == Adherence.NO_DATA }

        if (isEmpty) {
            drawEmptyState(canvas)
            return
        }

        // Drawable area = full height minus space reserved for day labels
        val drawableAreaTop = verticalPadding
        val drawableAreaBottom = h - labelHeightPx
        val drawableAreaHeight = drawableAreaBottom - drawableAreaTop

        val targetY = drawableAreaTop + drawableAreaHeight * targetYFraction

        for (i in 0 until 7) {
            val centerX = (i + 0.5f) * columnWidth
            val night = data[i]
            val targetColor = ContextCompat.getColor(context, R.color.adherence_target)

            // Compute actual dot position
            val (actualY, actualColor) = computeActualDotPosition(
                night, targetY, drawableAreaTop, drawableAreaHeight
            )

            // Draw connector line (only if we have data and the actual dot is below the target)
            if (night.adherence != Adherence.NO_DATA && actualY != null && actualY > targetY + dotRadiusPx * 2) {
                connectorPaint.color = ContextCompat.getColor(context, R.color.adherence_connector)
                canvas.drawLine(
                    centerX,
                    targetY + dotRadiusPx,
                    centerX,
                    actualY - dotRadiusPx,
                    connectorPaint
                )
            }

            // Draw target dot
            if (night.adherence != Adherence.NO_DATA) {
                dotPaint.color = targetColor
                canvas.drawCircle(centerX, targetY, dotRadiusPx, dotPaint)
            }

            // Draw actual dot
            if (actualY != null) {
                dotPaint.color = actualColor
                canvas.drawCircle(centerX, actualY, dotRadiusPx, dotPaint)
            }

            // Day label at the bottom
            val labelY = h - dpToPx(8f)
            canvas.drawText(dayLabels[i], centerX, labelY, labelPaint)
        }
    }

    /**
     * Returns (yPosition, color) for the actual dot.
     * Y positions are computed as fractions of the drawable area so dots never
     * escape the chart bounds regardless of container size.
     */
    private fun computeActualDotPosition(
        night: BedtimeAdherenceCalculator.NightAdherence,
        targetY: Float,
        drawableAreaTop: Float,
        drawableAreaHeight: Float
    ): Pair<Float?, Int> {
        return when (night.adherence) {
            Adherence.ADHERENT -> {
                val y = drawableAreaTop + drawableAreaHeight * onTimeYFraction
                y to ContextCompat.getColor(context, R.color.adherence_on_time)
            }
            Adherence.SLIGHT_DELAY -> {
                val y = drawableAreaTop + drawableAreaHeight * slightDelayYFraction
                y to ContextCompat.getColor(context, R.color.adherence_slight_delay)
            }
            Adherence.SIGNIFICANT_DELAY -> {
                val y = drawableAreaTop + drawableAreaHeight * lateYFraction
                y to ContextCompat.getColor(context, R.color.adherence_late)
            }
            Adherence.NO_DATA -> {
                // Same Y as target row, navy color, no connecting line
                targetY to ContextCompat.getColor(context, R.color.adherence_no_data)
            }
        }
    }

    private fun drawEmptyState(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val columnWidth = w / 7f

        // 1. Draw the day labels faintly
        val originalColor = labelPaint.color
        val originalAlpha = labelPaint.alpha
        labelPaint.color = ContextCompat.getColor(context, R.color.adherence_no_data)
        labelPaint.alpha = 100

        for (i in 0 until 7) {
            val centerX = (i + 0.5f) * columnWidth
            val labelY = h - dpToPx(8f)
            canvas.drawText(dayLabels[i], centerX, labelY, labelPaint)
        }

        // 2. Draw centered "No data" caption
        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.adherence_no_data)
            textSize = labelPaint.textSize * 1.1f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("No bedtime data for this week", w / 2f, h / 2f, captionPaint)

        // Restore paint state
        labelPaint.color = originalColor
        labelPaint.alpha = originalAlpha
    }

    private fun dpToPx(dp: Float): Float =
        dp * resources.displayMetrics.density
}