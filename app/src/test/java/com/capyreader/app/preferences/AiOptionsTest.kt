package com.capyreader.app.preferences

import android.content.Context
import com.jocmp.aiprofiles.AiProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AiOptionsTest {
    private val sharedPreferences = RuntimeEnvironment.getApplication()
        .getSharedPreferences("ai-options-test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    private val options = AppPreferences.AiOptions(AndroidPreferenceStore(sharedPreferences))

    @Test
    fun migrateLegacyConnection_putsTheLiveConnectionFirst() {
        val saved = AiProfile(name = "Backup", baseUrl = "https://backup.example/v1", model = "b")
        sharedPreferences.edit()
            .putString("ai_base_url", "https://api.z.ai/api/paas/v4")
            .putString("ai_api_key", "key")
            .putString("ai_model", "glm-4.6")
            .putString("ai_profiles", AiProfile.encodeList(listOf(saved)))
            .commit()

        options.migrateLegacyConnection()

        assertEquals(
            listOf(
                AiProfile(name = "Z.AI", baseUrl = "https://api.z.ai/api/paas/v4", apiKey = "key", model = "glm-4.6"),
                saved,
            ),
            options.profiles.get(),
        )
        assertFalse(sharedPreferences.contains("ai_base_url"))
        assertFalse(sharedPreferences.contains("ai_api_key"))
    }

    @Test
    fun migrateLegacyConnection_keepsTheNameOfTheSavedCopy() {
        val copy = AiProfile(name = "Work", baseUrl = "https://api.openai.com/v1", model = "gpt-4.1-mini")
        sharedPreferences.edit()
            .putString("ai_api_key", "key")
            .putString("ai_profiles", AiProfile.encodeList(listOf(copy)))
            .commit()

        options.migrateLegacyConnection()

        assertEquals(listOf(copy.copy(apiKey = "key")), options.profiles.get())
    }

    @Test
    fun migrateLegacyConnection_leavesAFreshInstallEmpty() {
        options.migrateLegacyConnection()

        assertEquals(emptyList<AiProfile>(), options.profiles.get())
    }
}
