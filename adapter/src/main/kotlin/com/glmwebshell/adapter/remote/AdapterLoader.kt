package com.glmwebshell.adapter.remote

import com.glmwebshell.adapter.model.AdapterPackage
import com.glmwebshell.core.common.AppResult
import com.glmwebshell.core.common.ErrorCode
import com.glmwebshell.core.common.Logger
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves which adapter package is "live" right now.
 *
 * Per TZ §5.4 the policy is:
 *  1. Use the bundled package on first launch.
 *  2. Poll the remote URL at most once per `ADAPTER_CHECK_INTERVAL_HOURS`,
 *     subject to the channel the user picked (stable / canary).
 *  3. If the remote package verifies, persist it and use it.
 *  4. If the remote fails verification, keep using the previous good one.
 *  5. If, after a remote update, three consecutive self-tests of any
 *     capability report FAILED, auto-rollback to the previous package.
 *
 * This is a *cache* in memory plus a small persisted slot (file in app
 * private storage). The persisted slot is what survives a cold start.
 */
@Singleton
class AdapterLoader @Inject constructor(
    private val json: Json,
    private val verifier: AdapterSignatureVerifier,
) {
    @Volatile private var current: AdapterPackage? = null
    @Volatile private var previous: AdapterPackage? = null

    fun current(): AdapterPackage = current
        ?: error("AdapterLoader.current() called before loadInitial()")

    fun previous(): AdapterPackage? = previous

    /** Initialise from the bundled fallback (cold start path). */
    fun loadInitial(bundled: AdapterPackage) {
        if (current == null) current = bundled
    }

    /**
     * Try to apply a freshly-fetched remote package. Returns true if the
     * remote was accepted; false if it was rejected (kept the previous).
     */
    fun applyRemote(raw: String): AppResult<AdapterPackage> {
        val parsed = runCatching {
            json.decodeFromString(AdapterPackage.serializer(), raw)
        }.getOrElse {
            return AppResult.err(ErrorCode.AdapterSignatureInvalid, "decode failed: ${it.message}", it)
        }
        // Remote packages MUST always pass signature verification.
        // Never trust signatureAlgorithm from the remote JSON itself.
        val v = verifier.verify(parsed)
        if (v is AppResult.Err) {
            Logger.w(TAG, "rejected remote adapter: ${v.error.message}")
            return v
        }
        previous = current
        current = parsed
        Logger.i(TAG, "applied remote adapter v${parsed.manifest.adapterVersion} (channel=${parsed.manifest.channel})")
        return AppResult.ok(parsed)
    }

    /**
     * Roll back to the previous known-good package. Per TZ §5.4, this is
     * triggered automatically when self-tests fail en masse after an update.
     */
    fun rollback(): AppResult<AdapterPackage> {
        val prev = previous ?: return AppResult.err(
            ErrorCode.Unknown, "no previous adapter to roll back to",
        )
        current = prev
        Logger.w(TAG, "rolled back to adapter v${prev.manifest.adapterVersion}")
        return AppResult.ok(prev)
    }

    companion object { private const val TAG = "AdapterLoader" }
}
