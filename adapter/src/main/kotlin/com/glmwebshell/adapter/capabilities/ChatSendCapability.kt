package com.glmwebshell.adapter.capabilities

import com.glmwebshell.adapter.core.Capability
import com.glmwebshell.adapter.core.CapabilityContext
import com.glmwebshell.adapter.core.CapabilitySelfTestRunner
import com.glmwebshell.adapter.core.HealthStatus
import com.glmwebshell.adapter.core.Strategies
import com.glmwebshell.adapter.core.StrategyOutcome
import com.glmwebshell.adapter.model.CapabilitySpec
import com.glmwebshell.core.common.AppResult
import com.glmwebshell.core.common.ErrorCode
import com.glmwebshell.pageengine.BridgeMessage
import javax.inject.Inject

/**
 * Per TZ §5.1 — `chat.send`:
 *   1. dispatch Enter-key in the field
 *   2. click the send button (located by aria-label / role)
 *   3. disabled
 */
class ChatSendCapability @Inject constructor(
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

    /** Triggers send via the best available strategy. */
    suspend fun send(ctx: CapabilityContext, spec: CapabilitySpec): AppResult<Unit> {
        for (s in spec.strategies.sortedBy { it.rank }) {
            val probe = Strategies.byType(s.type)
            val r = probe.probe(ctx, s)
            if (r is StrategyOutcome.Satisfied) {
                val labels = s.semanticRules.mapNotNull { it.textMatches }.filter { it.isNotBlank() }
                val locator = resolveLocator(s.selector, r.evidence, labels)
                if (locator.isNullOrBlank()) continue
                ctx.postBridge(BridgeMessage.Outgoing.RequestSend(locator))
                return AppResult.ok(Unit)
            }
        }
        return AppResult.err(ErrorCode.CapabilityFailed, "no send strategy satisfied")
    }

    /**
     * Prefer an explicit CSS/ARIA selector. Avoid treating generic evidence like
     * "button" as a page-wide query (that would click the first button).
     * When textMatches is present, build a button[aria-label] / text heuristic.
     */
    private fun resolveLocator(selector: String?, evidence: String?, textMatches: List<String>?): String? {
        val sel = selector?.trim().orEmpty()
        if (sel.isNotEmpty() && looksLikeCss(sel)) return sel
        val ev = evidence?.trim().orEmpty()
        if (ev.isNotEmpty() && looksLikeCss(ev)) return ev
        val label = textMatches?.firstOrNull()?.trim().orEmpty()
        if (label.isNotEmpty()) {
            // Prefer aria-label contains; fall back to button type=submit.
            return """button[aria-label*="${label}" i], button[type=submit]"""
        }
        if (sel.isNotEmpty()) return sel
        return null
    }

    private fun looksLikeCss(s: String): Boolean =
        s.contains('[') || s.contains('#') || s.contains('.') || s.contains('>') ||
            s.startsWith("button") || s.startsWith("[") || s.contains("role=")

    companion object { const val ID = "chat.send" }
}
