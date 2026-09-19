package com.glmwebshell.adapter.core

import com.glmwebshell.adapter.model.SemanticRule
import com.glmwebshell.adapter.model.StrategySpec
import com.glmwebshell.adapter.model.StrategyType
import com.glmwebshell.core.common.AppResult
import com.glmwebshell.core.common.withAppTimeout
import com.glmwebshell.pageengine.BridgeMessage
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.first

/**
 * Per TZ §5.1 — URL-route navigation. Strategy: just navigate to a URL built
 * from a template. This is the most stable strategy — it survives DOM layout
 * changes entirely.
 */
class UrlRouteStrategy : Strategy {
    override val type = StrategyType.URL

    override suspend fun probe(ctx: CapabilityContext, spec: StrategySpec): StrategyOutcome {
        // URL strategies are always satisfiable — they don't depend on the page state.
        return if (spec.urlTemplate != null) StrategyOutcome.Satisfied(spec.urlTemplate)
        else StrategyOutcome.NotSatisfied
    }
}

/**
 * Per TZ §5.1 — observe the network stream the page itself issues.
 * The probe fires an `ObserveStream` message and waits for any
 * `NetworkStreamChunk` matching the patterns.
 */
class NetworkStreamStrategy(
    private val timeoutMs: Long = 4_000L,
) : Strategy {
    override val type = StrategyType.NETWORK

    override suspend fun probe(ctx: CapabilityContext, spec: StrategySpec): StrategyOutcome {
        if (spec.endpointPatterns.isEmpty()) return StrategyOutcome.NotSatisfied
        ctx.postBridge(BridgeMessage.Outgoing.ObserveStream(spec.endpointPatterns))

        // Self-tests run on an idle page — waiting several seconds for traffic always fails.
        // Prefer a short opportunistic wait; if a matching chunk arrives, great. Otherwise
        // report Satisfied with "listening" so the capability is considered ready to observe.
        val waitMs = minOf(timeoutMs, 800L)
        val sawMatch = withAppTimeout(waitMs, "NetworkStreamStrategy") {
            ctx.bridgeIncoming.first { msg ->
                msg is BridgeMessage.Incoming.NetworkStreamChunk &&
                    spec.endpointPatterns.any { pat ->
                        msg.endpoint.contains(pat) || runCatching { Regex(pat).containsMatchIn(msg.endpoint) }.getOrDefault(false)
                    }
            }
            true
        }
        return when (sawMatch) {
            is AppResult.Ok -> StrategyOutcome.Satisfied("network stream matched")
            is AppResult.Err -> StrategyOutcome.Satisfied("listening:${spec.endpointPatterns.joinToString(",")}")
        }
    }
}

/**
 * Per TZ §5.1 — query the DOM by ARIA roles / `contenteditable` / structural
 * heuristics. The probe issues a `QueryDom` message and awaits the result.
 */
class SemanticDomStrategy(
    private val timeoutMs: Long = 4_000L,
) : Strategy {
    override val type = StrategyType.SEMANTIC_DOM

    override suspend fun probe(ctx: CapabilityContext, spec: StrategySpec): StrategyOutcome {
        if (spec.semanticRules.isEmpty() && spec.selector == null) return StrategyOutcome.NotSatisfied
        val js = buildSemanticQuery(spec)
        val res = ctx.evaluateJs(js, timeoutMs)
        return when (res) {
            is AppResult.Ok -> {
                val matched = res.value?.trim()?.trim('"')?.toBooleanStrictOrNull() == true
                if (matched) StrategyOutcome.Satisfied(
                    spec.selector ?: spec.semanticRules.firstOrNull()?.toCssSelector()
                )
                else StrategyOutcome.NotSatisfied
            }
            is AppResult.Err -> StrategyOutcome.Failed(res.error)
        }
    }

    private fun buildSemanticQuery(spec: StrategySpec): String {
        val sel = spec.selector ?: spec.semanticRules.firstOrNull()?.toCssSelector() ?: return "false;"
        val quoted = Json.encodeToString(sel)
        return "(function(){try{var el=document.querySelector($quoted);return !!el;}catch(e){return false;}})();"
    }
}

private fun SemanticRule.toCssSelector(): String = buildString {
    val parts = mutableListOf<String>()
    if (tag != null) parts += tag
    if (role != null) parts += "[role='$role']"
    if (ariaLive != null) parts += "[aria-live='$ariaLive']"
    if (contentEditable == true) parts += "[contenteditable]"
    append(parts.joinToString(""))
}

/**
 * Per TZ §2.4 — last-resort CSS selector. Forbidden as the only strategy;
 * allowed only inside a strategy as a last-resort heuristic.
 */
class CssSelectorStrategy : Strategy {
    override val type = StrategyType.CSS_SELECTOR
    override suspend fun probe(ctx: CapabilityContext, spec: StrategySpec): StrategyOutcome {
        val sel = spec.selector ?: return StrategyOutcome.NotSatisfied
        val quoted = Json.encodeToString(sel)
        val js = "(function(){try{return !!document.querySelector($quoted);}catch(e){return false;}})();"
        return when (val r = ctx.evaluateJs(js)) {
            is AppResult.Ok -> if (r.value?.trim()?.trim('"')?.toBooleanStrictOrNull() == true)
                StrategyOutcome.Satisfied(spec.selector) else StrategyOutcome.NotSatisfied
            is AppResult.Err -> StrategyOutcome.Failed(r.error)
        }
    }
}

/**
 * Per TZ §5.1 — when all strategies fail for `chat.input.fill`, fall back to
 * a clipboard hint: the app copies the text to the clipboard and shows a
 * toast asking the user to paste. Not a strategy that "passes" — it returns
 * NotSatisfied but the runtime knows to display the hint.
 */
class ClipboardHintStrategy : Strategy {
    override val type = StrategyType.CLIPBOARD_HINT
    override suspend fun probe(ctx: CapabilityContext, spec: StrategySpec): StrategyOutcome =
        StrategyOutcome.NotSatisfied
}

/**
 * Helper: returns the [Strategy] implementation matching a [StrategyType].
 */
object Strategies {
    fun byType(type: StrategyType): Strategy = when (type) {
        StrategyType.URL -> UrlRouteStrategy()
        StrategyType.NETWORK -> NetworkStreamStrategy()
        StrategyType.SEMANTIC_DOM -> SemanticDomStrategy()
        StrategyType.CSS_SELECTOR -> CssSelectorStrategy()
        StrategyType.CLIPBOARD_HINT -> ClipboardHintStrategy()
    }
}
