package com.glmwebshell.core.common

/**
 * The application-wide result envelope. Distinct from kotlin.Result in that
 * failures carry structured [Error] metadata instead of a Throwable.
 */
sealed interface AppResult<out T> {
    data class Ok<T>(val value: T) : AppResult<T>
    data class Err(val error: Error) : AppResult<Nothing>

    fun getOrNull(): T? = (this as? Ok)?.value
    fun errorOrNull(): Error? = (this as? Err)?.error

    companion object {
        fun <T> ok(v: T): AppResult<T> = Ok(v)
        fun err(code: ErrorCode, message: String, cause: Throwable? = null): AppResult<Nothing> =
            Err(Error(code, message, cause?.let { stackTraceToString(it) }))
    }
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Ok -> AppResult.Ok(transform(value))
    is AppResult.Err -> this
}

inline fun <T> AppResult<T>.onError(block: (Error) -> Unit): AppResult<T> {
    if (this is AppResult.Err) block(error)
    return this
}

enum class ErrorCode {
    NetworkUnavailable,
    OriginNotAllowed,
    BridgeTimeout,
    SchemaViolation,
    CapabilityFailed,
    CapabilityDegraded,
    CapabilityDisabled,
    AdapterSignatureInvalid,
    AdapterVersionTooNew,
    AdapterVersionTooOld,
    Storage,
    Proxy,
    Auth,
    Cancelled,
    Unknown
}

data class Error(
    val code: ErrorCode,
    val message: String,
    val cause: String? = null
) {
    override fun toString() = buildString {
        append("Error(code="); append(code)
        append(", message="); append(message)
        if (!cause.isNullOrBlank()) { append(", cause="); append(cause) }
        append(")")
    }
}

private fun stackTraceToString(t: Throwable): String =
    t.stackTraceToString().lineSequence().take(8).joinToString("\n")
