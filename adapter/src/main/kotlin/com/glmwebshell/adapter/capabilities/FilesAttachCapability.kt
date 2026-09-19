package com.glmwebshell.adapter.capabilities

import com.glmwebshell.adapter.core.Capability
import com.glmwebshell.adapter.core.CapabilityContext
import com.glmwebshell.adapter.core.HealthStatus
import com.glmwebshell.adapter.model.CapabilitySpec
import com.glmwebshell.core.common.AppResult
import javax.inject.Inject

/**
 * Per TZ §5.1 — `files.attach`: uses the standard
 * `WebChromeClient.onShowFileChooser`. The DOM is **never** touched — the
 * page's own file picker is the source of truth.
 *
 * This capability exists for parity with the [Capability] interface and to
 * produce a stable id in the Diagnostics screen; the actual wiring lives in
 * [com.glmwebshell.pageengine.w1.WebViewEngine.GlmWebChromeClient].
 */
class FilesAttachCapability @Inject constructor() : Capability {
    override val id: String = ID

    override suspend fun selfTest(ctx: CapabilityContext, spec: CapabilitySpec): AppResult<HealthStatus> {
        // The file chooser is part of the engine; it is always available
        // when the WebView is attached. There is no page-state dependency.
        return AppResult.ok(HealthStatus.OK)
    }

    companion object { const val ID = "files.attach" }
}
