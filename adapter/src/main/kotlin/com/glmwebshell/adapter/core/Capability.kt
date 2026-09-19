package com.glmwebshell.adapter.core

import com.glmwebshell.adapter.model.CapabilitySpec
import com.glmwebshell.adapter.model.StrategySpec
import com.glmwebshell.adapter.model.StrategyType
import com.glmwebshell.core.common.AppResult
import com.glmwebshell.core.common.Error
import com.glmwebshell.core.common.ErrorCode

/** Outcome of a single strategy attempt. */
sealed interface StrategyOutcome {
    data class Satisfied(val evidence: String? = null) : StrategyOutcome
    data object NotSatisfied : StrategyOutcome
    data class Failed(val error: Error) : StrategyOutcome
}

/**
 * Per TZ §2.2 + §5.1: each capability declares an ordered list of strategies.
 * Implementations are intentionally side-effect free at the strategy level —
 * side-effects (e.g. inserting text, clicking send) are issued by the
 * capability itself based on the strategy that satisfied.
 *
 * Strategies are looked up by [StrategyType]. The order is encoded as
 * [StrategySpec.rank] (lower rank = tried first).
 */
interface Strategy {
    val type: StrategyType
    suspend fun probe(ctx: CapabilityContext, spec: StrategySpec): StrategyOutcome
}

/**
 * The capability surface. Capabilities are tied to a stable [id] from the
 * spec — the UI shows them by id; strategies are an implementation detail.
 */
interface Capability {
    val id: String
    suspend fun selfTest(ctx: CapabilityContext, spec: CapabilitySpec): AppResult<HealthStatus>
    suspend fun observe(ctx: CapabilityContext, spec: CapabilitySpec) {}   // optional, e.g. chat.observe
}

/** What gets handed to a capability / strategy on each call. */
interface CapabilityContext {
    val pageUrl: String
    suspend fun evaluateJs(script: String, timeoutMs: Long = 4_000L): AppResult<String>
    suspend fun postBridge(message: com.glmwebshell.pageengine.BridgeMessage.Outgoing)
    val bridgeIncoming: kotlinx.coroutines.flow.SharedFlow<com.glmwebshell.pageengine.BridgeMessage.Incoming>
    val engineEvents: kotlinx.coroutines.flow.SharedFlow<com.glmwebshell.pageengine.PageEngineEvent>
}

enum class HealthStatus { OK, DEGRADED, FAILED }

/**
 * Order strategies in priority order (lower rank first). Per TZ §5.1 the
 * table maps capability → ordered list, which is what [StrategySpec.rank]
 * encodes.
 */
fun List<StrategySpec>.byPriority(): List<StrategySpec> = sortedBy { it.rank }

/** Map a non-App failure to AppResult for the runtime. */
fun StrategyOutcome.toAppResult(): AppResult<HealthStatus> = when (this) {
    is StrategyOutcome.Satisfied -> AppResult.ok(HealthStatus.OK)
    is StrategyOutcome.NotSatisfied -> AppResult.ok(HealthStatus.FAILED)
    is StrategyOutcome.Failed -> AppResult.err(ErrorCode.CapabilityFailed, error.message)
}
