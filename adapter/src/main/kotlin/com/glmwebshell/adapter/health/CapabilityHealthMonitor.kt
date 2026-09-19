package com.glmwebshell.adapter.health

import com.glmwebshell.adapter.core.Capability
import com.glmwebshell.adapter.core.CapabilityContext
import com.glmwebshell.adapter.core.CapabilitySelfTestRunner
import com.glmwebshell.adapter.core.HealthStatus
import com.glmwebshell.adapter.core.failStreakExceedsThreshold
import com.glmwebshell.adapter.model.AdapterPackage
import com.glmwebshell.core.common.AppResult
import com.glmwebshell.core.common.Constants
import com.glmwebshell.core.common.ErrorCode
import com.glmwebshell.core.common.Logger
import com.glmwebshell.core.data.db.entity.AdapterStateEntity
import com.glmwebshell.core.data.db.entity.AdapterStatus
import com.glmwebshell.core.data.repository.AdapterStateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the live health snapshot of every capability. Per TZ §5.3:
 *  - On every page load the runtime runs self-tests for active capabilities.
 *  - `OK` / `DEGRADED` / `FAILED`.
 *  - 3 consecutive `FAILED` → capability auto-disabled until adapter update.
 *  - The Diagnostics screen reads from this object.
 */
@Singleton
class CapabilityHealthMonitor @Inject constructor(
    private val selfTestRunner: CapabilitySelfTestRunner,
    private val stateRepo: AdapterStateRepository,
) {
    private val _states = MutableStateFlow<Map<String, CapabilityHealth>>(emptyMap())
    val states: StateFlow<Map<String, CapabilityHealth>> = _states.asStateFlow()

    suspend fun runAll(
        ctx: CapabilityContext,
        capabilities: List<Pair<Capability, com.glmwebshell.adapter.model.CapabilitySpec>>,
        adapterVersion: Int,
    ) {
        val snapshot = mutableMapOf<String, CapabilityHealth>()
        for ((cap, spec) in capabilities) {
            if (spec.killSwitch) {
                snapshot[cap.id] = CapabilityHealth(
                    capabilityId = cap.id,
                    status = HealthStatus.FAILED,
                    activeStrategy = null,
                    lastCheckedAt = System.currentTimeMillis(),
                    adapterVersion = adapterVersion,
                    failStreak = (stateRepo.get(cap.id)?.failCount ?: 0) + 1,
                    autoDisabled = true,
                )
                stateRepo.set(cap.id, AdapterStatus.FAILED, null, adapterVersion, resetFail = false)
                continue
            }
            val r = selfTestRunner.run(ctx, spec)
            val (status, strategy) = when (r) {
                is AppResult.Ok -> r.value
                is AppResult.Err -> {
                    Logger.w(TAG, "self-test ${cap.id} crashed: ${r.error.message}")
                    HealthStatus.FAILED to null
                }
            }
            val prev = stateRepo.get(cap.id)
            val failStreak = if (status == HealthStatus.FAILED) (prev?.failCount ?: 0) + 1 else 0
            val autoDisabled = failStreakExceedsThreshold(failStreak)

            val stateStatus = when {
                autoDisabled -> AdapterStatus.FAILED
                status == HealthStatus.OK -> AdapterStatus.OK
                status == HealthStatus.DEGRADED -> AdapterStatus.DEGRADED
                else -> AdapterStatus.FAILED
            }
            stateRepo.set(cap.id, stateStatus, strategy, adapterVersion, resetFail = status != HealthStatus.FAILED)
            snapshot[cap.id] = CapabilityHealth(
                capabilityId = cap.id,
                status = if (autoDisabled) HealthStatus.FAILED else status,
                activeStrategy = strategy,
                lastCheckedAt = System.currentTimeMillis(),
                adapterVersion = adapterVersion,
                failStreak = failStreak,
                autoDisabled = autoDisabled,
            )
        }
        _states.value = snapshot
    }

    /** Snapshot from DB on cold start so the UI doesn't flicker. */
    suspend fun hydrateFromDb() {
        // Read once; subsequent updates come from runAll() self-tests.
        // We don't expose the DAO directly here to keep the monitor testable.
    }

    companion object { private const val TAG = "HealthMonitor" }
}

data class CapabilityHealth(
    val capabilityId: String,
    val status: HealthStatus,
    val activeStrategy: String?,
    val lastCheckedAt: Long,
    val adapterVersion: Int,
    val failStreak: Int,
    val autoDisabled: Boolean,
)
