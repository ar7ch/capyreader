package com.capyreader.app.ui.settings.panels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capyreader.app.preferences.AppPreferences
import com.jocmp.aiprofiles.AiPresets
import com.jocmp.aiprofiles.AiProfile
import com.jocmp.aiprofiles.ThinkingLevel
import com.jocmp.capy.ai.AiSettings
import com.jocmp.capy.ai.ChatClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AISettingsViewModel(
    appPreferences: AppPreferences,
    private val chatClient: ChatClient,
) : ViewModel() {
    private val options = appPreferences.aiOptions.also { it.migrateLegacyConnection() }

    val presets: List<AiProfile> = AiPresets

    var enabled by mutableStateOf(options.enabled.get())
        private set

    var profiles by mutableStateOf(options.profiles.get())
        private set

    var thinkingLevel by mutableStateOf(options.thinkingLevel.get())
        private set

    var instructions by mutableStateOf(options.instructions.get())
        private set

    private var testJob: Job? = null

    var testResult by mutableStateOf<ConnectionTest?>(null)
        private set

    fun updateEnabled(enabled: Boolean) {
        options.enabled.set(enabled)
        this.enabled = enabled
    }

    fun updateThinkingLevel(level: ThinkingLevel) {
        options.thinkingLevel.set(level)
        thinkingLevel = level
    }

    fun updateInstructions(instructions: String) {
        options.instructions.set(instructions)
        this.instructions = instructions
    }

    /** Replaces the profile at [index], or adds [profile] at the bottom when [index] is null. */
    fun saveProfile(index: Int?, profile: AiProfile) {
        val clean = profile.copy(
            name = profile.name.trim(),
            baseUrl = profile.baseUrl.trim(),
            apiKey = profile.apiKey.trim(),
            model = profile.model.trim(),
        )

        updateProfiles(
            if (index != null && index in profiles.indices) {
                profiles.toMutableList().also { it[index] = clean }
            } else {
                profiles + clean
            }
        )
    }

    fun deleteProfile(index: Int) {
        updateProfiles(profiles.filterIndexed { i, _ -> i != index })
    }

    fun moveProfile(from: Int, to: Int) {
        if (from == to || from !in profiles.indices || to !in profiles.indices) {
            return
        }

        updateProfiles(profiles.toMutableList().also { it.add(to, it.removeAt(from)) })
    }

    /** Sends a question with a known answer to [profile] alone, without falling back. */
    fun testProfile(profile: AiProfile) {
        testJob?.cancel()
        testResult = ConnectionTest.Running

        testJob = viewModelScope.launch {
            testResult = chatClient.complete(
                settings = AiSettings(profiles = listOf(profile), thinkingLevel = thinkingLevel),
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

    /** A result only describes the profile it ran with, so any edit drops it. */
    fun clearTest() {
        testJob?.cancel()
        testJob = null
        testResult = null
    }

    private fun updateProfiles(updated: List<AiProfile>) {
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
