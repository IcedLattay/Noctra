package com.noctra.app.utils

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ConnectivityMonitor — the app's single source of truth for live
 * connectivity.
 *
 * One NetworkObserver registered for the process lifetime (started from
 * NoctraApplication.onCreate); its push callbacks feed a hot StateFlow so
 * every ViewModel reacts to the SAME flip at the same moment — the screen
 * swaps to its offline placeholder the instant Wi-Fi drops, without waiting
 * for a request to fail first.
 *
 * This does NOT replace NetworkObserver.checkNow(): the flow answers
 * "did connectivity CHANGE?", checkNow() answers "is it online RIGHT NOW?"
 * for point-in-time gates (attempt load? allow write?).
 */
object ConnectivityMonitor {

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    /** Idempotent — safe to call from Application.onCreate on every start. */
    fun start(context: Context) {
        if (started) return
        started = true

        val observer = NetworkObserver(context.applicationContext)
        scope.launch {
            observer.isConnected.collect { online ->
                _isOnline.value = online
            }
        }
    }
}
