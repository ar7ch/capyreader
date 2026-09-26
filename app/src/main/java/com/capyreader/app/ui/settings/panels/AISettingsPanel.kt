package com.capyreader.app.ui.settings.panels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType.Companion.PrimaryNotEditable
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.capyreader.app.common.RowItem
import com.capyreader.app.preferences.AiProfile
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
        presets = viewModel.presets,
        profiles = viewModel.profiles,
        applyProfile = viewModel::applyProfile,
        saveProfile = viewModel::saveProfile,
        deleteProfile = viewModel::deleteProfile,
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
    presets: List<AiProfile>,
    profiles: List<AiProfile>,
    applyProfile: (AiProfile) -> Unit,
    saveProfile: (String) -> Unit,
    deleteProfile: (AiProfile) -> Unit,
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
                    PresetMenu(presets = presets, onSelect = applyProfile)
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

        FormSection(title = stringResource(R.string.settings_ai_profiles_title)) {
            RowItem {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    profiles.forEach { profile ->
                        ProfileRow(
                            profile = profile,
                            onApply = { applyProfile(profile) },
                            onDelete = { deleteProfile(profile) },
                        )
                    }
                    SaveProfileButton(onSave = saveProfile)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetMenu(
    presets: List<AiProfile>,
    onSelect: (AiProfile) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor(PrimaryNotEditable)
                .fillMaxWidth(),
            readOnly = true,
            value = "",
            onValueChange = {},
            placeholder = { Text(stringResource(R.string.settings_ai_preset_label)) },
            label = { Text(stringResource(R.string.settings_ai_preset_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.name) },
                    onClick = {
                        onSelect(preset)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: AiProfile,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onApply)
                .padding(vertical = 8.dp),
        ) {
            Text(text = profile.name, style = typography.bodyLarge)
            Text(
                text = profile.model,
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.settings_ai_delete_profile),
            )
        }
    }
}

@Composable
private fun SaveProfileButton(
    onSave: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    TextButton(onClick = { showDialog = true }) {
        Text(stringResource(R.string.settings_ai_save_profile))
    }

    if (showDialog) {
        var name by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.settings_ai_save_profile)) },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_ai_profile_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onSave(name)
                        showDialog = false
                    },
                ) {
                    Text(stringResource(R.string.settings_ai_save_profile))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
