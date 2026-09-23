package com.capyreader.app.ui.settings.panels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.capyreader.app.preferences.AppPreferences

class AISettingsViewModel(
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val options = appPreferences.aiOptions

    var enabled by mutableStateOf(options.enabled.get())
        private set

    var baseURL by mutableStateOf(options.baseURL.get())
        private set

    var apiKey by mutableStateOf(options.apiKey.get())
        private set

    var model by mutableStateOf(options.model.get())
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
}
