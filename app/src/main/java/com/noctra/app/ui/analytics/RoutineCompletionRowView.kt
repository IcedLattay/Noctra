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

    /**
     * Per-night completion state for the 7-day block (ANALYTICS_SPEC.md §2.1).
     *
     * The spec requires four visually distinct states, not three:
     *  - COMPLETED  — filled green
     *  - MISSED     — pink; a night with no record counts as missed
     *  - PENDING    — yellow; a session still awaiting its grace-period
     *                 verdict. Never rendered pink, and excluded from every
     *                 rate so a pending night cannot skew a denominator.
     *  - INELIGIBLE — neutral hollow; onboarding finished after this night's
     *                 routine window closed, so the user could not have
     *                 participated. Distinct from both missed and pending.
     *
     * INCOMPLETE and NO_DATA previously collapsed MISSED and "no record" into
     * one grey. They are gone: "no record" is now MISSED per the spec, and the
     * grey fill is freed up for INELIGIBLE.
     */
    enum class DayStatus { COMPLETED, MISSED, PENDING, INELIGIBLE }

    // Default: 7 missed nights — overwritten by setData()
    private var statuses: List<DayStatus> = List(7) { DayStatus.MISSED }
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
        DayStatus.MISSED -> ContextCompat.getColor(context, R.color.completion_pink)
        // Pending reuses the shared yellow per spec §6's overwrite rule.
        DayStatus.PENDING -> ContextCompat.getColor(context, R.color.noctra_health_yellow)
        DayStatus.INELIGIBLE -> ContextCompat.getColor(context, R.color.completion_grey)
    }

    private fun dpToPx(dp: Float): Float =
        dp * resources.displayMetrics.density
}