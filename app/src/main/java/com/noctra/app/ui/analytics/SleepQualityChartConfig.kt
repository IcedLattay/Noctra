package com.noctra.app.ui.analytics

import android.content.Context
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.noctra.app.R

/**
 * Configuration for the 7-day sleep-score line (ANALYTICS_SPEC.md §2.1, §6).
 *
 * Two behaviours here are load-bearing and easy to get wrong:
 *
 *  1. **Gaps are real gaps.** §2.1 requires that "missing nights leave a gap —
 *     the line never connects across gaps or fills them with zeros." A single
 *     MPAndroidChart [LineDataSet] draws between its outermost points no matter
 *     how far apart they are in x, so one dataset would quietly draw a straight
 *     line straight through a missing night. [setData] therefore emits one
 *     dataset per *contiguous run* of scored nights, which makes a gap
 *     structurally impossible rather than merely unlikely.
 *
 *  2. **Read-only.** §3 says dots "take no taps". The tap marker and all
 *     touch/zoom are off — the only controls on this screen are the range
 *     arrows.
 */
object SleepQualityChartConfig {

    private const val DOT_RADIUS_DP = 5f      // ~10dp diameter
    private const val LINE_WIDTH_DP = 2f
    private const val GRID_STEPS = 5           // 0 / 25 / 50 / 75 / 100

    /**
     * Static configuration. Safe to call once per chart instance.
     */
    fun configure(chart: LineChart, context: Context) {
        chart.description.isEnabled = false
        chart.legend.isEnabled = false          // we render our own legend
        chart.axisRight.isEnabled = false

        chart.axisLeft.apply {
            axisMinimum = 0f
            axisMaximum = 100f
            // §6: faint grey gridlines at 0/25/50/75/100
            setDrawGridLines(true)
            gridColor = ContextCompat.getColor(context, R.color.analytics_axis)
            setDrawAxisLine(false)
            setDrawLabels(false)
            granularity = 100f / GRID_STEPS
            axisMaximum = 100f
        }

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            setDrawAxisLine(true)
            axisLineColor = ContextCompat.getColor(context, R.color.analytics_axis)
            granularity = 1f
            setDrawLabels(false)               // we draw date labels in setData
        }

        // §3 read-only charts: no taps, no drag, no zoom, no marker.
        chart.setTouchEnabled(false)
        chart.isDragEnabled = false
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.isHighlightPerTapEnabled = false
        chart.isHighlightPerDragEnabled = false
        chart.marker = null
        chart.setExtraOffsets(0f, 8f, 0f, 0f)
    }

    /**
     * @param scores one entry per night in the window, null where there is no
     *               record. Nulls become gaps, never zeros.
     * @param labels x-axis date labels, one per night, already formatted for
     *               display.
     */
    fun setData(chart: LineChart, context: Context, scores: List<Int?>, labels: List<String>) {
        require(scores.size == labels.size) {
            "scores (${scores.size}) and labels (${labels.size}) must line up"
        }

        chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels.toTypedArray())

        val lineColor = ContextCompat.getColor(context, R.color.analytics_muted)
        val sets = mutableListOf<com.github.mikephil.charting.interfaces.datasets.ILineDataSet>()

        // One dataset per contiguous run, so a gap can never be bridged.
        var run = mutableListOf<Entry>()
        scores.forEachIndexed { index, score ->
            if (score == null) {
                if (run.isNotEmpty()) { sets += run.toDataSet(context, lineColor); run = mutableListOf() }
            } else {
                run += Entry(index.toFloat(), score.toFloat())
            }
        }
        if (run.isNotEmpty()) sets += run.toDataSet(context, lineColor)

        if (sets.isEmpty()) {
            chart.clear()
            chart.invalidate()
            return
        }

        chart.data = LineData(sets)
        chart.invalidate()
        chart.animateY(400)
    }

    private fun List<Entry>.toDataSet(context: Context, lineColor: Int): LineDataSet {
        val dotColors = map { entry ->
            ContextCompat.getColor(
                context,
                when {
                    entry.y >= 75f -> R.color.completion_green
                    entry.y >= 50f -> R.color.noctra_health_yellow
                    else -> R.color.completion_pink
                }
            )
        }
        return LineDataSet(this, "Sleep Score").apply {
            color = lineColor
            lineWidth = dpToPx(context, LINE_WIDTH_DP)
            setDrawCircles(true)
            circleRadius = dpToPx(context, DOT_RADIUS_DP)
            setDrawCircleHole(false)
            setCircleColors(dotColors)
            setDrawValues(false)
            // LINEAR, not CUBIC_BEZIER: a spline would bow into the gap between
            // two runs even when the runs are drawn as separate datasets.
            mode = LineDataSet.Mode.LINEAR
            setDrawFilled(false)
            isHighlightEnabled = false
        }
    }

    private fun dpToPx(context: Context, dp: Float): Float =
        dp * context.resources.displayMetrics.density
}
