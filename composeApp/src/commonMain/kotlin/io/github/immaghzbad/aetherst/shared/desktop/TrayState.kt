package io.github.immaghzbad.aetherst.shared.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object TrayState {
    private val _navigateToSettings = MutableStateFlow(0L)
    val navigateToSettings: StateFlow<Long> = _navigateToSettings

    private val _adminDialogRequest = MutableStateFlow(0L)
    val adminDialogRequest: StateFlow<Long> = _adminDialogRequest

    fun requestSettings() {
        _navigateToSettings.value = System.currentTimeMillis()
    }

    fun requestAdminDialog() {
        _adminDialogRequest.value = System.currentTimeMillis()
    }
}
