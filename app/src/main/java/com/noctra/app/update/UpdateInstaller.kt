package com.noctra.app.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * UpdateInstaller — the "hands" of the updater: download, verify, install.
 * The UI state machine lives in UpdateFlowDialogFragment; this object owns
 * only mechanics, so nothing here knows about dialogs or screens.
 *
 * Downloads go through Android's system DownloadManager: it survives the
 * app being backgrounded, resumes dropped connections, and queues on flaky
 * networks. Destination is the app-specific external files directory (no
 * storage permission needed). The download id + version are persisted so a
 * dismissed dialog can re-attach to an in-flight or finished download on the
 * next open.
 */
object UpdateInstaller {

    private const val TAG = "UpdateInstaller"
    private const val PREFS = "noctra_update_install"
    private const val KEY_ACTIVE_DOWNLOAD_ID = "active_download_id"
    private const val KEY_ACTIVE_VERSION = "active_version"

    /** One probe of a download's current state. */
    sealed interface ProbeResult {
        data class Running(val bytesRead: Long, val totalBytes: Long) : ProbeResult
        object Success : ProbeResult
        data class Failed(val reason: Int) : ProbeResult
        /** Never existed, or removed/cancelled elsewhere — UI falls back to the offer state. */
        object Missing : ProbeResult
    }

    /** Result of checksum verification — see verify() for the degradation rules. */
    enum class VerifyResult { MATCH, MISMATCH, NOT_PROVIDED, MISSING_FILE }

    fun probe(context: Context, id: Long, fallbackTotal: Long): ProbeResult {
        downloadManager(context).query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return ProbeResult.Missing
            return when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> ProbeResult.Success
                DownloadManager.STATUS_FAILED -> ProbeResult.Failed(
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                )
                else -> {
                    val read = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
                    val totalColumn = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                    // The API's own size is the fallback when the server
                    // doesn't announce Content-Length (-1).
                    val total = if (totalColumn > 0) totalColumn else fallbackTotal
                    ProbeResult.Running(read, total)
                }
            }
        }
    }

    fun apkFileName(version: String) = "Noctra-$version.apk"

    fun apkFile(context: Context, version: String): File? =
        context.getExternalFilesDir(null)?.let { File(it, apkFileName(version)) }

    // ─── Download ────────────────────────────────────────────────────────────

    /** Enqueues the download; returns the system download id (persisted). */
    fun startDownload(context: Context, info: UpdateChecker.UpdateInfo): Long {
        val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("Noctra ${info.version}")
            .setDescription("Downloading update")
            // Visible while downloading — modern Android rejects HIDDEN from
            // apps outright (SecurityException "Invalid value for visibility:
            // 2"), and the ongoing notification is genuinely useful when the
            // dialog is dismissed mid-download. NOT visible after completion:
            // a lingering "download complete" tap would hand the raw APK to
            // the system installer, bypassing our verification.
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, null, apkFileName(info.version))

        val id = downloadManager(context).enqueue(request)
        prefs(context).edit()
            .putLong(KEY_ACTIVE_DOWNLOAD_ID, id)
            .putString(KEY_ACTIVE_VERSION, info.version)
            .apply()
        Log.d(TAG, "download enqueued id=$id version=${info.version}")
        return id
    }

    /** Cancels the in-flight download and removes the partial file. */
    fun cancel(context: Context, id: Long) {
        runCatching { downloadManager(context).remove(id) }
        clearActive(context)
    }

    fun activeDownloadId(context: Context): Long =
        prefs(context).getLong(KEY_ACTIVE_DOWNLOAD_ID, -1L)

    fun activeVersion(context: Context): String? =
        prefs(context).getString(KEY_ACTIVE_VERSION, null)

    fun clearActive(context: Context) {
        prefs(context).edit()
            .remove(KEY_ACTIVE_DOWNLOAD_ID)
            .remove(KEY_ACTIVE_VERSION)
            .apply()
    }

    // ─── Verify ──────────────────────────────────────────────────────────────

    /**
     * SHA-256 check of the downloaded APK against the release's optional
     * *.sha256 asset. Degradation rules (deliberate):
     *  - no checksum asset published, or the checksum itself can't be
     *    fetched  -> NOT_PROVIDED: proceed (Android still refuses to install
     *    a corrupted APK anyway — this is defense in depth, not a gate)
     *  - checksum fetched but doesn't match -> MISMATCH: caller must delete
     *    the file and re-download
     */
    suspend fun verify(context: Context, info: UpdateChecker.UpdateInfo): VerifyResult =
        withContext(Dispatchers.IO) {
            val file = apkFile(context, info.version)
                ?: return@withContext VerifyResult.MISSING_FILE
            if (!file.exists()) return@withContext VerifyResult.MISSING_FILE

            val expected = fetchExpectedChecksum(info.checksumUrl)
                ?: return@withContext VerifyResult.NOT_PROVIDED

            val actual = sha256Of(file)
            if (actual.equals(expected, ignoreCase = true)) VerifyResult.MATCH
            else {
                Log.d(TAG, "verify: checksum mismatch — deleting bad file")
                file.delete()
                clearActive(context)
                VerifyResult.MISMATCH
            }
        }

    private suspend fun fetchExpectedChecksum(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            HttpClient(Android).use { client ->
                val body = client.get(url).bodyAsText()
                // "hash" or "hash  filename" — first token is the hash.
                body.trim().substringBefore(' ').substringBefore('\n')
                    .lowercase().takeIf { it.length == 64 }
            }
        } catch (e: Exception) {
            Log.d(TAG, "checksum fetch failed (${e.javaClass.simpleName})")
            null // best-effort — see verify()
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun deleteDownloaded(context: Context, version: String) {
        apkFile(context, version)?.let { runCatching { it.delete() } }
        clearActive(context)
    }

    // ─── Install ─────────────────────────────────────────────────────────────

    /** Android 8+ per-app gate: has the user flipped "install unknown apps" for Noctra? */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Intent for the one-time "install unknown apps" toggle. Callers must
     *  launch this through an ActivityResult launcher — the result callback
     *  is what re-checks canInstall() and resumes the flow afterwards. */
    fun installPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )

    /**
     * Hands the APK to the system installer via FileProvider. Android shows
     * its own confirmation ("Do you want to install this update?") — that
     * dialog is unskippable and not ours to replicate. Same signature as the
     * installed app means: upgrade, all local data preserved.
     */
    fun fireInstall(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private fun downloadManager(context: Context): DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
