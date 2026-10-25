package com.capyreader.app.ui.settings.panels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.capyreader.app.preferences.AiProfile
import com.capyreader.app.preferences.AppPreferences
import androidx.lifecycle.viewModelScope
import com.jocmp.capy.ai.ChatClient
import com.jocmp.capy.ai.ThinkingLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AISettingsViewModel(
    private val appPreferences: AppPreferences,
    private val chatClient: ChatClient,
) : ViewModel() {
    private val options = appPreferences.aiOptions

    val presets: List<AiProfile> = AppPreferences.AiOptions.PRESETS

    var enabled by mutableStateOf(options.enabled.get())
        private set

    var baseURL by mutableStateOf(options.baseURL.get())
        private set

    var apiKey by mutableStateOf(options.apiKey.get())
        private set

    var model by mutableStateOf(options.model.get())
        private set

    var thinkingLevel by mutableStateOf(options.thinkingLevel.get())
        private set

    var instructions by mutableStateOf(options.instructions.get())
        private set

    private var testJob: Job? = null

    var testResult by mutableStateOf<ConnectionTest?>(null)
        private set

    var profiles by mutableStateOf(options.profiles.get())
        private set

    fun updateEnabled(enabled: Boolean) {
        options.enabled.set(enabled)
        this.enabled = enabled
    }

    fun updateBaseURL(baseURL: String) {
        clearTest()
        val trimmed = baseURL.trim()
        options.baseURL.set(trimmed)
        this.baseURL = trimmed
    }

    fun updateApiKey(apiKey: String) {
        clearTest()
        val trimmed = apiKey.trim()
        options.apiKey.set(trimmed)
        this.apiKey = trimmed
    }

    fun updateModel(model: String) {
        clearTest()
        val trimmed = model.trim()
        options.model.set(trimmed)
        this.model = trimmed
    }

    fun updateThinkingLevel(level: ThinkingLevel) {
        clearTest()
        options.thinkingLevel.set(level)
        thinkingLevel = level
    }

    fun updateInstructions(instructions: String) {
        clearTest()
        options.instructions.set(instructions)
        this.instructions = instructions
    }

    /** Sends a question with a known answer to the current connection. */
    fun testConnection() {
        testJob?.cancel()
        testResult = ConnectionTest.Running

        testJob = viewModelScope.launch {
            testResult = chatClient.complete(
                settings = options.settings(),
                system = "You are a calculator. Reply with the number only.",
                user = "What is 17 + 25?",
            ).fold(
                onSuccess = { reply ->
                    if (reply.contains("42")) ConnectionTest.Passed(reply) else ConnectionTest.Unexpected(reply)
                },
                onFailure = { ConnectionTest.Failed(it.message.orEmpty()) },
            )
        }
    }

    /** A result only describes the settings it ran with, so any edit drops it. */
    private fun clearTest() {
        testJob?.cancel()
        testJob = null
        testResult = null
    }

    /** Fills the connection fields from a preset or saved profile. A blank key leaves the current one. */
    fun applyProfile(profile: AiProfile) {
        updateBaseURL(profile.baseURL)
        updateModel(profile.model)

        if (profile.apiKey.isNotBlank()) {
            updateApiKey(profile.apiKey)
        }
    }

    /** Saves the current connection under [name], replacing any profile with the same name. */
    fun saveProfile(name: String) {
        val trimmed = name.trim()

        if (trimmed.isBlank()) {
            return
        }

        val profile = AiProfile(name = trimmed, baseURL = baseURL, model = model, apiKey = apiKey)
        val updated = profiles.filterNot { it.name.equals(trimmed, ignoreCase = true) } + profile

        options.profiles.set(updated)
        profiles = updated
    }

    fun deleteProfile(profile: AiProfile) {
        val updated = profiles.filterNot { it.name == profile.name }
        options.profiles.set(updated)
        profiles = updated
    }
}

sealed interface ConnectionTest {
    data object Running : ConnectionTest

    data class Passed(val reply: String) : ConnectionTest

    data class Unexpected(val reply: String) : ConnectionTest

    data class Failed(val message: String) : ConnectionTest
}
