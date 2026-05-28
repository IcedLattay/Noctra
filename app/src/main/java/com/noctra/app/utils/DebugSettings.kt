package com.noctra.app.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object DebugSettings {
    private val _forceRoutineWindow = MutableStateFlow(false)
    val forceRoutineWindow: StateFlow<Boolean> = _forceRoutineWindow

    private val _skipCompletionCheck = MutableStateFlow(false)
    val skipCompletionCheck: StateFlow<Boolean> = _skipCompletionCheck

    fun setForceRoutineWindow(value: Boolean) {
        _forceRoutineWindow.value = value
    }

    fun setSkipCompletionCheck(value: Boolean) {
        _skipCompletionCheck.value = value
    }
}