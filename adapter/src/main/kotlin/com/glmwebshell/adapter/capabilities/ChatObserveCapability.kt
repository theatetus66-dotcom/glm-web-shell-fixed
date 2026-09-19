package com.glmwebshell.adapter.capabilities

import com.glmwebshell.adapter.core.Capability
import com.glmwebshell.adapter.core.CapabilityContext
import com.glmwebshell.adapter.core.CapabilitySelfTestRunner
import com.glmwebshell.adapter.core.HealthStatus
import com.glmwebshell.adapter.model.CapabilitySpec
import com.glmwebshell.adapter.model.StrategyType
import com.glmwebshell.core.common.AppResult
import com.glmwebshell.core.common.ErrorCode
import com.glmwebshell.core.data.db.entity.CaptureSource
import com.glmwebshell.core.data.db.entity.MessageEntity
import com.glmwebshell.core.data.db.entity.MessageRole
import com.glmwebshell.core.data.repository.MessageRepository
import com.glmwebshell.pageengine.BridgeMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

/**
 * Per TZ §5.1 — `chat.observe`: discover chat turns for the local history.
 * Strategies in priority order:
 *   1. network stream observation
 *   2. semantic-dom MutationObserver (role=log, aria-live)
 *   3. disabled
 *
 * When `FAILED`, features History / Search / Export are hidden (TZ §6.3).
 */
class ChatObserveCapability @Inject constructor(
    private val messages: MessageRepository,
    private val selfTestRunner: CapabilitySelfTestRunner,
) : Capability {
    override val id: String = ID

    private val _turns = MutableSharedFlow<Turn>(extraBufferCapacity = 64)
    val turns: SharedFlow<Turn> = _turns.asSharedFlow()

    override suspend fun selfTest(ctx: CapabilityContext, spec: CapabilitySpec): AppResult<HealthStatus> {
        val r = selfTestRunner.run(ctx, spec)
        return when (r) {
            is AppResult.Ok -> AppResult.ok(r.value.first)
            is AppResult.Err -> r
        }
    }

    /** Collect bridge messages and persist them to the local history. */
    suspend fun observe(ctx: CapabilityContext, chatId: Long, spec: CapabilitySpec) {
        ctx.bridgeIncoming.collect { msg ->
            when (msg) {
                is BridgeMessage.Incoming.NetworkStreamChunk -> {
                    // The parse rule decides whether this chunk is a user or assistant turn.
                    // We attempt to parse JSON-lines; if it fails we skip (degraded mode).
                    val parsed = parseChunk(msg.chunk, spec)
                    if (parsed != null) {
                        val (role, content) = parsed
                        messages.upsert(chatId, role, content, CaptureSource.NETWORK)
                        _turns.tryEmit(Turn(role, content, CaptureSource.NETWORK))
                    }
                }
                is BridgeMessage.Incoming.ChatTurnCaptured -> {
                    val role = when (msg.role.lowercase()) {
                        "assistant" -> MessageRole.ASSISTANT
                        "system" -> MessageRole.SYSTEM
                        "tool" -> MessageRole.TOOL
                        else -> MessageRole.USER
                    }
                    val source = when (msg.capturedBy.lowercase()) {
                        "dom" -> CaptureSource.DOM
                        else -> CaptureSource.NETWORK
                    }
                    messages.upsert(chatId, role, msg.content, source)
                    _turns.tryEmit(Turn(role, msg.content, source))
                }
                else -> Unit
            }
        }
    }

    private fun parseChunk(chunk: String, spec: CapabilitySpec): Pair<MessageRole, String>? {
        val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        for (line in chunk.lineSequence()) {
            val trimmed = line.removePrefix("data:").trim()
            if (trimmed.isEmpty() || trimmed == "[DONE]") continue
            val element = runCatching { jsonParser.parseToJsonElement(trimmed) }.getOrNull() ?: continue
            val obj = element as? kotlinx.serialization.json.JsonObject ?: continue

            // Prefer structured role/content fields (OpenAI-compatible / many chat APIs).
            val roleField = (obj["role"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                ?: extractNestedString(obj, listOf("message", "role"))
                ?: extractNestedString(obj, listOf("delta", "role"))
            val contentField = (obj["content"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                ?: extractNestedString(obj, listOf("message", "content"))
                ?: extractNestedString(obj, listOf("delta", "content"))
                ?: extractNestedString(obj, listOf("choices", "0", "delta", "content"))
                ?: extractNestedString(obj, listOf("choices", "0", "message", "content"))

            if (!contentField.isNullOrBlank()) {
                val role = when (roleField?.lowercase()) {
                    "assistant", "model", "bot" -> MessageRole.ASSISTANT
                    "system" -> MessageRole.SYSTEM
                    "tool", "function" -> MessageRole.TOOL
                    "user", "human" -> MessageRole.USER
                    else -> {
                        // Heuristic from parseRule path strings when role is missing.
                        val raw = element.toString()
                        val assistantPath = spec.strategies.firstOrNull { it.parseRule != null }?.parseRule?.assistantPath
                        val userPath = spec.strategies.firstOrNull { it.parseRule != null }?.parseRule?.userPath
                        when {
                            assistantPath != null && raw.contains("assistant", ignoreCase = true) -> MessageRole.ASSISTANT
                            userPath != null && raw.contains("user", ignoreCase = true) -> MessageRole.USER
                            else -> MessageRole.ASSISTANT
                        }
                    }
                }
                return role to contentField.take(8192)
            }
        }
        return null
    }

    private fun extractNestedString(root: kotlinx.serialization.json.JsonObject, path: List<String>): String? {
        var cur: kotlinx.serialization.json.JsonElement = root
        for (key in path) {
            cur = when (cur) {
                is kotlinx.serialization.json.JsonObject -> cur[key] ?: return null
                is kotlinx.serialization.json.JsonArray -> {
                    val idx = key.toIntOrNull() ?: return null
                    cur.getOrNull(idx) ?: return null
                }
                else -> return null
            }
        }
        return (cur as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
    }

    data class Turndata class Turn(val role: MessageRole, val content: String, val capturedBy: CaptureSource)

    companion object { const val ID = "chat.observe" }
}
