package com.noctra.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord

/**
 * HealthConnectPermissionHelper
 *
 * Single source of truth for Health Connect availability and permission state.
 * Used by both the sync pipeline (SleepSyncManager) and the health UI flow
 * (pre-flight, grant, settings screens) so they never disagree.
 */
object HealthConnectPermissionHelper {

    /**
     * The permissions Noctra requests. Sleep is mandatory for the sync pipeline;
     * heart rate is optional (scoring redistributes its weight when missing).
     */
    val READ_SLEEP_PERMISSION: String =
        HealthPermission.getReadPermission(SleepSessionRecord::class)

    val READ_HEART_RATE_PERMISSION: String =
        HealthPermission.getReadPermission(HeartRateRecord::class)

    // ------------------------------------------------------------------
    // Availability
    // ------------------------------------------------------------------

    fun getSdkStatus(context: Context): Int =
        HealthConnectClient.getSdkStatus(context)

    /**
     * True when Health Connect is present and usable. On Android < 14 this
     * requires the Health Connect module to be installed via Play Store.
     */
    fun isAvailable(context: Context): Boolean =
        getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /**
     * True when the Health Connect module needs installing/updating
     * (Android < 14 without the APK). Callers should show an install link.
     */
    fun needsInstall(context: Context): Boolean =
        getSdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    /**
     * Play Store intent to install/update the Health Connect module
     * (documented fallback for devices where HC is not built in).
     */
    fun getInstallIntent(): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.android.vending")
            data = Uri.parse(
                "market://details?id=com.google.android.apps.healthdata" +
                    "&url=utm_source%3Dfirst_party_sdk%26utm_medium%3Dlink" +
                    "%26utm_campaign%3Dhealth_connect"
            )
        }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

    /**
     * Returns the set of health permissions the user has currently granted.
     * Caller must hold a HealthConnectClient (suspend — hits the HC store).
     */
    suspend fun getGrantedPermissions(client: HealthConnectClient): Set<String> =
        client.permissionController.getGrantedPermissions()

    fun hasSleepPermission(granted: Set<String>): Boolean =
        READ_SLEEP_PERMISSION in granted

    fun hasHeartRatePermission(granted: Set<String>): Boolean =
        READ_HEART_RATE_PERMISSION in granted
}
