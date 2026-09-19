package com.glmwebshell.pageengine

import kotlinx.serialization.Serializable

/**
 * Engine-level UI / lifecycle events surfaced to Compose via a SharedFlow.
 * They are intentionally coarse — capabilities observe bridge messages for
 * fine-grained signals.
 */
@Serializable
sealed interface PageEngineEvent {
    @Serializable data class LoadStarted(val url: String) : PageEngineEvent
    @Serializable data class LoadProgress(val percent: Int) : PageEngineEvent
    @Serializable data class LoadFinished(val url: String) : PageEngineEvent
    @Serializable data class LoadFailed(val url: String, val errorCode: Int, val description: String) : PageEngineEvent
    @Serializable data class ExternalNavigation(val url: String) : PageEngineEvent
    @Serializable data class SessionStateChanged(val signedIn: Boolean) : PageEngineEvent
    @Serializable data object PullToRefreshTriggered : PageEngineEvent
}
