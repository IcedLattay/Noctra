package com.noctra.app.ui.analytics

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
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
        val weekCounter = view.findViewById<TextView>(R.id.text_week_counter)
        val variability = view.findViewById<TextView>(R.id.text_variability)
        val btnPrev = view.findViewById<ImageView>(R.id.btn_week_prev)
        val btnNext = view.findViewById<ImageView>(R.id.btn_week_next)

        val lastNightDate = view.findViewById<TextView>(R.id.last_night_date)
        val lastNightScore = view.findViewById<TextView>(R.id.last_night_score)
        val lastNightLabel = view.findViewById<TextView>(R.id.last_night_label)
        val statDuration = view.findViewById<TextView>(R.id.stat_duration)
        val statOnset = view.findViewById<TextView>(R.id.stat_sleep_onset)
        val statHr = view.findViewById<TextView>(R.id.stat_avg_hr)
        val statRestlessness = view.findViewById<TextView>(R.id.stat_restlessness)
        val muted = ContextCompat.getColor(requireContext(), R.color.analytics_muted)
        val score = ContextCompat.getColor(requireContext(), R.color.analytics_score)
        val black = ContextCompat.getColor(requireContext(), R.color.black)

        val bedtimeAdherenceChart = view.findViewById<BedtimeAdherenceChartView>(R.id.chart_bedtime_adherence)
        val adherenceCalculator = BedtimeAdherenceCalculator()
        val routineCompletionChart = view.findViewById<RoutineCompletionRowView>(R.id.routine_completion_chart)
        val sleepQualityChart = view.findViewById<com.github.mikephil.charting.charts.LineChart>(R.id.chart_sleep_quality)
        SleepQualityChartConfig.configure(sleepQualityChart, requireContext())

        // Set empty state text for the chart
        sleepQualityChart.setNoDataText("No sleep quality data for this week")
        sleepQualityChart.setNoDataTextColor(requireContext().getColor(R.color.analytics_muted))

        val insightText = view.findViewById<TextView>(R.id.insight_text)

        // 30-day trend — its own clock, never shared with the block above
        val btnTrendPrev = view.findViewById<ImageView>(R.id.btn_trend_prev)
        val btnTrendNext = view.findViewById<ImageView>(R.id.btn_trend_next)
        val trendRangeLabel = view.findViewById<TextView>(R.id.text_trend_range)
        val trendChart = view.findViewById<SleepTrendChartView>(R.id.chart_trend)
        val trendPlaceholder = view.findViewById<TextView>(R.id.trend_placeholder)

        val mainContent = view.findViewById<View>(R.id.mainContent)
        val noInternetView = view.findViewById<View>(R.id.noInternetView)


        // Detail block navigation — ±1 day per tap (§3 small steps)
        btnPrev.setOnClickListener { viewModel.shiftDetail(-1) }
        btnNext.setOnClickListener { viewModel.shiftDetail(+1) }
        btnTrendPrev.setOnClickListener { viewModel.shiftTrend(-1) }
        btnTrendNext.setOnClickListener { viewModel.shiftTrend(+1) }

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
                weekCounter.text = getString(R.string.analytics_week_counter, state.weekCounter)

                // Real date labels — §3 bans Mon-Sun calendar framing, so all
                // three charts label the same seven real dates.
                val dateLabels = dateLabelsFor(state.detailStart)

                // Sleep Quality chart — nulls become gaps, never zeros
                SleepQualityChartConfig.setData(
                    sleepQualityChart, requireContext(), state.detailScores, dateLabels
                )

                // Bedtime Adherence chart, built from the onsets already resolved
                // for this window so the pair chart and the completion cells can
                // never disagree about which nights exist.
                val targetBedtimeTime = parseTargetBedtime(state.targetBedtime)
                val adherence = dateLabels.indices.map { offset ->
                    adherenceCalculator.classify(
                        sessionDate = state.detailStart.plusDays(offset.toLong()),
                        targetBedtime = targetBedtimeTime,
                        sleepOnsetTime = state.detailOnsets.getOrNull(offset)?.toString()
                    )
                }
                bedtimeAdherenceChart.setData(adherence, dateLabels)

                // Completion cells
                routineCompletionChart.setData(state.detailCompletion, dateLabels)

                // §7 locked caption. No SD yet means no caption at all rather
                // than a fabricated "±0 min".
                variability.text = state.variabilitySd?.let {
                    getString(R.string.analytics_variability, it)
                }.orEmpty()
                variability.visibility =
                    if (state.variabilitySd == null) View.GONE else View.VISIBLE

                // ─── 30-day trend ─────────────────────────────────────────
                trendRangeLabel.text = formatRange(state.trendStart, TREND_DAYS)
                btnTrendNext.alpha = if (viewModel.canShiftTrendForward()) 1.0f else 0.3f
                btnTrendPrev.alpha = if (viewModel.canShiftTrendBack()) 1.0f else 0.3f

                // §2.2: only the trend waits. Everything else shows from night one.
                val showTrend = state.hasEnoughForTrend
                trendPlaceholder.visibility = if (showTrend) View.GONE else View.VISIBLE
                trendChart.visibility = if (showTrend) View.VISIBLE else View.INVISIBLE
                btnTrendPrev.isEnabled = showTrend
                btnTrendNext.isEnabled = showTrend

                if (showTrend) {
                    // First / middle / last date labels only — a 30-point axis of
                    // full dates would be unreadable at phone width.
                    val fmt = DateTimeFormatter.ofPattern("MMM d")
                    val end = state.trendStart.plusDays((TREND_DAYS - 1).toLong())
                    val mid = state.trendStart.plusDays((TREND_DAYS / 2).toLong())
                    trendChart.setData(
                        state.trendPoints,
                        state.trendAverages,
                        listOf(
                            state.trendStart.format(fmt),
                            mid.format(fmt),
                            end.format(fmt)
                        )
                    )
                }

                // Last Night card — §2.4. No window, no navigation: always the
                // most recent row, ignoring both range controls. The score keeps
                // one fixed colour (§6 item 3); the band lives in the label text,
                // so a low score reads as information rather than as an alarm.
                val record = state.lastNightRecord
                if (record != null) {
                    lastNightDate.text = formatDate(record.sessionDate)
                    lastNightScore.text =
                        record.compositeScore?.toString() ?: getString(R.string.analytics_placeholder_dash)
                    // Set explicitly rather than left to the XML: the no-data
                    // branch below mutes these, and a score that arrives after a
                    // failed load must win its colour back.
                    lastNightScore.setTextColor(score)
                    lastNightLabel.text = qualityLabel(record.compositeScore)
                    lastNightLabel.setTextColor(black)
                    statDuration.text = formatDuration(record.sleepDurationMinutes)
                    statOnset.text = formatOnsetTime(record.sleepOnsetTime)
                    statHr.text = record.avgHeartRateBpm?.let { "${it.toInt()} bpm" }
                        ?: getString(R.string.analytics_placeholder_dash)
                    statRestlessness.text = restlessnessLabel(record.movementEventCount)
                } else {
                    // Honest no-data state: muted, and no score number invented.
                    lastNightDate.text = ""
                    lastNightScore.text = getString(R.string.analytics_no_score)
                    lastNightScore.setTextColor(muted)
                    lastNightLabel.text = getString(R.string.analytics_no_sleep_data)
                    lastNightLabel.setTextColor(muted)
                    listOf(
                        statDuration, statOnset, statHr, statRestlessness
                    ).forEach { it.text = getString(R.string.analytics_placeholder_dash) }
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

    /**
     * "Jun 14" for each of the seven nights starting at [start]. All three charts
     * in the block share this list so their x-axes line up pixel for pixel.
     */
    private fun dateLabelsFor(start: LocalDate): List<String> {
        val fmt = DateTimeFormatter.ofPattern("MMM d")
        return (0 until DETAIL_DAYS).map { start.plusDays(it.toLong()).format(fmt) }
    }

    private fun formatDate(isoDate: String): String = try {
        LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } catch (e: Exception) { isoDate }

    private fun formatDuration(minutes: Int?): String {
        if (minutes == null) return getString(R.string.analytics_placeholder_dash)
        val h = minutes / 60
        val m = minutes % 60
        return "${h}h ${m}m"
    }

    private fun formatOnsetTime(isoTimestamp: String?): String {
        if (isoTimestamp == null) return getString(R.string.analytics_placeholder_dash)
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
        score >= 75 -> getString(R.string.analytics_quality_good)
        score >= 50 -> getString(R.string.analytics_quality_moderate)
        else -> getString(R.string.analytics_quality_poor)
    }

    private fun restlessnessLabel(count: Int?): String = when {
        count == null -> getString(R.string.analytics_placeholder_dash)
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
