package com.capyreader.app.ui.settings.panels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.capyreader.app.preferences.AiProfile
import com.capyreader.app.preferences.AppPreferences

class AISettingsViewModel(
    private val appPreferences: AppPreferences,
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

    var profiles by mutableStateOf(options.profiles.get())
        private set

    fun updateEnabled(enabled: Boolean) {
        options.enabled.set(enabled)
        this.enabled = enabled
    }

    fun updateBaseURL(baseURL: String) {
        val trimmed = baseURL.trim()
        options.baseURL.set(trimmed)
        this.baseURL = trimmed
    }

    fun updateApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        options.apiKey.set(trimmed)
        this.apiKey = trimmed
    }

    fun updateModel(model: String) {
        val trimmed = model.trim()
        options.model.set(trimmed)
        this.model = trimmed
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
