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
    private var targetLabels: List<String> = emptyList()
    private var brackets: List<Pair<Int, Int>> = emptyList()
    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f)
        color = ContextCompat.getColor(context, R.color.analytics_muted)
    }
    private val targetLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dpToPx(11f)
        color = ContextCompat.getColor(context, R.color.analytics_muted)
    }

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
    // Tall top padding: the per-night hour labels sit above the target
    // rings and must never clip.
    private val verticalPadding = dpToPx(24f)

    // Vertical bands for the actual dot. Fractions of the drawable area so
    // nothing escapes the bounds at any container height.
    private val onTimeYFraction = 0.30f
    private val slightDelayYFraction = 0.55f
    private val lateYFraction = 0.82f

    /**
     * @param weekAdherence one entry per night in the window
     * @param dateLabels    one date label per night, already formatted
     * @param targetLabels  one hour-only target label per night ("10 PM")
     */
    fun setData(
        weekAdherence: List<BedtimeAdherenceCalculator.NightAdherence>,
        dateLabels: List<String>,
        targetLabels: List<String> = emptyList()
    ) {
        require(weekAdherence.size == dateLabels.size) {
            "adherence (${weekAdherence.size}) and labels (${dateLabels.size}) must line up"
        }
        data = weekAdherence
        labels = dateLabels
        this.targetLabels = targetLabels
        brackets = computeBrackets(targetLabels)
        invalidate()
    }

    /**
     * Bracket runs clipped to the visible window: a line joins the endpoint
     * labels of a run of equal targets ONLY when at least one day sits in
     * between (3+ nights) — adjacent pairs need no line. Blank (rowless)
     * nights break runs.
     */
    private fun computeBrackets(targets: List<String>): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < targets.size) {
            val label = targets[i]
            if (label.isEmpty()) {
                i++
                continue
            }
            var j = i
            while (j + 1 < targets.size && targets[j + 1] == label) j++
            if (j - i >= 2) out += i to j
            i = j + 1
        }
        return out
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
        val targetLabelY = targetY - ringRadiusPx - dpToPx(6f)

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

            // Hour-only target value for this night ("10 PM").
            // Middles of a bracket stay blank — endpoints carry the run.
            val inBracketMiddle = brackets.any { (from, to) -> i > from && i < to }
            canvas.drawText(
                if (inBracketMiddle) "" else targetLabels.getOrElse(i) { "" },
                centerX, targetLabelY, targetLabelPaint
            )

            val labelY = h - dpToPx(6f)
            canvas.drawText(labels.getOrElse(i) { "" }, centerX, labelY, labelPaint)
        }

        if (data.all { it.adherence == Adherence.NO_DATA }) {
            canvas.drawText(
                context.getString(R.string.analytics_no_bedtime_data),
                w / 2f, h / 2f, captionPaint
            )
        }

        // Regime brackets: one line joining the endpoint labels of each run.
        for ((from, to) in brackets) {
            val startX = (from + 0.5f) * columnWidth
            val endX = (to + 0.5f) * columnWidth
            val y = targetLabelY - dpToPx(12f)
            canvas.drawLine(startX, y, endX, y, bracketPaint)
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
