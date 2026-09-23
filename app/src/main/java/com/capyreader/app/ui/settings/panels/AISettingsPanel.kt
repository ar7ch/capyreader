package com.capyreader.app.ui.settings.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.capyreader.app.common.RowItem
import com.capyreader.app.ui.components.FormSection
import com.capyreader.app.ui.components.TextSwitch
import org.koin.androidx.compose.koinViewModel

@Composable
fun AISettingsPanel(
    viewModel: AISettingsViewModel = koinViewModel(),
) {
    AISettingsPanelView(
        enabled = viewModel.enabled,
        updateEnabled = viewModel::updateEnabled,
        baseURL = viewModel.baseURL,
        updateBaseURL = viewModel::updateBaseURL,
        apiKey = viewModel.apiKey,
        updateApiKey = viewModel::updateApiKey,
        model = viewModel.model,
        updateModel = viewModel::updateModel,
    )
}

@Composable
fun AISettingsPanelView(
    enabled: Boolean,
    updateEnabled: (Boolean) -> Unit,
    baseURL: String,
    updateBaseURL: (String) -> Unit,
    apiKey: String,
    updateApiKey: (String) -> Unit,
    model: String,
    updateModel: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        FormSection(title = stringResource(R.string.settings_panel_ai_title)) {
            RowItem {
                TextSwitch(
                    checked = enabled,
                    onCheckedChange = updateEnabled,
                    title = stringResource(R.string.settings_ai_enable_title),
                    subtitle = stringResource(R.string.settings_ai_enable_subtitle),
                )
            }
        }

        FormSection(title = stringResource(R.string.settings_ai_connection_title)) {
            RowItem {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = baseURL,
                        onValueChange = updateBaseURL,
                        label = { Text(stringResource(R.string.settings_ai_base_url)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = apiKey,
                        onValueChange = updateApiKey,
                        label = { Text(stringResource(R.string.settings_ai_api_key)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = model,
                        onValueChange = updateModel,
                        label = { Text(stringResource(R.string.settings_ai_model)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.settings_ai_privacy_notice),
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
