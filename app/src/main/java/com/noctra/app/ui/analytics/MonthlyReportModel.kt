package com.noctra.app.ui.analytics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable model for the monthly report exported as JSON to Supabase Storage.
 */
@Serializable
data class MonthlyReportModel(
    @SerialName("user_id") val userId: String,
    @SerialName("month") val month: String,                    // "2026-05"
    @SerialName("bedtime_adherence_rate") val bedtimeAdherenceRate: Double,
    @SerialName("total_routines_completed") val totalRoutinesCompleted: Int,
    @SerialName("total_days_in_month") val totalDaysInMonth: Int,
    @SerialName("avg_sleep_quality_score") val avgSleepQualityScore: Double,
    @SerialName("weekly_breakdown") val weeklyBreakdown: List<WeeklyBreakdownModel>,
    @SerialName("best_night_score") val bestNightScore: Int?,
    @SerialName("best_night_date") val bestNightDate: String?,
    @SerialName("worst_night_score") val worstNightScore: Int?,
    @SerialName("worst_night_date") val worstNightDate: String?,
    @SerialName("month_insight") val monthInsight: String?,
    @SerialName("exported_at") val exportedAt: String
)

@Serializable
data class WeeklyBreakdownModel(
    @SerialName("week_number") val weekNumber: Int,
    @SerialName("bar_rate") val barRate: Double,
    @SerialName("routines_completed") val routinesCompleted: Int,
    @SerialName("days_in_week") val daysInWeek: Int,
    @SerialName("avg_sleep_score") val avgSleepScore: Double?
)
