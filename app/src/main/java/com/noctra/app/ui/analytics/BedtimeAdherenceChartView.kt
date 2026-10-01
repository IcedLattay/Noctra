package com.noctra.app.ui.analytics

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.noctra.app.R
import com.noctra.app.domain.usecase.BedtimeAdherenceCalculator
import com.noctra.app.domain.usecase.BedtimeAdherenceCalculator.Adherence

/**
 * Bedtime pairs for the 7-day block (ANALYTICS_SPEC.md §2.1, §6 item 4).
 *
 * Per night: a hollow dashed ring at the target bedtime row, and — only when
 * sleep onset was actually recorded — a filled dot below it, joined by a short
 * connector whose length grows with the delay. Colour encodes the adherence
 * band: on-time, slight delay, late.
 *
 * §2.1: a night with no data shows the ring alone, with no dot and no
 * connector, so "we did not measure this" never looks like "we measured zero
 * delay". §3: the view is read-only.
 */
class BedtimeAdherenceChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<BedtimeAdherenceCalculator.NightAdherence> = emptyList()
    private var labels: List<String> = emptyList()

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f)
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(3f), dpToPx(3f)), 0f)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dpToPx(2f)
        strokeCap = Paint.Cap.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dpToPx(11f)
        color = ContextCompat.getColor(context, R.color.analytics_muted)
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dpToPx(13f)
        color = ContextCompat.getColor(context, R.color.analytics_muted)
    }

    // [MEASURE] against NEW_ANALYTICS_UI.png: the dashed target ring reads
    // ~14dp across and the actual dot ~12dp, so the ring sits just proud of
    // the dot it anchors.
    private val ringRadiusPx = dpToPx(7f)
    private val dotRadiusPx = dpToPx(6f)
    private val labelHeightPx = dpToPx(20f)
    private val verticalPadding = dpToPx(10f)

    // Vertical bands for the actual dot. Fractions of the drawable area so
    // nothing escapes the bounds at any container height.
    private val onTimeYFraction = 0.30f
    private val slightDelayYFraction = 0.55f
    private val lateYFraction = 0.82f

    /**
     * @param weekAdherence one entry per night in the window
     * @param dateLabels    one date label per night, already formatted
     */
    fun setData(weekAdherence: List<BedtimeAdherenceCalculator.NightAdherence>, dateLabels: List<String>) {
        require(weekAdherence.size == dateLabels.size) {
            "adherence (${weekAdherence.size}) and labels (${dateLabels.size}) must line up"
        }
        data = weekAdherence
        labels = dateLabels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val columnWidth = w / data.size

        val drawableTop = verticalPadding
        val drawableHeight = h - labelHeightPx - drawableTop
        val targetY = drawableTop + drawableHeight * 0.10f

        val ringColor = ContextCompat.getColor(context, R.color.adherence_target)
        val connectorColor = ContextCompat.getColor(context, R.color.adherence_connector)

        for (i in data.indices) {
            val centerX = (i + 0.5f) * columnWidth
            val night = data[i]
            val hasData = night.adherence != Adherence.NO_DATA

            if (hasData) {
                val (actualY, actualColor) = actualDotPosition(
                    night, targetY, drawableTop, drawableHeight
                )
                if (actualY != null) {
                    connectorPaint.color = connectorColor
                    canvas.drawLine(
                        centerX, targetY + ringRadiusPx,
                        centerX, actualY - dotRadiusPx,
                        connectorPaint
                    )
                    dotPaint.color = actualColor
                    canvas.drawCircle(centerX, actualY, dotRadiusPx, dotPaint)
                }
            }

            // Hollow dashed target ring — drawn last so it reads as the anchor
            // even when the connector passes behind it.
            ringPaint.color = ringColor
            canvas.drawCircle(centerX, targetY, ringRadiusPx, ringPaint)

            val labelY = h - dpToPx(6f)
            canvas.drawText(labels.getOrElse(i) { "" }, centerX, labelY, labelPaint)
        }

        if (data.all { it.adherence == Adherence.NO_DATA }) {
            canvas.drawText(
                context.getString(R.string.analytics_no_bedtime_data),
                w / 2f, h / 2f, captionPaint
            )
        }
    }

    private fun actualDotPosition(
        night: BedtimeAdherenceCalculator.NightAdherence,
        targetY: Float,
        drawableTop: Float,
        drawableHeight: Float
    ): Pair<Float?, Int> = when (night.adherence) {
        Adherence.ADHERENT -> (drawableTop + drawableHeight * onTimeYFraction) to
            ContextCompat.getColor(context, R.color.adherence_on_time)
        Adherence.SLIGHT_DELAY -> (drawableTop + drawableHeight * slightDelayYFraction) to
            ContextCompat.getColor(context, R.color.adherence_slight_delay)
        Adherence.SIGNIFICANT_DELAY -> (drawableTop + drawableHeight * lateYFraction) to
            ContextCompat.getColor(context, R.color.adherence_late)
        // No measurement: the ring above stands alone, so the view must not
        // also draw a dot sitting on the target row.
        Adherence.NO_DATA -> null to
            ContextCompat.getColor(context, R.color.adherence_no_data)
    }

    private fun dpToPx(dp: Float): Float =
        dp * resources.displayMetrics.density
}
