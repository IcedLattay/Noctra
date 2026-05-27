package com.noctra.app.ui.analytics

import android.content.Context
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.noctra.app.R

/**
 * One-time configuration for the Sleep Quality LineChart.
 * Call [setData] each time the data needs to update.
 */
object SleepQualityChartConfig {

    private val dayLabels = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /**
     * Apply all the chart's static configuration (axes, legend, interaction, etc.).
     * Only needs to be called once after the LineChart is inflated.
     */
    fun configure(chart: LineChart, context: Context) {
        // Description (the watermark text in the bottom-right) — we don't want it
        chart.description.isEnabled = false

        // Built-in legend — we're using our own legend below the chart
        chart.legend.isEnabled = false

        // Right Y-axis is redundant; disable it
        chart.axisRight.isEnabled = false

        // Left Y-axis: no labels per design, but we need a range
        chart.axisLeft.apply {
            axisMinimum = 0f
            axisMaximum = 100f
            setDrawLabels(false)
            setDrawAxisLine(false)
            setDrawGridLines(false)

            // Reference line at 75 (the "Good" threshold)
            removeAllLimitLines()
            val limitLine = LimitLine(75f, "").apply {
                lineColor = ContextCompat.getColor(context, R.color.quality_reference_line)
                lineWidth = 1f
                enableDashedLine(8f, 6f, 0f)  // dashed: 8px on, 6px off
            }
            addLimitLine(limitLine)
        }

        // X-axis: day labels along the bottom
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            setDrawAxisLine(false)
            granularity = 1f
            valueFormatter = IndexAxisValueFormatter(dayLabels)
            textColor = ContextCompat.getColor(context, R.color.noctra_purple_dark)
            textSize = 12f
        }

        // Touch and zoom behavior
        chart.setTouchEnabled(true)
        chart.setScaleEnabled(false)        // no zoom
        chart.setPinchZoom(false)
        chart.isDragEnabled = false

        // Marker (tap-to-show-value popup)
        val marker = SleepQualityMarkerView(context)
        marker.chartView = chart
        chart.marker = marker

        // Extra padding so the marker doesn't get clipped at the top
        chart.setExtraOffsets(0f, 16f, 0f, 0f)
    }

    /**
     * Populate the chart with 7 days of scores. Pass `null` for days with no data.
     * @param scoresByDay must be exactly 7 entries, Monday→Sunday.
     */
    fun setData(chart: LineChart, context: Context, scoresByDay: List<Int?>) {
        require(scoresByDay.size == 7) { "Expected 7 scores, got ${scoresByDay.size}" }

        // Build entries — skip null days (creates gaps in the line)
        val entries = scoresByDay.mapIndexedNotNull { index, score ->
            if (score != null) Entry(index.toFloat(), score.toFloat()) else null
        }

        if (entries.isEmpty()) {
            chart.clear()
            chart.invalidate()
            return
        }

        // Per-dot colors based on score thresholds
        val dotColors = entries.map { entry ->
            colorForScore(entry.y.toInt(), context)
        }

        val dataSet = LineDataSet(entries, "Sleep Quality").apply {
            // Line styling
            color = ContextCompat.getColor(context, R.color.quality_good)
            lineWidth = 2.5f
            setDrawValues(false)  // no permanent labels; we use the marker instead

            // Dot styling
            setDrawCircles(true)
            circleRadius = 5f
            circleHoleRadius = 2f
            setCircleColors(dotColors)
            setDrawCircleHole(true)
            setCircleHoleColor(android.graphics.Color.WHITE)

            // Smooth line through the dots
            mode = LineDataSet.Mode.LINEAR

            // No fill below the line
            setDrawFilled(false)

            // Highlight (when user taps a dot)
            highLightColor = ContextCompat.getColor(context, R.color.noctra_purple_light)
            highlightLineWidth = 1f
            isHighlightEnabled = true
        }

        chart.data = LineData(dataSet)
        chart.invalidate()  // trigger redraw
        chart.animateY(600)  // brief grow-in animation
    }

    private fun colorForScore(score: Int, context: Context): Int = when {
        score >= 75 -> ContextCompat.getColor(context, R.color.quality_good)
        score >= 50 -> ContextCompat.getColor(context, R.color.quality_moderate)
        else -> ContextCompat.getColor(context, R.color.quality_poor)
    }
}