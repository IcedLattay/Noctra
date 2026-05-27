package com.noctra.app.ui.analytics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.noctra.app.R

/**
 * Displays 7 vertical pill-shaped bars representing routine completion status
 * for each day of the week (Monday through Sunday).
 *
 * Call [setData] to update the chart with the week's session statuses.
 */
class RoutineCompletionRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class DayStatus { COMPLETED, INCOMPLETE, NO_DATA }

    // Default: 7 days of "no data" — overwritten by setData()
    private var statuses: List<DayStatus> = List(7) { DayStatus.NO_DATA }
    private val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dpToPx(12f)
        color = ContextCompat.getColor(context, R.color.noctra_purple_dark)
        isFakeBoldText = false
    }

    private val labelHeightPx = dpToPx(20f)   // space below bars for labels
    private val barSpacingPx = dpToPx(8f)     // horizontal space between bars
    private val barCornerPx = dpToPx(20f)     // pill corner radius

    private val barRect = RectF()

    /**
     * Update the data and redraw.
     * @param statuses must be exactly 7 entries, one per day Mon→Sun
     */
    fun setData(statuses: List<DayStatus>) {
        require(statuses.size == 7) { "Expected 7 statuses, got ${statuses.size}" }
        this.statuses = statuses
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        val totalSpacing = barSpacingPx * 6  // gaps between 7 bars
        val barWidth = (w - totalSpacing) / 7f
        val barAreaHeight = h - labelHeightPx

        for (i in 0 until 7) {
            val left = i * (barWidth + barSpacingPx)
            val right = left + barWidth
            barRect.set(left, 0f, right, barAreaHeight)

            paint.color = colorForStatus(statuses[i])
            canvas.drawRoundRect(barRect, barCornerPx, barCornerPx, paint)

            // Day label below
            val labelX = (left + right) / 2f
            val labelY = h - dpToPx(4f)
            canvas.drawText(dayLabels[i], labelX, labelY, labelPaint)
        }
    }

    private fun colorForStatus(status: DayStatus): Int = when (status) {
        DayStatus.COMPLETED -> ContextCompat.getColor(context, R.color.completion_green)
        DayStatus.INCOMPLETE -> ContextCompat.getColor(context, R.color.completion_pink)
        DayStatus.NO_DATA -> ContextCompat.getColor(context, R.color.completion_grey)
    }

    private fun dpToPx(dp: Float): Float =
        dp * resources.displayMetrics.density
}