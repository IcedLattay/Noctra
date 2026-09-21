package com.noctra.app.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.button.MaterialButton
import com.noctra.app.R
import com.noctra.app.domain.usecase.BedtimeAdherenceCalculator
import com.noctra.app.domain.usecase.InsightGenerationUseCase
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class AnalyticsDashboardFragment : Fragment(R.layout.fragment_analytics_dashboard) {

    private val viewModel: AnalyticsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ─── Shared views ─────────────────────────────────────────────
        val mainContent = view.findViewById<View>(R.id.mainContent)
        val noInternetView = view.findViewById<View>(R.id.noInternetView)

        // Toggle
        val btnWeekly = view.findViewById<TextView>(R.id.btn_weekly)
        val btnMonthly = view.findViewById<TextView>(R.id.btn_monthly)

        // Weekly views
        val weeklyContent = view.findViewById<LinearLayout>(R.id.weekly_content)
        val weekRangeLabel = view.findViewById<TextView>(R.id.text_week_range)
        val btnPrevWeek = view.findViewById<ImageView>(R.id.btn_week_prev)
        val btnNextWeek = view.findViewById<ImageView>(R.id.btn_week_next)

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
        val sleepQualityChart = view.findViewById<LineChart>(R.id.chart_sleep_quality)
        SleepQualityChartConfig.configure(sleepQualityChart, requireContext())
        sleepQualityChart.setNoDataText("No sleep quality data for this week")
        sleepQualityChart.setNoDataTextColor(requireContext().getColor(R.color.adherence_no_data))

        val labelRoutineCompletion = view.findViewById<TextView>(R.id.label_routine_completion)
        val insightText = view.findViewById<TextView>(R.id.insight_text)

        // Monthly views
        val monthlyContent = view.findViewById<LinearLayout>(R.id.monthly_content)
        val btnPrevMonth = view.findViewById<ImageView>(R.id.btn_month_prev)
        val btnNextMonth = view.findViewById<ImageView>(R.id.btn_month_next)
        val textMonthRange = view.findViewById<TextView>(R.id.text_month_range)
        val chartWeeklyAdherence = view.findViewById<BarChart>(R.id.chart_weekly_adherence)
        val chartMonthlySleepQuality = view.findViewById<LineChart>(R.id.chart_monthly_sleep_quality)
        val textMonthlyCompletionPct = view.findViewById<TextView>(R.id.text_monthly_completion_pct)
        val textMonthlyCompletionDetail = view.findViewById<TextView>(R.id.text_monthly_completion_detail)
        val viewMonthlyCompletionFill = view.findViewById<View>(R.id.view_monthly_completion_fill)
        val textMonthInsightDashboard = view.findViewById<TextView>(R.id.text_month_insight_dashboard)
        val btnViewMonthlyReport = view.findViewById<MaterialButton>(R.id.btn_view_monthly_report)

        // Monthly Last Night card
        val lastNightDateMonthly = view.findViewById<TextView>(R.id.last_night_date_monthly)
        val lastNightScoreMonthly = view.findViewById<TextView>(R.id.last_night_score_monthly)
        val lastNightLabelMonthly = view.findViewById<TextView>(R.id.last_night_label_monthly)
        val statDurationMonthly = view.findViewById<TextView>(R.id.stat_duration_monthly)
        val statOnsetMonthly = view.findViewById<TextView>(R.id.stat_sleep_onset_monthly)
        val statHrMonthly = view.findViewById<TextView>(R.id.stat_avg_hr_monthly)
        val statRestlessnessMonthly = view.findViewById<TextView>(R.id.stat_restlessness_monthly)

        // ─── Toggle handlers ──────────────────────────────────────────
        btnWeekly.setOnClickListener { viewModel.setViewMode(AnalyticsViewMode.WEEKLY) }
        btnMonthly.setOnClickListener { viewModel.setViewMode(AnalyticsViewMode.MONTHLY) }

        // ─── Navigation handlers ──────────────────────────────────────
        btnPrevWeek.setOnClickListener { viewModel.previousWeek(requireContext()) }
        btnNextWeek.setOnClickListener { viewModel.nextWeek(requireContext()) }
        btnPrevMonth.setOnClickListener { viewModel.previousMonth(requireContext()) }
        btnNextMonth.setOnClickListener { viewModel.nextMonth(requireContext()) }

        // ─── View Full Monthly Report ─────────────────────────────────
        btnViewMonthlyReport.setOnClickListener {
            val bundle = Bundle().apply {
                putString("year_month", viewModel.state.value.selectedMonth.toString())
            }
            try {
                androidx.navigation.fragment.NavHostFragment
                    .findNavController(this@AnalyticsDashboardFragment)
                    .navigate(R.id.action_analytics_to_monthlyReport, bundle)
            } catch (e: Exception) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.nav_host, MonthlyReportFragment::class.java, bundle)
                    .addToBackStack(null)
                    .commit()
            }
            // Hide bottom nav during report
            try {
                val bottomNav = requireActivity().findViewById<View>(R.id.bottom_nav)
                bottomNav?.visibility = View.GONE
            } catch (_: Exception) {}
        }

        // ─── State collection ─────────────────────────────────────────
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                // Handle offline
                if (state.isOffline) {
                    noInternetView.visibility = View.VISIBLE
                    mainContent.visibility = View.GONE
                    setupRetryButton(view)
                    return@collect
                }
                noInternetView.visibility = View.GONE
                mainContent.visibility = View.VISIBLE

                // Update toggle visuals
                when (state.viewMode) {
                    AnalyticsViewMode.WEEKLY -> {
                        btnWeekly.setBackgroundResource(R.drawable.bg_view_toggle_active)
                        btnWeekly.setTextColor(Color.parseColor("#522ABE"))
                        btnMonthly.setBackgroundResource(R.drawable.bg_view_toggle_inactive)
                        btnMonthly.setTextColor(requireContext().getColor(R.color.noctra_label_grey))
                        weeklyContent.visibility = View.VISIBLE
                        monthlyContent.visibility = View.GONE
                    }
                    AnalyticsViewMode.MONTHLY -> {
                        btnMonthly.setBackgroundResource(R.drawable.bg_view_toggle_active)
                        btnMonthly.setTextColor(Color.parseColor("#522ABE"))
                        btnWeekly.setBackgroundResource(R.drawable.bg_view_toggle_inactive)
                        btnWeekly.setTextColor(requireContext().getColor(R.color.noctra_label_grey))
                        weeklyContent.visibility = View.GONE
                        monthlyContent.visibility = View.VISIBLE
                    }
                }

                // ─── Weekly view updates ──────────────────────────────
                weekRangeLabel.text = state.weekRangeLabel
                btnNextWeek.alpha = if (state.canGoForward) 1.0f else 0.3f

                val scores = buildScoresByDay(state.weekStart, state.weekSleepRecords)
                SleepQualityChartConfig.setData(sleepQualityChart, requireContext(), scores)

                val targetBedtimeTime = parseTargetBedtime(state.targetBedtime)
                val adherence = adherenceCalculator.classifyWeek(
                    weekStart = state.weekStart,
                    targetBedtime = targetBedtimeTime,
                    sleepRecords = state.weekSleepRecords
                )
                bedtimeAdherenceChart.setData(adherence)

                val statuses = buildCompletionStatuses(state.weekStart, state.weekSessions)
                routineCompletionChart.setData(statuses)

                // Last Night card (weekly)
                populateLastNightCard(
                    state.lastNightRecord, lastNightDate, lastNightScore,
                    lastNightLabel, statDuration, statOnset, statHr, statRestlessness
                )

                // Routine Completion header
                val total = state.weekSessions.size
                if (total == 0) {
                    labelRoutineCompletion.text = "ROUTINE COMPLETION - NO DATA THIS WEEK"
                } else {
                    val completed = state.weekSessions.count { it.status == "COMPLETED" }
                    val pct = (completed * 100 / total)
                    labelRoutineCompletion.text =
                        "ROUTINE COMPLETION - $completed OF $total NIGHTS ($pct%)"
                }

                // Insight
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

                // ─── Monthly view updates ─────────────────────────────
                textMonthRange.text = state.monthRangeLabel
                btnNextMonth.alpha = if (state.canGoForwardMonth) 1.0f else 0.3f

                // Last Night card (monthly — same data)
                populateLastNightCard(
                    state.lastNightRecord, lastNightDateMonthly, lastNightScoreMonthly,
                    lastNightLabelMonthly, statDurationMonthly, statOnsetMonthly,
                    statHrMonthly, statRestlessnessMonthly
                )

                // Weekly adherence bar chart
                populateWeeklyAdherenceChart(chartWeeklyAdherence, state.weeklyBreakdownData)

                // 30-day sleep quality trend
                populateMonthlySleepQualityChart(
                    chartMonthlySleepQuality, state.monthSleepRecords
                )

                // Monthly completion
                val completionPct = if (state.monthlyTotalDays > 0) {
                    (state.monthlyTotalCompleted * 100 / state.monthlyTotalDays)
                } else 0
                textMonthlyCompletionPct.text = "$completionPct%"
                textMonthlyCompletionDetail.text =
                    "${state.monthlyTotalCompleted} of ${state.monthlyTotalDays} nights completed"

                val fillRatio = if (state.monthlyTotalDays > 0) {
                    state.monthlyTotalCompleted.toFloat() / state.monthlyTotalDays
                } else 0f
                viewMonthlyCompletionFill.post {
                    val parent = viewMonthlyCompletionFill.parent as? ViewGroup ?: return@post
                    val params = viewMonthlyCompletionFill.layoutParams
                    params.width = (parent.width * fillRatio).toInt()
                    viewMonthlyCompletionFill.layoutParams = params
                }

                // Month insight
                when (val result = state.monthlyInsightResult) {
                    is com.noctra.app.domain.usecase.MonthlyInsightGenerationUseCase.Result.Insight -> {
                        textMonthInsightDashboard.text = result.message
                    }
                    is com.noctra.app.domain.usecase.MonthlyInsightGenerationUseCase.Result.InsufficientData -> {
                        textMonthInsightDashboard.text = result.message
                    }
                    else -> {}
                }
            }
        }

        viewModel.load(requireContext())
    }

    // ─── Populate last night card (shared logic) ──────────────────────
    private fun populateLastNightCard(
        record: com.noctra.app.data.model.SleepRecord?,
        dateView: TextView,
        scoreView: TextView,
        labelView: TextView,
        durationView: TextView,
        onsetView: TextView,
        hrView: TextView,
        restlessnessView: TextView
    ) {
        if (record != null) {
            dateView.text = formatDate(record.sessionDate)
            scoreView.text = record.compositeScore?.toString() ?: "—"
            scoreView.setTextColor(scoreColor(record.compositeScore))
            labelView.text = qualityLabel(record.compositeScore)
            durationView.text = formatDuration(record.sleepDurationMinutes)
            onsetView.text = formatOnsetTime(record.sleepOnsetTime)
            hrView.text = record.avgHeartRateBpm?.let { "${it.toInt()} bpm" } ?: "—"
            restlessnessView.text = restlessnessLabel(record.movementEventCount)
        } else {
            dateView.text = ""
            scoreView.text = "—"
            labelView.text = "No sleep data recorded last night"
            durationView.text = "—"
            onsetView.text = "—"
            hrView.text = "—"
            restlessnessView.text = "—"
        }
    }

    // ─── Populate weekly adherence bar chart ───────────────────────────
    private fun populateWeeklyAdherenceChart(
        chart: BarChart,
        breakdownData: List<MonthlyWeeklyBreakdownData>
    ) {
        if (breakdownData.isEmpty()) {
            chart.clear()
            return
        }

        val entries = breakdownData.mapIndexed { index, data ->
            BarEntry(index.toFloat(), data.barRate.toFloat())
        }

        val colors = breakdownData.map { data ->
            when {
                data.barRate >= 75 -> Color.parseColor("#2E9F66")  // green
                data.barRate >= 57 -> Color.parseColor("#E8A33D")  // orange
                else -> Color.parseColor("#D4183D")                // red
            }
        }

        val dataSet = BarDataSet(entries, "Adherence Rate").apply {
            this.colors = colors
            setDrawValues(true)
            valueTextSize = 12f
            valueTextColor = Color.parseColor("#16056E")
        }

        chart.data = BarData(dataSet).apply {
            barWidth = 0.6f
        }

        chart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setDrawGridBackground(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                valueFormatter = IndexAxisValueFormatter(
                    breakdownData.map { "Wk ${it.weekNumber}" }
                )
                textSize = 12f
                textColor = Color.parseColor("#16056E")
            }

            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                setDrawGridLines(false)
                setDrawLabels(false)
                setDrawAxisLine(false)
            }

            axisRight.isEnabled = false
            animateY(800)
            invalidate()
        }
    }

    // ─── Populate monthly sleep quality line chart ─────────────────────
    private fun populateMonthlySleepQualityChart(
        chart: LineChart,
        sleepRecords: List<com.noctra.app.data.model.SleepRecord>
    ) {
        if (sleepRecords.isEmpty()) {
            chart.clear()
            chart.setNoDataText("No sleep quality data for this month")
            return
        }

        val sorted = sleepRecords
            .filter { it.compositeScore != null }
            .sortedBy { it.sessionDate }

        if (sorted.isEmpty()) {
            chart.clear()
            return
        }

        val entries = sorted.mapIndexed { index, record ->
            Entry(index.toFloat(), record.compositeScore!!.toFloat())
        }

        val pointColors = sorted.map { record ->
            when {
                record.compositeScore!! >= 75 -> Color.parseColor("#2E9F66")
                record.compositeScore!! >= 50 -> Color.parseColor("#E8A33D")
                else -> Color.parseColor("#D4183D")
            }
        }

        val dataSet = LineDataSet(entries, "Sleep Quality").apply {
            color = Color.parseColor("#A78BFA")
            lineWidth = 2f
            setDrawCircles(true)
            circleRadius = 3f
            setCircleColors(pointColors)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(false)
        }

        // Reference line at 75
        val referenceLine = LimitLine(75f, "Good (75)").apply {
            lineColor = Color.parseColor("#9E9E9E")
            lineWidth = 1f
            enableDashedLine(10f, 10f, 0f)
            textColor = Color.parseColor("#9E9E9E")
            textSize = 10f
        }

        chart.data = LineData(dataSet)
        chart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setDrawGridBackground(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 5f
                labelCount = 6
                valueFormatter = IndexAxisValueFormatter(
                    sorted.map {
                        try {
                            LocalDate.parse(it.sessionDate)
                                .format(DateTimeFormatter.ofPattern("d"))
                        } catch (e: Exception) { "" }
                    }
                )
                textSize = 10f
                textColor = Color.parseColor("#9E9E9E")
            }

            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#F0F0F0")
                setDrawLabels(true)
                textSize = 10f
                textColor = Color.parseColor("#9E9E9E")
                addLimitLine(referenceLine)
            }

            axisRight.isEnabled = false
            animateX(800)
            invalidate()
        }
    }

    // ─── Formatting helpers ──────────────────────────────────────────────

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
            score >= 75 -> android.graphics.Color.parseColor("#2E9F66")
            score >= 50 -> android.graphics.Color.parseColor("#E8A33D")
            else -> android.graphics.Color.parseColor("#D4183D")
        }
    }

    private fun restlessnessLabel(count: Int?): String = when {
        count == null -> "—"
        count <= 10 -> "Low"
        count <= 30 -> "Moderate"
        else -> "High"
    }

    private fun buildCompletionStatuses(
        weekStart: LocalDate,
        sessions: List<com.noctra.app.data.model.RoutineSession>
    ): List<RoutineCompletionRowView.DayStatus> {
        val byDate = sessions.associateBy { it.sessionDate }
        return (0..6).map { dayOffset ->
            val date = weekStart.plusDays(dayOffset.toLong())
            val session = byDate[date.toString()]
            when {
                session == null -> RoutineCompletionRowView.DayStatus.NO_DATA
                session.status == "COMPLETED" -> RoutineCompletionRowView.DayStatus.COMPLETED
                else -> RoutineCompletionRowView.DayStatus.INCOMPLETE
            }
        }
    }

    private fun buildScoresByDay(
        weekStart: LocalDate,
        sleepRecords: List<com.noctra.app.data.model.SleepRecord>
    ): List<Int?> {
        val byDate = sleepRecords.associateBy { it.sessionDate }
        return (0..6).map { dayOffset ->
            val date = weekStart.plusDays(dayOffset.toLong())
            val record = byDate[date.toString()]
            record?.compositeScore
        }
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
