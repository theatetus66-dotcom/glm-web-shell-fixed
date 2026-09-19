package com.glmwebshell.adapter.core

import com.glmwebshell.adapter.model.CapabilitySpec
import com.glmwebshell.adapter.model.StrategySpec
import com.glmwebshell.adapter.model.StrategyType
import com.glmwebshell.core.common.AppResult
import com.glmwebshell.core.common.Constants
import com.glmwebshell.core.common.ErrorCode
import com.glmwebshell.core.common.Logger
import com.glmwebshell.core.common.withAppTimeout
import javax.inject.Inject

/**
 * Runs a capability's strategies in priority order. Per TZ §5.3:
 *  - The first strategy that returns `Satisfied` wins.
 *  - The runtime records which strategy was active.
 *  - If the chosen strategy is the most stable one for that capability,
 *    `OK` is reported; if a less-stable fallback was needed, `DEGRADED`.
 *  - If no strategy satisfies, `FAILED` (capability hidden in UI).
 *
 * Three consecutive `FAILED` → capability auto-disabled, per TZ §5.3.
 */
class CapabilitySelfTestRunner @Inject constructor() {

    /**
     * Runs the strategies of [spec] against [ctx] and returns the resulting
     * health status plus the name of the strategy that satisfied.
     */
    suspend fun run(
        ctx: CapabilityContext,
        spec: CapabilitySpec,
    ): AppResult<Pair<HealthStatus, String?>> {
        if (spec.killSwitch) return AppResult.ok(HealthStatus.FAILED to "kill-switch")

        // Skip self-tests that are not meant for the current page (e.g. login-only).
        val onPage = spec.selfTest?.onPage
        if (!onPage.isNullOrBlank() && onPage != "any") {
            val url = ctx.pageUrl.lowercase()
            val isAuth = "/login" in url || "/signin" in url || "/auth" in url
            val isChat = "chat." in url || "/c/" in url
            val matches = when (onPage.lowercase()) {
                "chat" -> isChat && !isAuth
                "auth" -> isAuth
                else -> true
            }
            if (!matches) {
                Logger.d(TAG, "self-test ${spec.id} skipped (onPage=$onPage, url=${ctx.pageUrl})")
                return AppResult.ok(HealthStatus.OK to "skipped:onPage")
            }
        }

        // The most stable strategy type for this capability — used to decide
        // OK vs DEGRADED. The first strategy in priority order is the most
        // stable by convention (lower rank = tried first).
        val ordered = spec.strategies.sortedBy { it.rank }
        if (ordered.isEmpty()) return AppResult.err(
            ErrorCode.CapabilityFailed, "no strategies declared for ${spec.id}",
        )

        val mostStableType = ordered.first().type
        var activeStrategy: String? = null

        for (s in ordered) {
            val strategy = Strategies.byType(s.type)
            val r = withAppTimeout(Constants.SELFTEST_TIMEOUT_MS, "self-test:${spec.id}:${s.type}") {
                strategy.probe(ctx, s)
            }
            when (r) {
                is AppResult.Ok -> {
                    val outcome = r.value
                    if (outcome is StrategyOutcome.Satisfied) {
                        activeStrategy = s.name
                        val status = if (s.type == mostStableType) HealthStatus.OK else HealthStatus.DEGRADED
                        Logger.d(TAG, "self-test ${spec.id} → $status via ${s.name}")
                        return AppResult.ok(status to activeStrategy)
                    }
                }
                is AppResult.Err -> {
                    Logger.w(TAG, "self-test ${spec.id}:${s.type} error: ${r.error.message}")
                }
            }
        }
        return AppResult.ok(HealthStatus.FAILED to null)
    }

    companion object { private const val TAG = "SelfTestRunner" }
}

/**
 * Convenience: compute whether a failure streak exceeds the disable threshold
 * (TZ §5.3). The repository persists the streak and consults this helper.
 */
fun failStreakExceedsThreshold(streak: Int, threshold: Int = Constants.CAPABILITY_FAIL_THRESHOLD): Boolean =
    streak >= threshold
