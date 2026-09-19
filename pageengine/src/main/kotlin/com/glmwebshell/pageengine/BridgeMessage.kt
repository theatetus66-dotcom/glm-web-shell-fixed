package com.glmwebshell.pageengine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
sealed interface BridgeMessage {

    val protocol: Int

    @Serializable
    sealed interface Incoming : BridgeMessage {
        @Serializable
        @SerialName("PageReady")
        data class PageReady(
            val url: String,
            val title: String? = null,
            override val protocol: Int = com.glmwebshell.core.common.Constants.BRIDGE_PROTOCOL_VERSION,
        ) : Incoming

        @Serializable
        @SerialName("ChatTurnCaptured")
        data class ChatTurnCaptured(
            val role: String,
            val content: String,
            val capturedBy: String,
            val ts: Long,
            override val protocol: Int = com.glmwebshell.core.common.Constants.BRIDGE_PROTOCOL_VERSION,
        ) : Incoming

        @Serializable
        @SerialName("NetworkStreamChunk")
        data class NetworkStreamChunk(
            val endpoint: String,
            val chunk: String,
            override val protocol: Int = com.glmwebshell.core.common.Constants.BRIDGE_PROTOCOL_VERSION,
        ) : Incoming

        @Serializable
        @SerialName("SemanticDomEvent")
        data class SemanticDomEvent(
            val kind: String,
            val payload: JsonElement,
            override val protocol: Int = com.glmwebshell.core.common.Constants.BRIDGE_PROTOCOL_VERSION,
        ) : Incoming

        @Serializable
        @SerialName("InputDetected")
        data class InputDetected(
            val locator: String,
            override val protocol: Int = com.glmwebshell.core.common.Constants.BRIDGE_PROTOCOL_VERSION,
        ) : Incoming

        @Serializable
        @SerialName("SendButtonDetected")
        data class SendButtonDetected(
            val locator: String,
            override val protocol: Int = com.glmwebshell.core.common.Constants.BRIDGE_PROTOCOL_VERSION,
        ) : Incoming

        @Serializable
        @SerialName("BlobUrl")
        data class BlobUrl(
            val url: String,
            val mime: String,
            val suggestedName: String? = null,
            override val protocol: Int = com.glmwebshell.core.common.Constants.BRIDGE_PROTOCOL_VERSION,
        ) : Incoming

        @Serializable
        @SerialName("SelfTestResult")
        data class SelfTestResult(
            val capabilityId: String,
            val passed: Boolean,
            val activeStrategy: String? = null,
            override val protocol: Int = com.glmwebshell.core.common.Constants.BRIDGE_PROTOCOL_VERSION,
        ) : Incoming

        @Serializable
        @SerialName("Error")
        data class Error(
            val message: String,
            override val protocol: Int = com.glmwebshell.core.common.Constants.BRIDGE_PROTOCOL_VERSION,
        ) : Incoming
    }

    @Serializable
    sealed interface Outgoing : BridgeMessage {
        @Serializable
        @SerialName("RequestSelfTest")
        data class RequestSelfTest(
            val capabilityIds: List<String>,
            override val protocol: Int = com.glmwebshell.core.common.Constants.BRIDGE_PROTOCOL_VERSION,
        ) : Outgoing

        @Serializable
        @SerialName("RequestFill")
        data class RequestFill(
            val locator: String,
            val text: String,
            override val protocol: Int = com.glmwebshell.core.common.Constants.BRIDGE_PROTOCOL_VERSION,
        ) : Outgoing

        @Serializable
        @SerialName("RequestSend")
        data class RequestSend(
            val locator: String,
            override val protocol: Int = com.glmwebshell.core.common.Constants.BRIDGE_PROTOCOL_VERSION,
        ) : Outgoing

        @Serializable
        @SerialName("ObserveStream")
        data class ObserveStream(
            val endpointPatterns: List<String>,
            override val protocol: Int = com.glmwebshell.core.common.Constants.BRIDGE_PROTOCOL_VERSION,
        ) : Outgoing

        @Serializable
        @SerialName("QueryDom")
        data class QueryDom(
            val query: String,
            val asJsonPath: Boolean = false,
            override val protocol: Int = com.glmwebshell.core.common.Constants.BRIDGE_PROTOCOL_VERSION,
        ) : Outgoing

        @Serializable
        @SerialName("Ping")
        data class Ping(
            val nonce: String,
            override val protocol: Int = com.glmwebshell.core.common.Constants.BRIDGE_PROTOCOL_VERSION,
        ) : Outgoing
    }
}
