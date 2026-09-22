package com.fcplus.android

import kotlinx.coroutines.flow.MutableStateFlow

data class FcStatus(
    val running: Boolean = false,
    val dryRun: Boolean = true,
    val strategy: String = "Quick Flip",
    val engineStatus: String = "Stopped",
    val pageTitle: String = "",
    val pageUrl: String = "",
    val lastEvent: String = "Ready",
    val securityStop: Boolean = false
)

object AppState {
    val status = MutableStateFlow(FcStatus())

    fun update(block: (FcStatus) -> FcStatus) {
        status.value = block(status.value)
    }
}
