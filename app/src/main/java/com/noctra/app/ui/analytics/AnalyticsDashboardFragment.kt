package com.noctra.app.ui.analytics

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.noctra.app.R
import com.noctra.app.domain.usecase.BedtimeAdherenceCalculator
import com.noctra.app.domain.usecase.InsightGenerationUseCase
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class AnalyticsDashboardFragment : Fragment(R.layout.fragment_analytics_dashboard) {

    private val viewModel: AnalyticsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val weekRangeLabel = view.findViewById<TextView>(R.id.text_week_range)
        val btnPrev = view.findViewById<ImageView>(R.id.btn_week_prev)
        val btnNext = view.findViewById<ImageView>(R.id.btn_week_next)

        val lastNightDate = view.findViewById<TextView>(R.id.last_night_date)
        val lastNightScore = view.findViewById<TextView>(R.id.last_night_score)
        val lastNightLabel = view.findViewById<TextView>(R.id.last_night_label)
        val statDuration = view.findViewById<TextView>(R.id.stat_duration)
        val statOnset = view.findViewById<TextView>(R.id.stat_sleep_onset)
        val statHr = view.findViewById<TextView>(R.id.stat_avg_hr)
        val statRestlessness = view.findViewById<TextView>(R.id.stat_restlessness)

        val bedtimeAdherenceChart = view.findViewById<BedtimeAdherenceChartView>(R.id.chart_bedtime_adherence)
        val adherenceCalculator = BedtimeAdherenceCalculator()
        val routineCompletionChart = view.findViewById<RoutineCompletionRowView>(R.id.routine_completion_chart)
        val sleepQualityChart = view.findViewById<com.github.mikephil.charting.charts.LineChart>(R.id.chart_sleep_quality)
        SleepQualityChartConfig.configure(sleepQualityChart, requireContext())

        // Set empty state text for the chart
        sleepQualityChart.setNoDataText("No sleep quality data for this week")
        sleepQualityChart.setNoDataTextColor(requireContext().getColor(R.color.adherence_no_data))

        val labelRoutineCompletion = view.findViewById<TextView>(R.id.label_routine_completion)
        val insightText = view.findViewById<TextView>(R.id.insight_text)

        val mainContent = view.findViewById<View>(R.id.mainContent)
        val noInternetView = view.findViewById<View>(R.id.noInternetView)


        // Detail block navigation — ±1 day per tap (§3 small steps)
        btnPrev.setOnClickListener { viewModel.shiftDetail(-1) }
        btnNext.setOnClickListener { viewModel.shiftDetail(+1) }

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                // Handle offline state
                if (state.isOffline) {
                    noInternetView.visibility = View.VISIBLE
                    mainContent.visibility = View.GONE
                    setupRetryButton(view)
                    return@collect
                } else {
                    noInternetView.visibility = View.GONE
                    mainContent.visibility = View.VISIBLE
                }

                weekRangeLabel.text = formatRange(state.detailStart, 7)
                btnNext.alpha = if (viewModel.canShiftDetailForward()) 1.0f else 0.3f
                btnPrev.alpha = if (viewModel.canShiftDetailBack()) 1.0f else 0.3f

                // Sleep Quality chart — nulls become gaps, never zeros
                SleepQualityChartConfig.setData(sleepQualityChart, requireContext(), state.detailScores)

                // Bedtime Adherence chart, built from the onsets already resolved
                // for this window so the pair chart and the completion cells can
                // never disagree about which nights exist.
                val targetBedtimeTime = parseTargetBedtime(state.targetBedtime)
                val adherence = (0 until 7).map { offset ->
                    adherenceCalculator.classify(
                        sessionDate = state.detailStart.plusDays(offset.toLong()),
                        targetBedtime = targetBedtimeTime,
                        sleepOnsetTime = state.detailOnsets.getOrNull(offset)?.toString()
                    )
                }
                bedtimeAdherenceChart.setData(adherence)

                // Completion cells
                routineCompletionChart.setData(state.detailCompletion)

                // §2.1: the cells speak — no totals, no percentages. Phase 4
                // removes the summary label from the layout entirely.
                labelRoutineCompletion.text = ""

                // Last Night card
                val record = state.lastNightRecord
                if (record != null) {
                    lastNightDate.text = formatDate(record.sessionDate)
                    lastNightScore.text = record.compositeScore?.toString() ?: "—"
                    lastNightScore.setTextColor(scoreColor(record.compositeScore))
                    lastNightLabel.text = qualityLabel(record.compositeScore)
                    statDuration.text = formatDuration(record.sleepDurationMinutes)
                    statOnset.text = formatOnsetTime(record.sleepOnsetTime)
                    statHr.text = record.avgHeartRateBpm?.let { "${it.toInt()} bpm" } ?: "—"
                    statRestlessness.text = restlessnessLabel(record.movementEventCount)
                } else {
                    lastNightDate.text = ""
                    lastNightScore.text = "—"
                    lastNightLabel.text = "No sleep data recorded last night"
                    statDuration.text = "—"
                    statOnset.text = "—"
                    statHr.text = "—"
                    statRestlessness.text = "—"
                }

                // Insight computation
                when (val result = state.insightResult) {
                    is InsightGenerationUseCase.Result.Insight -> {
                        insightText.text = result.message
                        insightText.alpha = 1.0f
                    }
                    is InsightGenerationUseCase.Result.InsufficientData -> {
                        insightText.text = result.message
                        insightText.alpha = 0.7f
                    }
                    null -> {
                        insightText.text = "Loading insight..."
                        insightText.alpha = 0.5f
                    }
                }
            }
        }

        viewModel.load(requireContext())
    }

    // ─── Formatting helpers ──────────────────────────────────────────────

    /**
     * §7 locked range format: "Jun 14, 2026 - Jun 20, 2026". Plain dates only —
     * no Monday/Sunday framing, no month names standing in for a range.
     */
    private fun formatRange(start: LocalDate, days: Int): String {
        val end = start.plusDays((days - 1).toLong())
        return "${start.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))} - " +
            end.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }

    private fun formatDate(isoDate: String): String = try {
        LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } catch (e: Exception) { isoDate }

    private fun formatDuration(minutes: Int?): String {
        if (minutes == null) return "—"
        val h = minutes / 60
        val m = minutes % 60
        return "${h}h ${m}m"
    }

    private fun formatOnsetTime(isoTimestamp: String?): String {
        if (isoTimestamp == null) return "—"
        return try {
            // Parse as UTC then convert to local time
            val instant = java.time.Instant.parse(isoTimestamp)
            val localTime = LocalDateTime.ofInstant(instant, ZoneOffset.systemDefault())
            localTime.format(DateTimeFormatter.ofPattern("h:mm a"))
        } catch (e: Exception) {
            isoTimestamp
        }
    }

    private fun qualityLabel(score: Int?): String = when {
        score == null -> ""
        score >= 75 -> "Good sleep quality"
        score >= 50 -> "Moderate sleep quality"
        else -> "Poor sleep quality"
    }

    private fun scoreColor(score: Int?): Int {
        val ctx = requireContext()
        return when {
            score == null -> ctx.getColor(R.color.noctra_label_grey)
            score >= 75 -> android.graphics.Color.parseColor("#2E9F66")  // green
            score >= 50 -> android.graphics.Color.parseColor("#E8A33D")  // orange
            else -> android.graphics.Color.parseColor("#D4183D")          // red
        }
    }

    private fun restlessnessLabel(count: Int?): String = when {
        count == null -> "—"
        count <= 10 -> "Low"
        count <= 30 -> "Moderate"
        else -> "High"
    }

    private fun parseTargetBedtime(stored: String?): java.time.LocalTime? {
        if (stored.isNullOrBlank()) return null
        return try {
            java.time.LocalTime.parse(stored)
        } catch (e: Exception) {
            null
        }
    }

    private fun setupRetryButton(view: View) {
        val noInternetView = view.findViewById<View>(R.id.noInternetView) ?: return
        val btnRetry = noInternetView.findViewById<android.widget.ImageButton>(R.id.btnRetry)
        val progressRetry = noInternetView.findViewById<android.widget.ProgressBar>(R.id.progressRetry)
        val tvRetry = noInternetView.findViewById<TextView>(R.id.tvRetry)

        btnRetry.setOnClickListener {
            btnRetry.visibility = View.GONE
            progressRetry.visibility = View.VISIBLE
            tvRetry.text = "retrying..."
            viewModel.retry(requireContext())
        }
    }
}
