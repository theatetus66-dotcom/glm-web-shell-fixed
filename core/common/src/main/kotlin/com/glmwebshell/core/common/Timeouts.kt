package com.glmwebshell.core.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Run a block with a hard timeout. Returns [AppResult.Err] of [ErrorCode.BridgeTimeout] on expiry. */
suspend fun <T> withAppTimeout(
    timeoutMs: Long,
    tag: String = "withAppTimeout",
    block: suspend () -> T,
): AppResult<T> = try {
    AppResult.ok(withTimeout(timeoutMs) { block() })
} catch (t: TimeoutCancellationException) {
    AppResult.err(ErrorCode.BridgeTimeout, "$tag: timed out after $timeoutMs ms")
} catch (t: CancellationException) {
    throw t
} catch (t: Throwable) {
    AppResult.err(ErrorCode.Unknown, "$tag: ${t.message ?: t.javaClass.simpleName}", t)
}
