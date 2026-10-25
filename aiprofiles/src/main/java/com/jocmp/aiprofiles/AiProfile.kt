package com.jocmp.aiprofiles

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames

/** One OpenAI compatible connection. See README.md for the rules all apps share. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AiProfile(
    val name: String = "",
    /** Up to and including the version path, e.g. https://api.openai.com/v1 */
    @JsonNames("baseURL")
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
) {
    val usable: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank()

    val label: String get() = name.ifBlank { model }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Reads a stored list, oldest format included. Anything unreadable is an empty list. */
        fun decodeList(stored: String): List<AiProfile> =
            runCatching { json.decodeFromString<List<AiProfile>>(stored) }.getOrDefault(emptyList())

        fun encodeList(profiles: List<AiProfile>): String = json.encodeToString(profiles)
    }
}

enum class ThinkingLevel(val effort: String) {
    NONE("none"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
}

/** Common services as starting points. The user adds the key. */
val AiPresets = listOf(
    AiProfile(name = "OpenAI", baseUrl = "https://api.openai.com/v1", model = "gpt-4.1-mini"),
    AiProfile(name = "Z.AI coding plan", baseUrl = "https://api.z.ai/api/coding/paas/v4", model = "glm-4.6"),
    AiProfile(name = "Z.AI", baseUrl = "https://api.z.ai/api/paas/v4", model = "glm-4.6"),
    AiProfile(name = "Zhipu GLM", baseUrl = "https://open.bigmodel.cn/api/paas/v4", model = "glm-4.6"),
    AiProfile(name = "Anthropic", baseUrl = "https://api.anthropic.com/v1", model = "claude-haiku-4-5"),
    AiProfile(name = "Google Gemini", baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai", model = "gemini-2.5-flash"),
    AiProfile(name = "OpenRouter", baseUrl = "https://openrouter.ai/api/v1", model = "openai/gpt-4.1-mini"),
    AiProfile(name = "Ollama", baseUrl = "http://localhost:11434/v1", model = "qwen3"),
)
