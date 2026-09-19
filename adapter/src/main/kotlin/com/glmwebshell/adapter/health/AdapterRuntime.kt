package com.glmwebshell.adapter.health

import com.glmwebshell.adapter.core.Capability
import com.glmwebshell.adapter.core.CapabilityContext
import com.glmwebshell.adapter.health.CapabilityHealthMonitor
import com.glmwebshell.adapter.model.AdapterPackage
import com.glmwebshell.adapter.model.CapabilitySpec
import com.glmwebshell.adapter.remote.AdapterLoader
import com.glmwebshell.core.common.AppResult
import com.glmwebshell.core.common.ErrorCode
import com.glmwebshell.core.common.Logger
import com.glmwebshell.core.data.db.entity.AdapterStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The runtime that the host (:features:chat) drives:
 *  1. Hydrates the current adapter package (bundled / cached / freshly fetched).
 *  2. Builds the (Capability, CapabilitySpec) pairs for the active package.
 *  3. Hands them to [CapabilityHealthMonitor] for self-tests.
 *  4. Listens for the "mass FAILED" signal and triggers auto-rollback.
 */
@Singleton
class AdapterRuntime @Inject constructor(
    private val loader: AdapterLoader,
    private val monitor: CapabilityHealthMonitor,
    private val capabilities: @JvmSuppressWildcards Map<String, Capability>,
) {
    fun currentPackage(): AdapterPackage = loader.current()

    fun activeCapabilities(): List<Pair<Capability, CapabilitySpec>> {
        val pkg = loader.current()
        return pkg.manifest.capabilities.mapNotNull { spec ->
            val impl = capabilities[spec.id] ?: run {
                Logger.w(TAG, "no implementation registered for capability ${spec.id}; skipping")
                return@mapNotNull null
            }
            impl to spec
        }
    }

    suspend fun runSelfTests(ctx: CapabilityContext) {
        val pkg = loader.current()
        val pairs = activeCapabilities()
        monitor.runAll(ctx, pairs, pkg.manifest.adapterVersion)
        if (massFailureDetected()) {
            Logger.w(TAG, "mass-failure detected after self-tests; rolling back adapter")
            loader.rollback()
            monitor.runAll(ctx, activeCapabilities(), loader.current().manifest.adapterVersion)
        }
    }

    /** True when >50% of capabilities FAILED (TZ §5.4 auto-rollback trigger). */
    private fun massFailureDetected(): Boolean {
        val states = monitor.states.value
        if (states.isEmpty()) return false
        val failed = states.count { it.value.status == com.glmwebshell.adapter.core.HealthStatus.FAILED }
        return failed * 2 > states.size
    }

    companion object { private const val TAG = "AdapterRuntime" }
}
