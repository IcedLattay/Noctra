package com.noctra.app.update

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

/**
 * UpdateChecker — asks GitHub's public Releases API whether a newer Noctra
 * exists. Detection only; the download/install hand-off is Phase 2.
 *
 * Design rules (agreed scope):
 *  - Stateless: no timers, no stored timestamps. The callers decide WHEN to
 *    ask — MainActivity runs this on every cold open, the Settings row runs
 *    it on tap.
 *  - Failures are silent — a GitHub outage must never surface to the user,
 *    and an error simply reads as "no update information".
 *  - The caller guarantees connectivity (MainActivity only invokes this
 *    behind its existing online gate), but this class still treats any
 *    error as "no update information" rather than throwing.
 *  - No auth token: the repo is public, so anonymous requests are fine
 *    (60/hour per IP — one request per cold open is nothing).
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val RELEASES_API =
        "https://api.github.com/repos/IcedLattay/Noctra/releases/latest"
    private const val USER_AGENT = "Noctra-Android"

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
    }

    /** What we need from a GitHub release. Field names map 1:1 to the API. */
    @Serializable
    private data class GhAsset(
        val name: String,
        val browser_download_url: String = "",
        val size: Long = 0
    )

    @Serializable
    private data class GhRelease(
        val tag_name: String = "",
        val html_url: String = "",
        val body: String? = null,
        val assets: List<GhAsset> = emptyList()
    )

    /** A newer release we can offer. */
    data class UpdateInfo(
        val version: String,        // cleaned tag, e.g. "1.2"
        val notes: String,          // release body (may be empty)
        val sizeBytes: Long,
        val downloadUrl: String,    // direct APK URL (Phase 2 uses this)
        val releasePageUrl: String  // human page (Phase 1 opens this)
    )

    sealed class CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult()
        object UpToDate : CheckResult()
        object Failed : CheckResult()
    }

    /**
     * One check against GitHub. Never throws; network problems, rate limits
     * and malformed payloads all collapse into CheckResult.Failed on purpose.
     */
    suspend fun check(): CheckResult {
        val client = HttpClient(Android) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 10_000
            }
        }
        return try {
            val response = client.get(RELEASES_API) {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", USER_AGENT)
            }
            if (response.status.value != 200) {
                Log.d(TAG, "check: HTTP ${response.status.value}")
                return CheckResult.Failed
            }

            val release = json.decodeFromString<GhRelease>(response.bodyAsText())
            val latest = release.tag_name.removePrefix("v")
            if (latest.isBlank()) return CheckResult.Failed

            if (!isNewer(latest, currentVersion())) {
                Log.d(TAG, "check: up to date (latest=$latest)")
                return CheckResult.UpToDate
            }

            // Offer only when an APK asset is actually attached.
            val apk = release.assets.firstOrNull {
                it.name.endsWith(".apk", ignoreCase = true)
            }
            if (apk == null) {
                Log.d(TAG, "check: release $latest has no apk asset")
                return CheckResult.Failed
            }

            CheckResult.Available(
                UpdateInfo(
                    version = latest,
                    notes = release.body.orEmpty(),
                    sizeBytes = apk.size,
                    downloadUrl = apk.browser_download_url,
                    releasePageUrl = release.html_url
                )
            )
        } catch (e: Exception) {
            // Offline, DNS, timeout, malformed payload — all indistinguishable
            // to the user on purpose: the answer is "nothing to see".
            Log.d(TAG, "check: failed (${e.javaClass.simpleName})")
            CheckResult.Failed
        } finally {
            client.close()
        }
    }

    /**
     * Numeric segment-wise compare, so 1.10 > 1.9 (lexicographic would say
     * otherwise). Non-numeric segments count as 0; a missing segment is 0.
     */
    fun isNewer(latest: String, current: String): Boolean {
        val l = latest.removePrefix("v").substringBefore('-').split('.')
        val c = current.removePrefix("v").substringBefore('-').split('.')
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrNull(i)?.toIntOrNull() ?: 0
            val cv = c.getOrNull(i)?.toIntOrNull() ?: 0
            if (lv != cv) return lv > cv
        }
        return false // equal (or unparseable) — never offer
    }

    fun formatSize(bytes: Long): String =
        if (bytes <= 0) "" else String.format("%.0f MB", bytes / 1024.0 / 1024.0)

    private fun currentVersion(): String =
        com.noctra.app.BuildConfig.VERSION_NAME
}
