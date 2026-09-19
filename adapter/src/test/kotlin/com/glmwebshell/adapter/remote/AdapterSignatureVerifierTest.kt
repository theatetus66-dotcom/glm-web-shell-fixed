package com.glmwebshell.adapter.remote

import com.glmwebshell.adapter.model.AdapterPackage
import com.glmwebshell.adapter.model.AdapterManifest
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import com.glmwebshell.core.common.AppResult

/**
 * Per TZ §12 acceptance: "Unsupported / unsigned package is rejected".
 */
class AdapterSignatureVerifierTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val verifier = AdapterSignatureVerifier(json)

    private val pkg = AdapterPackage(
        manifest = AdapterManifest(
            adapterVersion = 2,
            minAppVersion = "1.0.0",
            channel = "stable",
            generatedAt = "2026-09-19T00:00:00Z",
            targetHost = "chat.z.ai",
            capabilities = emptyList(),
        ),
        signature = "AAA",                // clearly invalid
        signatureAlgorithm = "Ed25519",
    )

    @Test fun `empty signature is rejected`() {
        val r = verifier.verify(pkg.copy(signature = ""))
        assertThat(r).isInstanceOf(AppResult.Err::class.java)
        assertThat((r as AppResult.Err).error.code)
            .isEqualTo(com.glmwebshell.core.common.ErrorCode.AdapterSignatureInvalid)
    }

    @Test fun `garbage signature is rejected`() {
        val r = verifier.verify(pkg)
        assertThat(r).isInstanceOf(AppResult.Err::class.java)
    }

    @Test fun `unsupported algorithm is rejected`() {
        val r = verifier.verify(pkg.copy(signatureAlgorithm = "RSA-4096"))
        assertThat(r).isInstanceOf(AppResult.Err::class.java)
    }
}
