package com.noctra.app.ui.analytics

import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.noctra.app.R

/**
 * Custom marker shown when the user taps a data point on the Sleep Quality chart.
 * Displays the numeric score value (e.g., "82") in a small dark purple pill.
 */
class SleepQualityMarkerView(context: Context) :
    MarkerView(context, R.layout.marker_sleep_quality) {

    private val valueText: TextView = findViewById(R.id.marker_value)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        valueText.text = e?.y?.toInt()?.toString() ?: "—"
        super.refreshContent(e, highlight)
    }

    // Position the marker centered horizontally above the data point
    override fun getOffset(): MPPointF =
        MPPointF((-width / 2f), (-height - 12f))
}