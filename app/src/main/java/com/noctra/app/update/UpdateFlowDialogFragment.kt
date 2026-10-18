package com.noctra.app.update

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.noctra.app.databinding.DialogUpdateAvailableBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UpdateFlowDialogFragment — the update dialog as a real state machine.
 *
 * States:
 *   OFFER      "Noctra X.Y is available"        [Update] [Later]
 *   DOWNLOADING progress bar + percent          [Cancel]
 *   VERIFYING  checksum check (brief)           (no buttons)
 *   PERMISSION one-time Android setting expl.   [Continue] [Not now]
 *   ERROR      calm failure copy                [Retry] [Cancel]
 *
 * Why a DialogFragment and not a plain AlertDialog (Phase 1 shape): the flow
 * needs a lifecycle — a coroutine polling DownloadManager, and an
 * ActivityResult launcher to come back from the install-permission Settings
 * screen. Both belong to a component that survives navigation; a bare
 * dialog object has neither.
 *
 * Dismissing at any point is safe: the download lives in the system's
 * DownloadManager and the id/version are persisted, so re-opening the flow
 * re-attaches (progress continues, or a finished file goes straight to
 * verify).
 */
class UpdateFlowDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "UpdateFlowDialogFragment"
        private const val ARG_UPDATE = "update_info"

        fun launch(fragmentManager: FragmentManager, info: UpdateChecker.UpdateInfo) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            UpdateFlowDialogFragment().apply {
                arguments = Bundle().apply { putSerializable(ARG_UPDATE, info) }
            }.show(fragmentManager, TAG)
        }
    }

    private enum class State { OFFER, DOWNLOADING, VERIFYING, PERMISSION, ERROR }

    private var _binding: DialogUpdateAvailableBinding? = null
    private val binding get() = _binding!!

    private lateinit var info: UpdateChecker.UpdateInfo
    private var state = State.OFFER

    private var errorTitle = ""
    private var errorMessage = ""
    private var errorAction: (() -> Unit)? = null

    private var activeDownloadId = -1L
    private var downloadJob: Job? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Returning from the "install unknown apps" toggle for Noctra.
        if (UpdateInstaller.canInstall(requireContext())) {
            proceedToInstall()
        } else {
            showError(
                "Install permission is still off",
                "Noctra can't install the update yet.",
                action = { openPermissionSettings() }
            )
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            setLayout((resources.displayMetrics.widthPixels * 0.85).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogUpdateAvailableBinding.inflate(inflater, container, false)
        info = requireArguments().getSerializable(ARG_UPDATE) as UpdateChecker.UpdateInfo

        binding.btnPositive.setOnClickListener { onPrimary() }
        binding.btnNegative.setOnClickListener { onSecondary() }

        restoreOrOffer()
        render()
        return binding.root
    }

    // ─── State entry points ──────────────────────────────────────────────────

    /** Re-attach to a previous run's download, or start fresh from OFFER. */
    private fun restoreOrOffer() {
        val ctx = requireContext()
        val id = UpdateInstaller.activeDownloadId(ctx)
        val sameVersion = UpdateInstaller.activeVersion(ctx) == info.version

        if (!sameVersion && id != -1L) {
            UpdateInstaller.cancel(ctx, id) // stale download from an older offer
        }

        val file = UpdateInstaller.apkFile(ctx, info.version)
        if (file?.exists() == true) {
            // A finished download whose dialog was dismissed — skip straight to verify.
            activeDownloadId = id
            beginVerify()
            return
        }

        when {
            sameVersion && id != -1L -> {
                activeDownloadId = id
                when (val probe = UpdateInstaller.probe(ctx, id, info.sizeBytes)) {
                    is UpdateInstaller.ProbeResult.Running -> {
                        state = State.DOWNLOADING
                        beginPolling()
                    }
                    UpdateInstaller.ProbeResult.Success -> beginVerify()
                    is UpdateInstaller.ProbeResult.Failed -> {
                        val (title, message) = friendlyDownloadFailure(probe.reason)
                        showError(title, message, action = { startDownload() })
                    }
                    UpdateInstaller.ProbeResult.Missing -> {
                        UpdateInstaller.clearActive(ctx)
                        state = State.OFFER
                    }
                }
            }
            else -> state = State.OFFER
        }
    }

    private fun startDownload() {
        try {
            activeDownloadId = UpdateInstaller.startDownload(requireContext(), info)
            state = State.DOWNLOADING
            render()
            beginPolling()
        } catch (e: Exception) {
            Log.e(TAG, "enqueue failed for ${info.downloadUrl}", e)
            showError(
                "Download failed",
                "Check your connection and try again.",
                action = { startDownload() }
            )
        }
    }

    private fun beginPolling() {
        downloadJob?.cancel()
        downloadJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val probe = withContext(Dispatchers.IO) {
                    UpdateInstaller.probe(requireContext(), activeDownloadId, info.sizeBytes)
                }
                when (probe) {
                    is UpdateInstaller.ProbeResult.Running -> renderProgress(probe)
                    UpdateInstaller.ProbeResult.Success -> {
                        beginVerify()
                        return@launch
                    }
                    is UpdateInstaller.ProbeResult.Failed -> {
                        val (title, message) = friendlyDownloadFailure(probe.reason)
                        showError(title, message, action = { startDownload() })
                        return@launch
                    }
                    UpdateInstaller.ProbeResult.Missing -> {
                        UpdateInstaller.clearActive(requireContext())
                        state = State.OFFER
                        render()
                        return@launch
                    }
                }
                delay(500)
            }
        }
    }

    private fun beginVerify() {
        downloadJob?.cancel()
        state = State.VERIFYING
        render()
        viewLifecycleOwner.lifecycleScope.launch {
            when (UpdateInstaller.verify(requireContext(), info)) {
                UpdateInstaller.VerifyResult.MATCH,
                UpdateInstaller.VerifyResult.NOT_PROVIDED -> afterVerify()
                UpdateInstaller.VerifyResult.MISMATCH -> showError(
                    "That download came out damaged",
                    "We removed it. Please try again.",
                    action = { startDownload() }
                )
                UpdateInstaller.VerifyResult.MISSING_FILE -> {
                    UpdateInstaller.clearActive(requireContext())
                    showError(
                        "Download didn't finish",
                        "Check your connection and try again.",
                        action = { startDownload() }
                    )
                }
            }
        }
    }

    private fun afterVerify() {
        if (UpdateInstaller.canInstall(requireContext())) {
            proceedToInstall()
        } else {
            state = State.PERMISSION
            render()
        }
    }

    private fun openPermissionSettings() {
        try {
            // Through the launcher, NOT plain startActivity: the result
            // callback is what re-checks the permission and continues to the
            // installer when the user comes back from Settings.
            permissionLauncher.launch(
                UpdateInstaller.installPermissionIntent(requireContext())
            )
        } catch (e: Exception) {
            Log.e(TAG, "couldn't open install-permission settings", e)
            showError("Couldn't open settings", "Open Noctra's install permission manually in Android settings.")
        }
    }

    private fun proceedToInstall() {
        val ctx = requireContext()
        val file = UpdateInstaller.apkFile(ctx, info.version)
        if (file == null || !file.exists()) {
            UpdateInstaller.clearActive(ctx)
            showError("Download didn't finish", "Check your connection and try again.",
                action = { startDownload() })
            return
        }
        try {
            // Android's own confirmation dialog takes over from here; on a
            // successful upgrade the app restarts into the new version.
            UpdateInstaller.fireInstall(ctx, file)
            dismiss()
        } catch (e: Exception) {
            showError("Couldn't start the installer", "Please try again.")
        }
    }

    // ─── Button routing ──────────────────────────────────────────────────────

    private fun onPrimary() {
        when (state) {
            State.OFFER -> startDownload()
            State.PERMISSION -> openPermissionSettings()
            State.ERROR -> errorAction?.invoke()
            State.DOWNLOADING, State.VERIFYING -> Unit // buttons hidden in these states
        }
    }

    private fun onSecondary() {
        when (state) {
            State.OFFER, State.PERMISSION, State.ERROR -> dismiss()
            State.DOWNLOADING -> {
                downloadJob?.cancel()
                UpdateInstaller.cancel(requireContext(), activeDownloadId)
                state = State.OFFER
                render()
            }
            State.VERIFYING -> Unit // buttons hidden
        }
    }

    private fun showError(title: String, message: String, action: (() -> Unit)? = null) {
        errorTitle = title
        errorMessage = message
        errorAction = action
        state = State.ERROR
        render()
    }

    // ─── Rendering ───────────────────────────────────────────────────────────

    private fun render() {
        val b = binding
        b.progressDownload.isVisible = state == State.DOWNLOADING
        b.progressSpinner.isVisible = state == State.DOWNLOADING || state == State.VERIFYING
        b.dialogMeta.isVisible = false
        b.dialogMessage.isVisible = true
        b.dialogTitle.textSize = 20f
        b.btnPositive.isVisible = state != State.DOWNLOADING && state != State.VERIFYING
        b.btnNegative.isVisible = state != State.VERIFYING

        when (state) {
            State.OFFER -> {
                b.dialogTitle.text = "Noctra ${info.version} is available"
                b.dialogMessage.isVisible = false // no release-notes body
                val sizeLine = offerSizeLine()
                if (sizeLine != null) {
                    b.dialogMeta.text = sizeLine
                    b.dialogMeta.isVisible = true
                }
                b.btnPositive.text = "Update"
                b.btnNegative.text = "Later"
            }
            State.DOWNLOADING -> {
                b.dialogTitle.text = "Downloading Noctra ${info.version}…"
                b.dialogMessage.text = "Starting…"
                b.btnNegative.text = "Cancel"
            }
            State.VERIFYING -> {
                // Calm, minimal state: spinner, then the label at subtitle
                // size — no body copy, no buttons. The spinner itself is
                // shown in the common block for both busy states.
                b.dialogTitle.text = "Checking the download…"
                b.dialogTitle.textSize = 16f
                b.dialogMessage.isVisible = false
            }
            State.PERMISSION -> {
                b.dialogTitle.text = "One-time permission"
                b.dialogMessage.text = "To install updates, allow Noctra to install apps from " +
                    "this source. It's a one-time Android setting."
                b.btnPositive.text = "Continue"
                b.btnNegative.text = "Not now"
            }
            State.ERROR -> {
                b.dialogTitle.text = errorTitle
                b.dialogMessage.text = errorMessage
                b.btnPositive.text = "Retry"
                b.btnPositive.isVisible = errorAction != null
                b.btnNegative.text = "Cancel"
            }
        }
    }

    private fun renderProgress(running: UpdateInstaller.ProbeResult.Running) {
        val total = if (running.totalBytes > 0) running.totalBytes else info.sizeBytes
        val read = running.bytesRead.coerceAtMost(total)
        val percent = if (total > 0) ((read * 100) / total).toInt().coerceIn(0, 100) else 0
        binding.progressDownload.progress = percent
        binding.dialogMessage.text = if (total > 0) {
            "${UpdateChecker.formatSize(read)} of ${UpdateChecker.formatSize(total)} · $percent%"
        } else {
            UpdateChecker.formatSize(read)
        }
    }

    /** Size hint under the offer message — rendered at caption scale (dialog_meta). */
    private fun offerSizeLine(): String? {
        val size = UpdateChecker.formatSize(info.sizeBytes)
        return if (size.isEmpty()) null else "$size download · Wi-Fi recommended"
    }

    /** Maps DownloadManager failure reasons to plain language — never codes. */
    private fun friendlyDownloadFailure(reason: Int): Pair<String, String> = when (reason) {
        android.app.DownloadManager.ERROR_INSUFFICIENT_SPACE ->
            "Not enough storage space" to "Free up about ${UpdateChecker.formatSize(info.sizeBytes)} and try again."
        android.app.DownloadManager.ERROR_CANNOT_RESUME,
        android.app.DownloadManager.ERROR_HTTP_DATA_ERROR,
        android.app.DownloadManager.ERROR_UNHANDLED_HTTP_CODE,
        android.app.DownloadManager.ERROR_TOO_MANY_REDIRECTS ->
            "Download didn't finish" to "Check your connection and try again."
        else ->
            "Download failed" to "Something went wrong while downloading. Please try again."
    }

    override fun onDestroyView() {
        super.onDestroyView()
        downloadJob?.cancel()
        _binding = null
    }
}
