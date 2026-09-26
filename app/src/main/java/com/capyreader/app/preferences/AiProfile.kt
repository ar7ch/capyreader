package com.capyreader.app.preferences

import kotlinx.serialization.Serializable

/**
 * A named connection for AI summaries. Built-in presets carry a blank [apiKey] the user fills in;
 * saved profiles keep whatever key was entered.
 */
@Serializable
data class AiProfile(
    val name: String,
    val baseURL: String,
    val model: String,
    val apiKey: String = "",
)
