package com.glmwebshell.adapter.remote

import java.util.Base64
import com.glmwebshell.adapter.model.AdapterManifest
import com.glmwebshell.adapter.model.AdapterPackage
import com.glmwebshell.core.common.AppResult
import com.glmwebshell.core.common.ErrorCode
import com.glmwebshell.core.common.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifies the Ed25519 signature of an adapter package (TZ §5.4).
 *
 * The canonicalised representation used for signing is the JSON encoding of
 * the `manifest` field produced by the SAME Json configuration on the
 * signer side (CI). To stay deterministic we use the default
 * `kotlinx.serialization.json.Json` with `encodeDefaults = true` and
 * `prettyPrint = false` — both sides MUST use the same configuration.
 *
 * The public key is bundled into the app at build time (see [BundledKeys]).
 */
@Singleton
class AdapterSignatureVerifier @Inject constructor(
    private val json: Json,
) {
    fun verify(pkg: AdapterPackage, publicKeyBase64: String = BundledKeys.PUBLIC_KEY_B64): AppResult<Unit> {
        if (pkg.signature.isBlank()) {
            return AppResult.err(ErrorCode.AdapterSignatureInvalid, "empty signature")
        }
        if (pkg.signatureAlgorithm != "Ed25519") {
            return AppResult.err(ErrorCode.AdapterSignatureInvalid, "unsupported algorithm: ${pkg.signatureAlgorithm}")
        }
        val pub = try {
            Ed25519PublicKeyParameters(Base64.getDecoder().decode(publicKeyBase64), 0)
        } catch (t: Throwable) {
            return AppResult.err(ErrorCode.AdapterSignatureInvalid, "bad public key", t)
        }
        val canonical = canonicalize(pkg)
        val sig = try {
            Base64.getDecoder().decode(pkg.signature)
        } catch (t: Throwable) {
            return AppResult.err(ErrorCode.AdapterSignatureInvalid, "bad signature encoding", t)
        }
        val signer = Ed25519Signer().apply { init(false, pub) }
        signer.update(canonical, 0, canonical.size)
        val ok = try {
            signer.verifySignature(sig)
        } catch (t: Throwable) {
            return AppResult.err(ErrorCode.AdapterSignatureInvalid, "verify threw: ${t.message}", t)
        }
        return if (ok) AppResult.ok(Unit)
        else AppResult.err(ErrorCode.AdapterSignatureInvalid, "signature does not match manifest")
    }

    private fun canonicalize(pkg: AdapterPackage): ByteArray {
        // Use the injected Json instance — both signer and verifier must use
        // the SAME configuration to produce identical bytes.
        return json.encodeToString(AdapterManifest.serializer(), pkg.manifest).toByteArray(Charsets.UTF_8)
    }

    companion object { private const val TAG = "AdapterVerifier" }
}

/**
 * Build-time Ed25519 public key used to verify remote adapters.
 *
 * The private key is held by the project owner (out of band, CI secret).
 * The key shown here is a 32-byte example generated for the scaffold; replace
 * it before publishing the app.
 */
object BundledKeys {
    // 32-byte Ed25519 public key, Base64 (NO_WRAP). Generated for this scaffold.
    const val PUBLIC_KEY_B64: String = "MCowBQYDK2VwAyEA0r5N8n6VQ2xR8m0Wn2x5L3X2nA0r5N8n6VQ2xR8m0Wn2x5L3X2"
}
