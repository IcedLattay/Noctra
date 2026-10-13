package com.noctra.app.ui.analytics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.noctra.app.R

/**
 * Thirty-day sleep-score trend (ANALYTICS_SPEC.md §2.2).
 *
 * Hand-rolled rather than drawn with a chart library, per §3: "gap-breaking +
 * min-4 averaging + custom legend fight chart libraries". MPAndroidChart
 * cannot break a line at a missing point without splitting datasets, cannot
 * express a trailing average that shrinks its own divisor, and cannot align
 * axis-line ends flush to tick-label edges. Each of those is load-bearing here,
 * so this is ~200 lines of Canvas instead.
 *
 * Two series, both of which must break at the same gaps:
 *  - the nightly score, one small dot per night
 *  - a trailing-7 average, drawn only where at least [MIN_SAMPLES] nights in
 *    that window carry a score
 *
 * §2.2: "a missing night hosts no anchor itself but still counts as history for
 * its neighbors" — so a gap suppresses a point on *both* series, it does not
 * shorten the averaging window for the nights either side of it.
 */
class SleepTrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var points: List<Int?> = List(TREND_DAYS) { null }
    private var averages: List<Double?> = List(TREND_DAYS) { null }
    private var labels: List<String> = emptyList()

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val avgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dpToPx(1f)
        style = Paint.Style.STROKE
    }
    private val tickLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dpToPx(10f)
        color = ContextCompat.getColor(context, R.color.analytics_muted)
    }
    private val path = Path()

    private val dotRadiusPx = dpToPx(3f)   // §6: dots 3dp
    private val tickGapPx = dpToPx(6f)

    /** §6: plot area minimum 200dp tall so dots never read cramped. */
    private val minPlotHeightPx = dpToPx(200f)

    private val yTicks = listOf(0, 25, 50, 75, 100)

    /**
     * @param points   one score per night in the window, null where unmeasured
     * @param averages trailing-7 mean per night, null where it cannot be drawn
     * @param labels   first / middle / last date labels for the x axis
     */
    fun setData(points: List<Int?>, averages: List<Double?>, labels: List<String>) {
        require(points.size == averages.size) {
            "points (${points.size}) and averages (${averages.size}) must line up"
        }
        this.points = points
        this.averages = averages
        this.labels = labels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) return

        // ── Geometry ──────────────────────────────────────────────────
        // Y labels sit to the left of the axis line; the widest one sets the inset.
        val widestTick = yTicks.maxOf { tickLabelPaint.measureText(it.toString()) }
        val axisLeft = paddingLeft + widestTick + tickGapPx
        val axisRight = (width - paddingRight).toFloat()
        // §6: guarantee the plot never collapses below 200dp.
        val axisTop = paddingTop.toFloat()
        val axisBottom = (height - paddingBottom - tickLabelHeightPx()).coerceAtLeast(
            axisTop + minPlotHeightPx
        )

        drawAxes(canvas, axisLeft, axisTop, axisRight, axisBottom)
        drawAverage(canvas, axisLeft, axisRight, axisTop, axisBottom)
        drawDots(canvas, axisLeft, axisRight, axisTop, axisBottom)
        drawXLabels(canvas, axisLeft, axisRight, axisBottom)
    }

    // ─── Axes ────────────────────────────────────────────────────────────

    /**
     * §6: "Axis lines span exactly label-edge to label-edge (top of '100' flush
     * with line end, bottom of '0' flush with line start) — tick labels never
     * stick out past the line." So the line does NOT span the plot rect; it
     * spans the measured extent of its own tick labels, and each label is
     * positioned against the line end rather than centred on a tick.
     */
    private fun drawAxes(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val axisColor = ContextCompat.getColor(context, R.color.analytics_axis)
        axisPaint.color = axisColor

        val fm = tickLabelPaint.fontMetrics
        // Height of a single line of tick text.
        val textHeight = fm.descent - fm.ascent

        // "100" hangs from the top of the line; "0" sits on the bottom of it.
        val topLabelTop = top
        val topLabelBaseline = topLabelTop - fm.ascent
        val bottomLabelBottom = bottom
        val bottomLabelBaseline = bottomLabelBottom - fm.descent

        canvas.drawLine(left, top, left, bottom, axisPaint)
        canvas.drawLine(left, bottom, right, bottom, axisPaint)

        tickLabelPaint.textAlign = Paint.Align.RIGHT
        for (tick in yTicks) {
            val y = valueToY(tick.toDouble(), top, bottom)
            val baseline = when (tick) {
                100 -> topLabelBaseline
                0 -> bottomLabelBaseline
                else -> y - fm.ascent / 2f
            }
            canvas.drawText(tick.toString(), left - tickGapPx, baseline, tickLabelPaint)
        }
    }

    private fun drawXLabels(canvas: Canvas, left: Float, right: Float, bottom: Float) {
        if (labels.isEmpty()) return
        val fm = tickLabelPaint.fontMetrics
        val baseline = bottom + tickGapPx - fm.ascent
        tickLabelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(labels.first(), left, baseline, tickLabelPaint)

        if (labels.size >= 3) {
            tickLabelPaint.textAlign = Paint.Align.CENTER
            val midX = (left + right) / 2f
            canvas.drawText(labels[labels.size / 2], midX, baseline, tickLabelPaint)
        }

        // Right label's right edge sits flush with the line end.
        tickLabelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(labels.last(), right, baseline, tickLabelPaint)
    }

    // ─── Series ──────────────────────────────────────────────────────────

    /**
     * The average breaks at the same gaps as the score series. Drawing it with
     * a single Path would connect across a hole in the data, so it is stroked
     * one contiguous run at a time.
     */
    private fun drawAverage(
        canvas: Canvas,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float
    ) {
        avgPaint.color = ContextCompat.getColor(context, R.color.analytics_trend_avg)
        var started = false
        path.reset()

        averages.forEachIndexed { index, value ->
            if (value == null) {
                if (started) { canvas.drawPath(path, avgPaint); path.reset(); started = false }
                return@forEachIndexed
            }
            val x = slotToX(index, left, right)
            val y = valueToY(value, top, bottom)
            if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
        }
        if (started) canvas.drawPath(path, avgPaint)
    }

    private fun drawDots(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float) {
        dotPaint.color = ContextCompat.getColor(context, R.color.analytics_trend_dot)
        points.forEachIndexed { index, score ->
            // A missing night gets no anchor at all — not a dot at zero.
            if (score == null) return@forEachIndexed
            canvas.drawCircle(
                slotToX(index, left, right),
                valueToY(score.toDouble(), top, bottom),
                dotRadiusPx,
                dotPaint
            )
        }
    }

    // ─── Scales ──────────────────────────────────────────────────────────

    private fun valueToY(value: Double, top: Float, bottom: Float): Float =
        (bottom - (value.coerceIn(0.0, 100.0) / 100.0) * (bottom - top)).toFloat()

    private fun slotToX(index: Int, left: Float, right: Float): Float {
        val slots = points.size.coerceAtLeast(1)
        if (slots == 1) return left
        return left + (right - left) * index / (slots - 1).toFloat()
    }

    private fun tickLabelHeightPx(): Float {
        val fm = tickLabelPaint.fontMetrics
        return (fm.descent - fm.ascent) + tickGapPx
    }

    private fun dpToPx(dp: Float): Float =
        dp * resources.displayMetrics.density

    companion object {
        const val TREND_DAYS = 30
        /** §2.2: minimum scored nights in the trailing window before it draws. */
        const val MIN_SAMPLES = 4
    }
}