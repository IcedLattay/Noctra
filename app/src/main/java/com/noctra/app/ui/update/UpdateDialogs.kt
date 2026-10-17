package com.noctra.app.ui.update

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Gravity
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.noctra.app.R
import com.noctra.app.update.UpdateChecker

/**
 * UpdateDialogs — the single "Noctra X.Y is available" dialog, used by both
 * the throttled cold-start check (MainActivity) and the manual Settings row.
 *
 * Phase 1 behavior: "Update" opens the release page in the browser — the
 * same page the site's download button points at. Phase 2 replaces that one
 * click-handler with the in-app download + installer hand-off; the dialog
 * itself does not change.
 */
object UpdateDialogs {

    fun showAvailable(context: Context, info: UpdateChecker.UpdateInfo): AlertDialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_update_available, null)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.dialog_title).text =
            "Noctra ${info.version} is available"

        view.findViewById<TextView>(R.id.dialog_message).text =
            buildMessage(info)

        view.findViewById<Button>(R.id.btn_positive).setOnClickListener {
            dialog.dismiss()
            // Phase 1: browser fallback. Phase 2: DownloadManager + installer.
            openReleasePage(context, info.releasePageUrl)
        }
        view.findViewById<Button>(R.id.btn_negative).setOnClickListener {
            dialog.dismiss() // plain "Later" — re-offers after the next 24h window
        }

        dialog.show()

        // Same sizing as the app's other custom dialogs: 85% width, centered.
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setGravity(Gravity.CENTER)
        return dialog
    }

    /** Release notes excerpt + size line; calm fallbacks when GitHub has neither. */
    private fun buildMessage(info: UpdateChecker.UpdateInfo): String {
        val trimmedNotes = info.notes.trim()
        val excerpt = when {
            trimmedNotes.isEmpty() -> "A new version of Noctra is ready to download."
            trimmedNotes.length > 240 -> trimmedNotes.take(240).trimEnd() + "…"
            else -> trimmedNotes
        }
        val size = UpdateChecker.formatSize(info.sizeBytes)
        return if (size.isEmpty()) excerpt else "$excerpt\n\n$size download · Wi-Fi recommended"
    }

    private fun openReleasePage(context: Context, url: String) {
        if (url.isBlank()) return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
        }
    }
}
