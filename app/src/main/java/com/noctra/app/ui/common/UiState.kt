package com.noctra.app.ui.common

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    object Offline : UiState<Nothing>()
}
