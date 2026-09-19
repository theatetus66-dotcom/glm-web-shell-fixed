package com.glmwebshell.adapter.capabilities

import com.glmwebshell.adapter.core.Capability
import com.glmwebshell.adapter.core.CapabilityContext
import com.glmwebshell.adapter.core.HealthStatus
import com.glmwebshell.adapter.model.CapabilitySpec
import com.glmwebshell.adapter.model.StrategyType
import com.glmwebshell.core.common.AppResult
import com.glmwebshell.core.common.ErrorCode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per TZ §5.1 — `nav.openChat(id)` / `nav.newChat`.
 * Strategy: URL route (most stable).
 *
 * Actual [PageEngine.loadUrl] is performed by the host (ChatViewModel) after
 * observing [navigationRequests].
 */
@Singleton
class NavCapability @Inject constructor() : Capability {
    override val id: String = ID

    private val _navigationRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val navigationRequests: SharedFlow<String> = _navigationRequests.asSharedFlow()

    override suspend fun selfTest(ctx: CapabilityContext, spec: CapabilitySpec): AppResult<HealthStatus> {
        val hasTemplate = spec.strategies.any { it.type == StrategyType.URL && it.urlTemplate != null }
        return AppResult.ok(if (hasTemplate) HealthStatus.OK else HealthStatus.FAILED)
    }

    suspend fun openChat(ctx: CapabilityContext, spec: CapabilitySpec, remoteId: String): AppResult<Unit> {
        val tmpl = spec.strategies.firstOrNull { it.type == StrategyType.URL }?.urlTemplate
            ?: return AppResult.err(ErrorCode.CapabilityFailed, "no URL template for nav.openChat")
        val url = tmpl.replace("\${id}", remoteId).replace("{id}", remoteId)
        _navigationRequests.tryEmit(url)
        return AppResult.ok(Unit)
    }

    suspend fun newChat(ctx: CapabilityContext, spec: CapabilitySpec): AppResult<Unit> {
        val tmpl = spec.strategies.firstOrNull { it.type == StrategyType.URL }?.urlTemplate
            ?: return AppResult.err(ErrorCode.CapabilityFailed, "no URL template for nav.newChat")
        val url = tmpl.replace("\${id}", "").replace("{id}", "").trimEnd('/')
        _navigationRequests.tryEmit(url.ifBlank { "https://chat.z.ai/" })
        return AppResult.ok(Unit)
    }

    companion object { const val ID = "nav" }
}
