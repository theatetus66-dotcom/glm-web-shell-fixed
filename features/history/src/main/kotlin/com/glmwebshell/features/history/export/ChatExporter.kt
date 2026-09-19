package com.glmwebshell.features.history.export

import com.glmwebshell.core.data.db.entity.ChatEntity
import com.glmwebshell.core.data.db.entity.MessageEntity
import com.glmwebshell.core.data.db.entity.MessageRole
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** TZ §6.3.2 — Markdown / text / JSON export. */
sealed interface ChatExport {
    val filename: String
    val mime: String
    val content: String

    data class Markdown(override val content: String, val title: String) : ChatExport {
        override val filename = sanitizeFilename("$title.md")
        override val mime = "text/markdown"
    }
    data class PlainText(override val content: String, val title: String) : ChatExport {
        override val filename = sanitizeFilename("$title.txt")
        override val mime = "text/plain"
    }
    data class Json(override val content: String, val title: String) : ChatExport {
        override val filename = sanitizeFilename("$title.json")
        override val mime = "application/json"
    }
}

private fun sanitizeFilename(name: String): String =
    name.map { c ->
        when {
            c.isLetterOrDigit() || c in "._- " -> c
            else -> '_'
        }
    }.joinToString("").trim().ifBlank { "chat" }

object ChatExporter {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun toMarkdown(chat: ChatEntity, messages: List<MessageEntity>): ChatExport.Markdown {
        val sb = StringBuilder()
        sb.appendLine("# ${chat.title}")
        sb.appendLine()
        sb.appendLine("- URL: ${chat.url}")
        sb.appendLine("- Source: ${chat.source}")
        sb.appendLine("- Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(chat.createdAt))}")
        sb.appendLine()
        for (m in messages) {
            val speaker = when (m.role) {
                MessageRole.USER -> "**You**"
                MessageRole.ASSISTANT -> "**Assistant**"
                MessageRole.SYSTEM -> "_System_"
                MessageRole.TOOL -> "_Tool_"
            }
            sb.appendLine("$speaker:")
            sb.appendLine()
            sb.appendLine(m.content)
            sb.appendLine()
        }
        return ChatExport.Markdown(sb.toString(), chat.title.ifBlank { "chat" })
    }

    fun toPlainText(chat: ChatEntity, messages: List<MessageEntity>): ChatExport.PlainText {
        val sb = StringBuilder()
        sb.appendLine("== ${chat.title} ==")
        for (m in messages) {
            sb.appendLine("[${m.role.name.lowercase()}]")
            sb.appendLine(m.content)
            sb.appendLine()
        }
        return ChatExport.PlainText(sb.toString(), chat.title.ifBlank { "chat" })
    }

    fun toJson(chat: ChatEntity, messages: List<MessageEntity>): ChatExport.Json {
        val text = buildString {
            appendLine("{")
            appendLine("  \"chat\": " + json.encodeToString(ChatEntity.serializer(), chat) + ",")
            appendLine("  \"messages\": " + json.encodeToString(
                ListSerializer(MessageEntity.serializer()), messages))
            appendLine("}")
        }
        return ChatExport.Json(text, chat.title.ifBlank { "chat" })
    }
}
