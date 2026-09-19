package com.glmwebshell.adapter.remote

import com.glmwebshell.adapter.model.AdapterPackage
import com.glmwebshell.adapter.model.CapabilitySpec
import com.glmwebshell.adapter.model.StrategySpec
import com.glmwebshell.adapter.model.StrategyType
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the bundled adapter from the APK `assets/` (TZ §5.4 — always available
 * fallback). Also serves the canary copy if one is bundled.
 */
@Singleton
class BundledAdapterProvider @Inject constructor(
    private val json: Json,
) {
    /** Load the bundled adapter for the given channel. */
    fun load(channel: AdapterChannel, assets: android.content.res.AssetManager): AdapterPackage {
        val path = when (channel) {
            AdapterChannel.STABLE -> "adapter/bundled-adapter.json"
            AdapterChannel.CANARY -> "adapter/bundled-adapter-canary.json"
        }
        val raw = runCatching {
            assets.open(path).bufferedReader().use { it.readText() }
        }.getOrNull()
        if (raw == null) return FALLBACK
        return runCatching {
            json.decodeFromString(AdapterPackage.serializer(), raw)
        }.getOrElse { FALLBACK }
    }

    /**
     * The hand-coded fallback used when even the assets file is missing
     * (should never happen in practice, but the runtime must not crash).
     */
    val FALLBACK: AdapterPackage = AdapterPackage(
        manifest = com.glmwebshell.adapter.model.AdapterManifest(
            adapterVersion = 1,
            minAppVersion = "1.0.0",
            channel = "stable",
            generatedAt = "2026-09-19T00:00:00Z",
            targetHost = "chat.z.ai",
            capabilities = listOf(
                CapabilitySpec(
                    id = "nav",
                    adapterVersion = 1,
                    minAppVersion = "1.0.0",
                    strategies = listOf(
                        StrategySpec(
                            type = StrategyType.URL,
                            name = "nav.url-template",
                            urlTemplate = "https://chat.z.ai/c/\${id}",
                            rank = 0,
                        ),
                    ),
                    selfTest = com.glmwebshell.adapter.model.SelfTestSpec(
                        onPage = "any",
                        expect = "URL template present",
                    ),
                    killSwitch = false,
                ),
                CapabilitySpec(
                    id = "chat.observe",
                    adapterVersion = 1,
                    minAppVersion = "1.0.0",
                    strategies = listOf(
                        StrategySpec(
                            type = StrategyType.NETWORK,
                            name = "observe.network",
                            endpointPatterns = listOf("/api/chat", "/conversation"),
                            parseRule = com.glmwebshell.adapter.model.ParseRule(
                                path = "messages",
                                userPath = "role=='user'",
                                assistantPath = "role=='assistant'",
                                streamDelimiter = "data:",
                            ),
                            rank = 0,
                        ),
                        StrategySpec(
                            type = StrategyType.SEMANTIC_DOM,
                            name = "observe.role-log",
                            semanticRules = listOf(
                                com.glmwebshell.adapter.model.SemanticRule(
                                    role = "log",
                                    ariaLive = "polite",
                                    notEmpty = true,
                                ),
                            ),
                            rank = 1,
                        ),
                    ),
                    selfTest = com.glmwebshell.adapter.model.SelfTestSpec(
                        onPage = "chat",
                        expect = "role=log or /api/chat endpoint",
                    ),
                    killSwitch = false,
                ),
                CapabilitySpec(
                    id = "chat.input.fill",
                    adapterVersion = 1,
                    minAppVersion = "1.0.0",
                    strategies = listOf(
                        StrategySpec(
                            type = StrategyType.SEMANTIC_DOM,
                            name = "input.textbox",
                            semanticRules = listOf(
                                com.glmwebshell.adapter.model.SemanticRule(role = "textbox"),
                                com.glmwebshell.adapter.model.SemanticRule(contentEditable = true),
                                com.glmwebshell.adapter.model.SemanticRule(tag = "textarea"),
                            ),
                            rank = 0,
                        ),
                        StrategySpec(
                            type = StrategyType.CSS_SELECTOR,
                            name = "input.textarea-fallback",
                            selector = "textarea",
                            rank = 1,
                        ),
                        StrategySpec(
                            type = StrategyType.CLIPBOARD_HINT,
                            name = "input.clipboard-hint",
                            rank = 2,
                        ),
                    ),
                    selfTest = com.glmwebshell.adapter.model.SelfTestSpec(
                        onPage = "chat",
                        expect = "textbox / contenteditable / textarea present",
                        selector = "textarea, [contenteditable], [role=textbox]",
                    ),
                    killSwitch = false,
                ),
                CapabilitySpec(
                    id = "chat.send",
                    adapterVersion = 1,
                    minAppVersion = "1.0.0",
                    strategies = listOf(
                        StrategySpec(
                            type = StrategyType.SEMANTIC_DOM,
                            name = "send.button",
                            semanticRules = listOf(
                                com.glmwebshell.adapter.model.SemanticRule(
                                    tag = "button",
                                    textMatches = "send|submit",
                                ),
                            ),
                            rank = 0,
                        ),
                        StrategySpec(
                            type = StrategyType.CSS_SELECTOR,
                            name = "send.enter-key",
                            selector = "textarea",
                            rank = 1,
                        ),
                    ),
                    selfTest = com.glmwebshell.adapter.model.SelfTestSpec(
                        onPage = "chat",
                        expect = "send button reachable",
                        selector = "button[type=submit], button[aria-label*='Send' i]",
                    ),
                    killSwitch = false,
                ),
                CapabilitySpec(
                    id = "session.status",
                    adapterVersion = 1,
                    minAppVersion = "1.0.0",
                    strategies = listOf(
                        StrategySpec(
                            type = StrategyType.URL,
                            name = "session.url",
                            urlTemplate = "https://chat.z.ai/",
                            rank = 0,
                        ),
                        StrategySpec(
                            type = StrategyType.SEMANTIC_DOM,
                            name = "session.form-hint",
                            semanticRules = listOf(
                                com.glmwebshell.adapter.model.SemanticRule(tag = "form"),
                            ),
                            rank = 1,
                        ),
                    ),
                    selfTest = com.glmwebshell.adapter.model.SelfTestSpec(
                        onPage = "any",
                        expect = "URL not redirected to /login",
                    ),
                    killSwitch = false,
                ),
                CapabilitySpec(
                    id = "files.attach",
                    adapterVersion = 1,
                    minAppVersion = "1.0.0",
                    strategies = listOf(
                        StrategySpec(
                            type = StrategyType.SEMANTIC_DOM,
                            name = "files.chooser-hook",
                            semanticRules = listOf(
                                com.glmwebshell.adapter.model.SemanticRule(tag = "input"),
                            ),
                            rank = 0,
                        ),
                    ),
                    selfTest = com.glmwebshell.adapter.model.SelfTestSpec(
                        onPage = "chat",
                        expect = "WebChromeClient.onShowFileChooser wired",
                    ),
                    killSwitch = false,
                ),
            ),
        ),
        signature = "",   // bundled fallback is unsigned; the runtime trusts it implicitly
        signatureAlgorithm = "none",
        publicKeyId = "bundled",
    )
}

enum class AdapterChannel { STABLE, CANARY }
