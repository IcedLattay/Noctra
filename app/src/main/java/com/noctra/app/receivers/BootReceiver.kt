package com.noctra.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.noctra.app.workers.WindDownNotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BootReceiver
 *
 * Reschedules the wind-down notification alarm when the phone is rebooted.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Only proceed if user is logged in
            if (com.noctra.app.utils.UserSession.getUserId(context) != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    WindDownNotificationScheduler.scheduleNext(context)
                }
            }
        }
    }
}
