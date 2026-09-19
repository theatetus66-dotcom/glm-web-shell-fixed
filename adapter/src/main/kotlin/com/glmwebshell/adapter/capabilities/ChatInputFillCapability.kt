package com.glmwebshell.adapter.capabilities

import com.glmwebshell.adapter.core.Capability
import com.glmwebshell.adapter.core.CapabilityContext
import com.glmwebshell.adapter.core.CapabilitySelfTestRunner
import com.glmwebshell.adapter.core.HealthStatus
import com.glmwebshell.adapter.model.CapabilitySpec
import com.glmwebshell.adapter.model.StrategyType
import com.glmwebshell.core.common.AppResult
import com.glmwebshell.core.common.ErrorCode
import com.glmwebshell.pageengine.BridgeMessage
import javax.inject.Inject

/**
 * Per TZ §5.1 — `chat.input.fill(text)`:
 *   1. semantic search for the input field (role=textbox, contenteditable, textarea)
 *   2. fallback selector
 *   3. clipboard + toast hint ("please paste")
 *
 * Per TZ §5.5 (transparency): the text is always visible to the user
 * before it is sent (the host shows a confirmation dialog with the prefilled
 * text). This capability only fills the field; the user still has to tap
 * "Send" themselves.
 */
class ChatInputFillCapability @Inject constructor(
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

    /**
     * Fills the input. Returns the strategy used so the host can show a
     * transparency hint.
     */
    suspend fun fill(ctx: CapabilityContext, spec: CapabilitySpec, text: String): AppResult<String> {
        // Find the most-preferred strategy that satisfies.
        for (s in spec.strategies.sortedBy { it.rank }) {
            val probe = com.glmwebshell.adapter.core.Strategies.byType(s.type)
            val r = probe.probe(ctx, s)
            if (r is com.glmwebshell.adapter.core.StrategyOutcome.Satisfied) {
                val locator = s.selector ?: r.evidence ?: s.name
                ctx.postBridge(BridgeMessage.Outgoing.RequestFill(locator, text))
                return AppResult.ok(s.name)
            }
        }
        // Last-resort: clipboard hint (host shows the toast).
        return AppResult.err(
            ErrorCode.CapabilityDegraded,
            "clipboard-hint",
        )
    }

    companion object { const val ID = "chat.input.fill" }
}
