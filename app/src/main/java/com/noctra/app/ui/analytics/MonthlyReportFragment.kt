package com.noctra.app.ui.analytics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.noctra.app.R
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class MonthlyReportFragment : Fragment(R.layout.fragment_monthly_report) {

    private val viewModel: MonthlyReportViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<ImageView>(R.id.btn_back)
        val textMonthLabel = view.findViewById<TextView>(R.id.text_month_label)
        val textAdherenceRate = view.findViewById<TextView>(R.id.text_adherence_rate)
        val textAvgScore = view.findViewById<TextView>(R.id.text_avg_score)
        val textCompletionRate = view.findViewById<TextView>(R.id.text_completion_rate)
        val textCompletionDetail = view.findViewById<TextView>(R.id.text_completion_detail)
        val viewCompletionFill = view.findViewById<View>(R.id.view_completion_fill)
        val weeklyContainer = view.findViewById<LinearLayout>(R.id.weekly_breakdown_container)
        val cardMonthInsight = view.findViewById<LinearLayout>(R.id.card_month_insight)
        val textMonthInsight = view.findViewById<TextView>(R.id.text_month_insight)
        val textBestScore = view.findViewById<TextView>(R.id.text_best_score)
        val textBestDate = view.findViewById<TextView>(R.id.text_best_date)
        val textWorstScore = view.findViewById<TextView>(R.id.text_worst_score)
        val textWorstDate = view.findViewById<TextView>(R.id.text_worst_date)

        val btnExport = view.findViewById<MaterialButton>(R.id.btn_export)
        val exportLoading = view.findViewById<LinearLayout>(R.id.export_loading)
        val exportSuccess = view.findViewById<LinearLayout>(R.id.export_success)
        val exportError = view.findViewById<LinearLayout>(R.id.export_error)
        val textDownloadLink = view.findViewById<TextView>(R.id.text_download_link)
        val btnCopyLink = view.findViewById<MaterialButton>(R.id.btn_copy_link)
        val btnRetryExport = view.findViewById<MaterialButton>(R.id.btn_retry_export)

        val mainContent = view.findViewById<View>(R.id.mainContent)
        val noInternetView = view.findViewById<View>(R.id.noInternetView)

        btnBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        // Get yearMonth from arguments (default to current month)
        val yearMonthArg = arguments?.getString("year_month")
        val yearMonth = if (yearMonthArg != null) {
            try { YearMonth.parse(yearMonthArg) } catch (e: Exception) { YearMonth.now() }
        } else {
            YearMonth.now()
        }

        // Export button
        btnExport.setOnClickListener {
            viewModel.exportReport(requireContext())
        }
        btnRetryExport.setOnClickListener {
            viewModel.exportState.value.let {
                viewModel.resetExportState()
            }
            viewModel.exportReport(requireContext())
        }

        // Copy link
        btnCopyLink.setOnClickListener {
            val link = textDownloadLink.text.toString()
            if (link.isNotBlank()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Report Link", link)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Link copied!", Toast.LENGTH_SHORT).show()
            }
        }

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                if (state.isOffline) {
                    noInternetView.visibility = View.VISIBLE
                    mainContent.visibility = View.GONE
                    return@collect
                }
                noInternetView.visibility = View.GONE
                mainContent.visibility = View.VISIBLE

                textMonthLabel.text = state.monthLabel
                textAdherenceRate.text = "${state.bedtimeAdherenceRate.toInt()}%"
                textAvgScore.text = state.avgSleepQualityScore?.let { "%.0f".format(it) } ?: "—"

                // Completion
                val completionPct = if (state.totalDaysInMonth > 0) {
                    (state.totalRoutinesCompleted * 100 / state.totalDaysInMonth)
                } else 0
                textCompletionRate.text = "$completionPct%"
                textCompletionDetail.text =
                    "${state.totalRoutinesCompleted} of ${state.totalDaysInMonth} nights completed"

                // Fill bar
                val fillRatio = if (state.totalDaysInMonth > 0) {
                    state.totalRoutinesCompleted.toFloat() / state.totalDaysInMonth
                } else 0f
                viewCompletionFill.post {
                    val parent = viewCompletionFill.parent as? ViewGroup ?: return@post
                    val params = viewCompletionFill.layoutParams
                    params.width = (parent.width * fillRatio).toInt()
                    viewCompletionFill.layoutParams = params
                }

                // Weekly breakdown
                weeklyContainer.removeAllViews()
                state.weeklyBreakdown.forEach { week ->
                    val rowView = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_weekly_breakdown_row, weeklyContainer, false)

                    rowView.findViewById<TextView>(R.id.text_week_label).text =
                        "Week ${week.weekNumber}"
                    rowView.findViewById<TextView>(R.id.text_bar_rate).text =
                        "${week.barRate.toInt()}%"
                    rowView.findViewById<TextView>(R.id.text_routines).text =
                        "${week.routinesCompleted}/${week.daysInWeek}"
                    rowView.findViewById<TextView>(R.id.text_avg_score).text =
                        week.avgSleepScore?.let { "%.0f".format(it) } ?: "—"

                    val badge = rowView.findViewById<TextView>(R.id.text_quality_badge)
                    val score = week.avgSleepScore
                    when {
                        score == null -> {
                            badge.text = "—"
                            badge.setBackgroundColor(requireContext().getColor(R.color.completion_grey))
                        }
                        score >= 75 -> {
                            badge.text = "Great"
                            badge.setBackgroundColor(requireContext().getColor(R.color.quality_good))
                        }
                        score >= 50 -> {
                            badge.text = "Good"
                            badge.setBackgroundColor(requireContext().getColor(R.color.quality_moderate))
                        }
                        else -> {
                            badge.text = "Poor"
                            badge.setBackgroundColor(requireContext().getColor(R.color.quality_poor))
                        }
                    }

                    weeklyContainer.addView(rowView)
                }

                // Month insight
                if (state.monthInsight != null) {
                    cardMonthInsight.visibility = View.VISIBLE
                    textMonthInsight.text = state.monthInsight
                } else {
                    cardMonthInsight.visibility = View.GONE
                }

                // Best / worst nights
                if (state.bestNightScore != null) {
                    textBestScore.text = state.bestNightScore.toString()
                    textBestDate.text = formatDateShort(state.bestNightDate)
                } else {
                    textBestScore.text = "—"
                    textBestDate.text = "No data"
                }

                if (state.worstNightScore != null) {
                    textWorstScore.text = state.worstNightScore.toString()
                    textWorstDate.text = formatDateShort(state.worstNightDate)
                } else {
                    textWorstScore.text = "—"
                    textWorstDate.text = "No data"
                }
            }
        }

        // Export state observer
        lifecycleScope.launch {
            viewModel.exportState.collect { exportState ->
                when (exportState) {
                    is ExportState.Idle -> {
                        btnExport.visibility = View.VISIBLE
                        exportLoading.visibility = View.GONE
                        exportSuccess.visibility = View.GONE
                        exportError.visibility = View.GONE
                    }
                    is ExportState.Loading -> {
                        btnExport.visibility = View.GONE
                        exportLoading.visibility = View.VISIBLE
                        exportSuccess.visibility = View.GONE
                        exportError.visibility = View.GONE
                    }
                    is ExportState.Success -> {
                        btnExport.visibility = View.GONE
                        exportLoading.visibility = View.GONE
                        exportSuccess.visibility = View.VISIBLE
                        exportError.visibility = View.GONE
                        // Show "Saved locally" for local file exports
                        val filePath = exportState.url
                        textDownloadLink.text = "Saved: ${File(filePath).name}"
                        // Add share button
                        btnCopyLink.text = "Share"
                        btnCopyLink.setOnClickListener {
                            try {
                                val file = File(filePath)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM,
                                        FileProvider.getUriForFile(
                                            requireContext(),
                                            "${requireContext().packageName}.fileprovider",
                                            file
                                        )
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(Intent.createChooser(shareIntent, "Share Report"))
                            } catch (e: Exception) {
                                // Fallback: copy path
                                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Report Path", filePath)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(requireContext(), "Path copied!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    is ExportState.Error -> {
                        btnExport.visibility = View.GONE
                        exportLoading.visibility = View.GONE
                        exportSuccess.visibility = View.GONE
                        exportError.visibility = View.VISIBLE
                    }
                }
            }
        }

        viewModel.load(requireContext(), yearMonth)
    }

    private fun formatDateShort(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return ""
        return try {
            LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("MMM d"))
        } catch (e: Exception) {
            isoDate
        }
    }
}
