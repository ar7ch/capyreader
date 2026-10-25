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
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType.Companion.PrimaryNotEditable
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.capyreader.app.common.RowItem
import com.capyreader.app.ui.components.FormSection
import com.capyreader.app.ui.components.TextSwitch
import com.capyreader.app.ui.settings.PreferenceSelect
import com.jocmp.aiprofiles.AiProfile
import com.jocmp.aiprofiles.ThinkingLevel
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableColumn

@Composable
fun AISettingsPanel(
    viewModel: AISettingsViewModel = koinViewModel(),
) {
    AISettingsPanelView(
        enabled = viewModel.enabled,
        updateEnabled = viewModel::updateEnabled,
        profiles = viewModel.profiles,
        presets = viewModel.presets,
        saveProfile = viewModel::saveProfile,
        deleteProfile = viewModel::deleteProfile,
        moveProfile = viewModel::moveProfile,
        testResult = viewModel.testResult,
        testProfile = viewModel::testProfile,
        clearTest = viewModel::clearTest,
        thinkingLevel = viewModel.thinkingLevel,
        updateThinkingLevel = viewModel::updateThinkingLevel,
        instructions = viewModel.instructions,
        updateInstructions = viewModel::updateInstructions,
    )
}

@Composable
fun AISettingsPanelView(
    enabled: Boolean,
    updateEnabled: (Boolean) -> Unit,
    profiles: List<AiProfile>,
    presets: List<AiProfile>,
    saveProfile: (Int?, AiProfile) -> Unit,
    deleteProfile: (Int) -> Unit,
    moveProfile: (Int, Int) -> Unit,
    testResult: ConnectionTest?,
    testProfile: (AiProfile) -> Unit,
    clearTest: () -> Unit,
    thinkingLevel: ThinkingLevel,
    updateThinkingLevel: (ThinkingLevel) -> Unit,
    instructions: String,
    updateInstructions: (String) -> Unit,
) {
    // Null while closed; NEW_PROFILE while adding one.
    var editing by remember { mutableStateOf<Int?>(null) }

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

        FormSection(title = stringResource(R.string.settings_ai_profiles_title)) {
            RowItem {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.settings_ai_profiles_hint),
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                    ReorderableColumn(
                        list = profiles,
                        onSettle = moveProfile,
                    ) { index, profile, _ ->
                        ReorderableItem {
                            ProfileRow(
                                profile = profile,
                                onEdit = {
                                    clearTest()
                                    editing = index
                                },
                                onMoveUp = { moveProfile(index, index - 1) }.takeIf { index > 0 },
                                onMoveDown = { moveProfile(index, index + 1) }.takeIf { index < profiles.lastIndex },
                                modifier = Modifier.longPressDraggableHandle(),
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            clearTest()
                            editing = NEW_PROFILE
                        },
                    ) {
                        Text(stringResource(R.string.settings_ai_add_profile))
                    }
                }
            }
        }

        FormSection(title = stringResource(R.string.settings_ai_requests_title)) {
            RowItem {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PreferenceSelect(
                        selected = thinkingLevel,
                        update = updateThinkingLevel,
                        options = ThinkingLevel.entries,
                        label = R.string.settings_ai_thinking_level,
                        optionText = { stringResource(thinkingLevelText(it)) },
                    )
                    TextField(
                        value = instructions,
                        onValueChange = updateInstructions,
                        label = { Text(stringResource(R.string.settings_ai_instructions)) },
                        supportingText = { Text(stringResource(R.string.settings_ai_instructions_hint)) },
                        minLines = 3,
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

    editing?.let { index ->
        val isNew = index == NEW_PROFILE

        ProfileEditor(
            initial = profiles.getOrNull(index) ?: AiProfile(),
            isNew = isNew,
            presets = presets,
            testResult = testResult,
            onTest = testProfile,
            onChange = clearTest,
            onSave = { profile ->
                saveProfile(index.takeUnless { isNew }, profile)
                editing = null
            },
            onDelete = {
                deleteProfile(index)
                editing = null
            },
            onDismiss = {
                clearTest()
                editing = null
            },
        )
    }
}

@Composable
private fun ProfileRow(
    profile: AiProfile,
    onEdit: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val moveUp = stringResource(R.string.settings_ai_move_up)
    val moveDown = stringResource(R.string.settings_ai_move_down)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .semantics {
                // Dragging needs a pointer, so screen readers get the same moves as actions.
                customActions = listOfNotNull(
                    onMoveUp?.let { CustomAccessibilityAction(moveUp) { it(); true } },
                    onMoveDown?.let { CustomAccessibilityAction(moveDown) { it(); true } },
                )
            }
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = profile.label, style = typography.bodyLarge)
            Text(
                text = listOf(profile.model, profile.baseUrl.toHttpUrlOrNull()?.host.orEmpty())
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileEditor(
    initial: AiProfile,
    isNew: Boolean,
    presets: List<AiProfile>,
    testResult: ConnectionTest?,
    onTest: (AiProfile) -> Unit,
    onChange: () -> Unit,
    onSave: (AiProfile) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }

    fun edit(profile: AiProfile) {
        draft = profile
        onChange()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(if (isNew) R.string.settings_ai_add_profile else R.string.settings_ai_edit_profile)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                PresetMenu(
                    presets = presets,
                    onSelect = { preset ->
                        edit(
                            draft.copy(
                                name = draft.name.ifBlank { preset.name },
                                baseUrl = preset.baseUrl,
                                model = preset.model,
                            )
                        )
                    },
                )
                TextField(
                    value = draft.name,
                    onValueChange = { edit(draft.copy(name = it)) },
                    label = { Text(stringResource(R.string.settings_ai_profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = draft.baseUrl,
                    onValueChange = { edit(draft.copy(baseUrl = it)) },
                    label = { Text(stringResource(R.string.settings_ai_base_url)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = draft.apiKey,
                    onValueChange = { edit(draft.copy(apiKey = it)) },
                    label = { Text(stringResource(R.string.settings_ai_api_key)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = draft.model,
                    onValueChange = { edit(draft.copy(model = it)) },
                    label = { Text(stringResource(R.string.settings_ai_model)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TestConnection(
                    enabled = draft.usable,
                    result = testResult,
                    onTest = { onTest(draft.trimmed()) },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft.usable,
                onClick = { onSave(draft) },
            ) {
                Text(stringResource(R.string.settings_ai_save_profile))
            }
        },
        dismissButton = {
            Row {
                if (!isNew) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.settings_ai_delete_profile))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
    )
}

private fun AiProfile.trimmed() = copy(baseUrl = baseUrl.trim(), apiKey = apiKey.trim(), model = model.trim())

@Composable
private fun TestConnection(
    enabled: Boolean,
    result: ConnectionTest?,
    onTest: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            enabled = enabled && result != ConnectionTest.Running,
            onClick = onTest,
        ) {
            Text(stringResource(R.string.settings_ai_test))
        }
        val message = when (result) {
            null -> null
            ConnectionTest.Running -> stringResource(R.string.settings_ai_test_running)
            is ConnectionTest.Passed -> stringResource(R.string.settings_ai_test_passed, result.reply)
            is ConnectionTest.Unexpected -> stringResource(R.string.settings_ai_test_unexpected, result.reply)
            is ConnectionTest.Failed -> stringResource(R.string.settings_ai_test_failed, result.message)
        }
        if (message != null) {
            Text(
                text = message,
                style = typography.bodySmall,
                color = if (result is ConnectionTest.Passed) colorScheme.onSurfaceVariant else colorScheme.error,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun thinkingLevelText(level: ThinkingLevel) = when (level) {
    ThinkingLevel.NONE -> R.string.settings_ai_thinking_level_none
    ThinkingLevel.LOW -> R.string.settings_ai_thinking_level_low
    ThinkingLevel.MEDIUM -> R.string.settings_ai_thinking_level_medium
    ThinkingLevel.HIGH -> R.string.settings_ai_thinking_level_high
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

private const val NEW_PROFILE = -1
