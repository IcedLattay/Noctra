package com.noctra.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SleepRecord(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("session_date") val sessionDate: String,
    @SerialName("sleep_onset_time") val sleepOnsetTime: String? = null,
    @SerialName("wake_time") val wakeTime: String? = null,
    @SerialName("sleep_duration_minutes") val sleepDurationMinutes: Int? = null,
    @SerialName("avg_heart_rate_bpm") val avgHeartRateBpm: Double? = null,
    @SerialName("movement_event_count") val movementEventCount: Int? = null,
    @SerialName("hr_baseline_at_scoring") val hrBaselineAtScoring: Double? = null,
    @SerialName("duration_score") val durationScore: Int? = null,
    @SerialName("heart_rate_score") val heartRateScore: Int? = null,
    @SerialName("movement_score") val movementScore: Int? = null,
    @SerialName("composite_score") val compositeScore: Int? = null,
    @SerialName("is_partial_data") val isPartialData: Boolean = false,
    @SerialName("data_capture_success") val dataCaptureSuccess: Boolean = true
)
