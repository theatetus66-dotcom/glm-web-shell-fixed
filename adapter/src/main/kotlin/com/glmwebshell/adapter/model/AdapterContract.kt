package com.glmwebshell.adapter.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A single declarative rule for how a capability extracts / performs an
 * action on the page. Per TZ §5.4: only declarative JSON, never arbitrary
 * executable code with elevated permissions (security + store compliance).
 *
 * Strategy types ordered from most to least stable, per TZ §2.2:
 *
 *  - `url`           — URL-route navigation (most stable).
 *  - `network`        — observe XHR / fetch streams issued by the page.
 *  - `semantic-dom`   — query by ARIA roles, `contenteditable`, structural
 *                       heuristics.
 *  - `css-selector`   — last-resort fallback; never the only strategy.
 *  - `clipboard-hint` — app shows a toast and the user pastes manually
 *                       (last fallback for `chat.input.fill`).
 */
@Serializable
data class StrategySpec(
    val type: StrategyType,
    val name: String,
    val selector: String? = null,
    val endpointPatterns: List<String> = emptyList(),
    val parseRule: ParseRule? = null,
    val semanticRules: List<SemanticRule> = emptyList(),
    val urlTemplate: String? = null,
    val expectAttr: String? = null,
    val rank: Int = 0,
)

@Serializable
enum class StrategyType {
    URL,
    NETWORK,
    SEMANTIC_DOM,
    CSS_SELECTOR,
    CLIPBOARD_HINT,
}

@Serializable
data class ParseRule(
    /** JSONPath-like selector for the network payload. */
    val path: String,
    /** Where the user message lives in the payload (e.g. "messages[?(@.role=='user')].content"). */
    val userPath: String? = null,
    /** Where the assistant response lives. */
    val assistantPath: String? = null,
    /** Stream delimiter (e.g. "data: " for SSE). */
    val streamDelimiter: String? = null,
)

@Serializable
data class SemanticRule(
    val role: String? = null,
    val ariaLive: String? = null,
    val contentEditable: Boolean? = null,
    val tag: String? = null,
    val textMatches: String? = null,
    val notEmpty: Boolean = false,
)

@Serializable
data class SelfTestSpec(
    val onPage: String,                    // "chat" | "auth" | "any"
    val expect: String,                    // human description for the Diagnostics screen
    val selector: String? = null,
    val expectAttr: String? = null,
)

@Serializable
data class CapabilitySpec(
    val id: String,
    val adapterVersion: Int,
    val minAppVersion: String,
    val strategies: List<StrategySpec>,
    val selfTest: SelfTestSpec? = null,
    val killSwitch: Boolean = false,
    @SerialName("requires_origin") val requiresOrigin: String? = null,
)

@Serializable
data class AdapterManifest(
    val adapterVersion: Int,
    val minAppVersion: String,
    val channel: String,                   // "stable" | "canary"
    val generatedAt: String,               // ISO-8601
    val targetHost: String,
    val capabilities: List<CapabilitySpec>,
)

@Serializable
data class AdapterPackage(
    val manifest: AdapterManifest,
    /** Base64 Ed25519 signature of the canonical JSON of `manifest`. */
    val signature: String,
    val signatureAlgorithm: String = "Ed25519",
    val publicKeyId: String = "builtin",
)

@Serializable
data class JsonElementWrapper(val value: JsonElement)
