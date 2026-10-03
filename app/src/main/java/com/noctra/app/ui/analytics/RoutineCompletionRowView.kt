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
 * One pill cell per night for the 7-day block (ANALYTICS_SPEC.md §2.1, §6 item 5).
 *
 * Three visually distinct states, and the distinction between the last two is
 * the point of the whole exercise:
 *
 * | state       | fill                          | meaning                              |
 * |-------------|-------------------------------|--------------------------------------|
 * | COMPLETED   | solid green                   | routine finished                     |
 * | MISSED      | solid pink                    | decided, not done — includes no row  |
 * | PENDING     | solid yellow                  | awaiting its grace-period verdict    |
 *
 * MISSED and PENDING must never share a colour: a night still in flight is not
 * a failure, and painting it pink would punish the user for a verdict that has
 * not landed yet.
 *
 * §3: read-only — this view never consumes touches.
 */
class RoutineCompletionRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * Per-night completion state for the 7-day block.
     *
     * UPCOMING is not a data state — it marks a slot whose night has not
     * happened yet. §2.1 requires it to be visibly scaffolding: an empty slot
     * with a faint date label and no outline, so it can never be misread as a
     * night we measured and found missed.
     */
    enum class DayStatus { COMPLETED, MISSED, PENDING, UPCOMING }

    private var statuses: List<DayStatus> = List(DETAIL_DAYS) { DayStatus.UPCOMING }
    private var labels: List<String> = emptyList()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dpToPx(11f)
        color = ContextCompat.getColor(context, R.color.analytics_muted)
    }

    private val labelHeightPx = dpToPx(18f)
    private val cellSpacingPx = dpToPx(12f)   // §6: 12dp gaps
    // [MEASURE] against NEW_ANALYTICS_UI.png: cells read roughly 38dp wide by
    // 50dp tall (ratio ~0.76), with a near-stadium corner close to half the
    // cell height. The container height is what pins the cell height — see
    // the FrameLayout in fragment_analytics_dashboard.xml.
    private val cornerPx = dpToPx(20f)
    private val cellRect = RectF()

    /**
     * @param statuses   one entry per night in the window
     * @param dateLabels one date label per night, already formatted
     */
    fun setData(statuses: List<DayStatus>, dateLabels: List<String>) {
        require(statuses.size == dateLabels.size) {
            "statuses (${statuses.size}) and labels (${dateLabels.size}) must line up"
        }
        this.statuses = statuses
        this.labels = dateLabels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (statuses.isEmpty()) return

        val count = statuses.size
        val totalSpacing = cellSpacingPx * (count - 1)
        val cellWidth = (width - totalSpacing) / count
        val cellHeight = height - labelHeightPx

        for (i in 0 until count) {
            val left = i * (cellWidth + cellSpacingPx)
            val right = left + cellWidth
            cellRect.set(left, 0f, right, cellHeight)

            when (statuses[i]) {
                // Not yet: no fill, no outline, faint label. Scaffolding, not a mark.
                DayStatus.UPCOMING -> Unit
                else -> {
                    fillPaint.color = colorForStatus(statuses[i])
                    canvas.drawRoundRect(cellRect, cornerPx, cornerPx, fillPaint)
                }
            }

            canvas.drawText(
                labels.getOrElse(i) { "" },
                (left + right) / 2f,
                height - dpToPx(5f),
                labelPaint
            )
        }
    }

    private fun colorForStatus(status: DayStatus): Int = when (status) {
        DayStatus.COMPLETED -> ContextCompat.getColor(context, R.color.completion_green)
        DayStatus.MISSED -> ContextCompat.getColor(context, R.color.completion_pink)
        // Pending reuses the shared yellow per spec §6's overwrite rule.
        DayStatus.PENDING -> ContextCompat.getColor(context, R.color.noctra_health_yellow)
        DayStatus.UPCOMING -> ContextCompat.getColor(context, R.color.analytics_card_bg)
    }

    private fun dpToPx(dp: Float): Float =
        dp * resources.displayMetrics.density
}
