package org.sakshi.app.aimodel

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.sakshi.app.R
import org.sakshi.app.ui.components.ConfirmDialog
import org.sakshi.app.ui.components.DestructiveButton
import org.sakshi.app.ui.components.LabelValue
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.SakshiCard
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SecondaryButton
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.theme.Spacing
import org.sakshi.processing.llm.model.ModelPreset

@Composable
fun AiModelScreen(
    state: AiModelUiState,
    onBack: () -> Unit,
    onDownloadPreset: (ModelPreset) -> Unit,
    onImportUri: (Uri) -> Unit,
    onDeleteModel: (String) -> Unit,
    onClearNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDeleteFor by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            onImportUri(uri)
        }
    }

    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            snackbar.showSnackbar(state.notice)
            onClearNotice()
        }
    }

    LaunchedEffect(state.error) {
        if (state.error != null) {
            snackbar.showSnackbar(state.error)
            onClearNotice()
        }
    }

    SakshiScaffold(
        title = stringResource(R.string.ai_model_title),
        modifier = modifier,
        onBack = onBack,
        snackbarHostState = snackbar,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item {
                InstalledModelCard(
                    installed = state.installedModel,
                    onDelete = { confirmDeleteFor = state.installedModel?.filename },
                )
            }

            item {
                DownloadInstructionsCard(
                    isImporting = state.isImporting,
                    onPickFile = { filePicker.launch(arrayOf("*/*")) },
                )
            }

            item {
                SectionHeader(stringResource(R.string.ai_model_download_heading))
            }

            items(state.presets, key = { it.id }) { preset ->
                PresetCard(preset = preset, onDownload = { onDownloadPreset(preset) })
            }
        }
    }

    val targetToDelete = confirmDeleteFor
    if (targetToDelete != null) {
        ConfirmDialog(
            title = stringResource(R.string.ai_model_delete_confirm_title),
            body = stringResource(R.string.ai_model_delete_confirm_body),
            confirmLabel = stringResource(R.string.ai_model_delete_confirm_action),
            onConfirm = {
                onDeleteModel(targetToDelete)
                confirmDeleteFor = null
            },
            onDismiss = { confirmDeleteFor = null },
            destructive = true,
        )
    }
}

@Composable
private fun InstalledModelCard(
    installed: InstalledModel?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SakshiCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionHeader(stringResource(R.string.ai_model_installed_heading))
            if (installed != null) {
                LabelValue(stringResource(R.string.ai_model_file_label, ""), installed.filename)
                LabelValue(stringResource(R.string.ai_model_size_label, ""), installed.formattedSize)
                LabelValue("Hash", installed.sha256Prefix)
                DestructiveButton(
                    text = stringResource(R.string.ai_model_btn_delete),
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SupportingText(stringResource(R.string.ai_model_no_model))
            }
        }
    }
}

@Composable
private fun DownloadInstructionsCard(
    isImporting: Boolean,
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SakshiCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionHeader("Step 2: Install downloaded model")
            SupportingText(stringResource(R.string.ai_model_download_body))
            PrimaryButton(
                text = stringResource(R.string.ai_model_btn_import),
                onClick = onPickFile,
                enabled = !isImporting,
            )
            if (isImporting) {
                SupportingText(stringResource(R.string.ai_model_importing))
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: ModelPreset,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SakshiCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(preset.displayName, style = MaterialTheme.typography.titleSmall)
                if (preset.isRecommended) {
                    Text(
                        "Recommended",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            SupportingText(preset.description)
            Text(stringResource(R.string.ai_model_size_label, preset.sizeDescription), style = MaterialTheme.typography.bodySmall)
            SecondaryButton(
                text = stringResource(R.string.ai_model_btn_download),
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )
        }
    }
}
