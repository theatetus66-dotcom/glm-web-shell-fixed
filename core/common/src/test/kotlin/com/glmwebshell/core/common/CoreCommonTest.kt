package com.glmwebshell.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppResultTest {

    @Test fun `ok carries value`() {
        val r = AppResult.ok(7)
        assertThat(r.getOrNull()).isEqualTo(7)
        assertThat(r.errorOrNull()).isNull()
    }

    @Test fun `err carries structured error`() {
        val r = AppResult.err(ErrorCode.BridgeTimeout, "no reply in 4s")
        assertThat(r.errorOrNull()?.code).isEqualTo(ErrorCode.BridgeTimeout)
        assertThat(r.getOrNull()).isNull()
    }

    @Test fun `map transforms ok without touching err`() {
        val ok: AppResult<Int> = AppResult.ok(2)
        val mapped = ok.map { it * 3 }
        assertThat(mapped.getOrNull()).isEqualTo(6)

        val err: AppResult<Int> = AppResult.err(ErrorCode.CapabilityFailed, "boom")
        assertThat(err.map { it * 3 }.errorOrNull()?.code).isEqualTo(ErrorCode.CapabilityFailed)
    }

    @Test fun `onError side-effect fires only on err`() {
        var calls = 0
        AppResult.ok(1).onError { calls++ }
        AppResult.err(ErrorCode.Unknown, "x").onError { calls++ }
        assertThat(calls).isEqualTo(1)
    }
}

class AllowedOriginsTest {

    @Test fun `internal chat host is allowed`() {
        assertThat(AllowedOrigins.isInternal("https://chat.z.ai/")).isTrue()
        assertThat(AllowedOrigins.isInternal("https://chat.z.ai/c/abc")).isTrue()
    }

    @Test fun `auth host and subdomains are allowed`() {
        assertThat(AllowedOrigins.isInternal("https://z.ai/")).isTrue()
        assertThat(AllowedOrigins.isInternal("https://accounts.z.ai/")).isTrue()
    }

    @Test fun `external hosts are not allowed`() {
        assertThat(AllowedOrigins.isInternal("https://example.com/")).isFalse()
        assertThat(AllowedOrigins.isInternal("file:///data/x.html")).isFalse()
    }

    @Test fun `bridge origin rules list chat origin`() {
        assertThat(AllowedOrigins.bridgeOriginRules).contains(AllowedOrigins.CHAT_ORIGIN)
        // Set<String> — verify both elements are present.
        assertThat(AllowedOrigins.bridgeOriginRules).hasSize(2)
    }
}
