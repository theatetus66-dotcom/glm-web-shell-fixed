package com.glmwebshell.adapter.capabilities

import com.glmwebshell.adapter.core.Capability
import com.glmwebshell.adapter.core.CapabilityContext
import com.glmwebshell.adapter.core.CapabilitySelfTestRunner
import com.glmwebshell.adapter.core.HealthStatus
import com.glmwebshell.adapter.model.CapabilitySpec
import com.glmwebshell.adapter.model.StrategySpec
import com.glmwebshell.adapter.model.StrategyType
import com.glmwebshell.core.common.AllowedOrigins
import com.glmwebshell.core.common.AppResult
import com.glmwebshell.core.common.ErrorCode
import com.glmwebshell.pageengine.BridgeMessage
import com.glmwebshell.pageengine.PageEngineEvent
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Per TZ §5.1 — `session.status`: decide whether the user is signed in.
 * Strategies:
 *   1. URL + cookie hint (e.g. `authToken` cookie on chat.z.ai)
 *   2. semantic page hints (presence of login form vs. chat surface)
 */
class SessionStatusCapability @Inject constructor(
    private val selfTestRunner: CapabilitySelfTestRunner,
) : Capability {
    override val id: String = ID

    override suspend fun selfTest(ctx: CapabilityContext, spec: CapabilitySpec): AppResult<HealthStatus> {
        val r = selfTestRunner.run(ctx, spec)
        return when (r) {
            is AppResult.Ok -> AppResult.ok(r.value.first)
            is AppResult.Err -> r
        }
    }

    /** True when the user is signed in. */
    suspend fun isSignedIn(ctx: CapabilityContext, spec: CapabilitySpec): AppResult<Boolean> {
        // Cookie hint: check whether the chat URL is reachable and we are not
        // redirected to a login page.
        return try {
            val ev = ctx.engineEvents.first { event ->
                event is PageEngineEvent.LoadFinished && AllowedOrigins.isInternal(event.url)
            }
            when (ev) {
                is PageEngineEvent.LoadFinished -> AppResult.ok(!ev.url.contains("/login") && !ev.url.contains("/signin"))
                else -> AppResult.err(ErrorCode.Auth, "session status unknown")
            }
        } catch (t: Throwable) {
            AppResult.err(ErrorCode.Auth, t.message ?: "session check failed", t)
        }
    }

    companion object { const val ID = "session.status" }
}
