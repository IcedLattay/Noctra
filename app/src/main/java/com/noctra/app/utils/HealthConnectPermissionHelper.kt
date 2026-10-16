package com.noctra.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
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
     * One-shot availability diagnosis for bug reports. Logs the raw SDK
     * status + OS info so a single logcat screenshot answers what state
     * the device is in, instead of reverse-engineering from UI copy:
     *
     *   status=0 SDK_AVAILABLE              → HC present and blessed
     *   status=1 SDK_UNAVAILABLE            → OS can't run HC at all
     *   status=2 UPDATE_REQUIRED            → module present but older
     *                                          than this SDK requires
     *                                          (reads may still work —
     *                                          the handshake is advisory)
     *   status=3 UPGRADE_REQUIRED           → Android 14+ with a stale
     *                                          Mainline module (fix =
     *                                          Google Play system update,
     *                                          NOT the Play Store app page)
     */
    fun logDiagnostics(context: Context) {
        val status = getSdkStatus(context)
        val label = when (status) {
            HealthConnectClient.SDK_AVAILABLE -> "SDK_AVAILABLE"
            HealthConnectClient.SDK_UNAVAILABLE -> "SDK_UNAVAILABLE (cannot run HC)"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "UPDATE_REQUIRED (module present, too old for this SDK)"
            // = SDK_UNAVAILABLE_PROVIDER_UPGRADE_REQUIRED in newer androidx SDKs
            // (absent from 1.1.0): Android 14+ with a stale Mainline module
            3 -> "UPGRADE_REQUIRED (14+ Mainline module stale — Google Play system update)"
            else -> "UNKNOWN status"
        }
        Log.d(
            "HealthConnectDiag",
            "status=$status $label | SDK=${Build.VERSION.SDK_INT} (Android ${Build.VERSION.RELEASE}) | ${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }

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

    // ------------------------------------------------------------------
    // Management deep link
    // ------------------------------------------------------------------

    /**
     * Opens Health Connect's per-app management page for this app (the
     * full Settings screen with live toggles). Used when the one-shot
     * request popup can no longer show — Health Connect suppresses
     * re-prompts after denials, and Google prescribes Settings routing
     * from that point on. Falls back to general HC settings.
     */
    fun openAppManagement(context: Context) {
        val intent = Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").apply {
            putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        }
    }
}
