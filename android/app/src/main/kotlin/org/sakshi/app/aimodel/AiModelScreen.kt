package org.sakshi.app.aimodel

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
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
import org.sakshi.app.R
import org.sakshi.app.ui.components.ConfirmDialog
import org.sakshi.app.ui.components.DestructiveButton
import org.sakshi.app.ui.components.MoreInfo
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.SakshiCard
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SecondaryButton
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.StatusChip
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.components.Tone
import org.sakshi.app.ui.theme.Spacing
import org.sakshi.processing.llm.model.ModelPreset

@Composable
fun AiModelScreen(
    state: AiModelUiState,
    onBack: () -> Unit,
    onDownloadPreset: (ModelPreset) -> Unit,
    onPickerOpening: () -> Unit,
    onPickerClosed: () -> Unit,
    onImportUri: (Uri) -> Unit,
    onImportDetected: (ModelPreset) -> Unit,
    onDeleteModel: (String) -> Unit,
    onClearNotice: () -> Unit,
    onTestInference: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDeleteFor by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    BackHandler {
        if (confirmDeleteFor != null) {
            confirmDeleteFor = null
        } else {
            onBack()
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        onPickerClosed()
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
            contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                InstalledModelCard(
                    installed = state.installedModel,
                    isTesting = state.isTestingInference,
                    testOutput = state.testOutput,
                    onDelete = { confirmDeleteFor = state.installedModel?.filename },
                    onTestInference = onTestInference,
                )
            }

            item {
                InstallActions(
                    isImporting = state.isImporting,
                    importedBytes = state.importedBytes,
                    totalBytes = state.totalBytesToImport,
                    detectedPreset = state.detectedInDownloads,
                    onPickFile = {
                        onPickerOpening()
                        filePicker.launch(arrayOf("*/*"))
                    },
                    onImportDetected = onImportDetected,
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SectionHeader(stringResource(R.string.ai_model_download_heading))
                    SupportingText(stringResource(R.string.ai_model_download_line))
                }
            }

            items(state.presets, key = { it.id }) { preset ->
                PresetCard(preset = preset, onDownload = { onDownloadPreset(preset) })
            }

            item {
                MoreInfo(stringResource(R.string.ai_model_about)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        SupportingText(stringResource(R.string.ai_model_download_body))
                        SupportingText(stringResource(R.string.ai_model_no_model))
                        state.installedModel?.let {
                            SupportingText(it.statusDescription)
                            SupportingText(stringResource(R.string.ai_model_hash_line, it.sha256Prefix))
                        }
                    }
                }
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
    isTesting: Boolean,
    testOutput: String?,
    onDelete: () -> Unit,
    onTestInference: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SakshiCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(stringResource(R.string.ai_model_installed_heading), Modifier.weight(1f))
                if (installed != null) {
                    StatusChip(stringResource(R.string.ai_model_chip_installed), tone = Tone.Success, icon = Icons.Default.Check)
                } else {
                    StatusChip(stringResource(R.string.ai_model_chip_none))
                }
            }
            if (installed != null) {
                Text(
                    stringResource(R.string.ai_model_name_size, installed.displayName, installed.formattedSize),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PrimaryButton(
                        text = if (isTesting) stringResource(R.string.ai_model_testing) else stringResource(R.string.ai_model_btn_test),
                        onClick = onTestInference,
                        enabled = !isTesting,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.PlayArrow,
                    )
                    DestructiveButton(
                        text = stringResource(R.string.ai_model_btn_delete),
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (testOutput != null) {
                    SakshiCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(
                                text = stringResource(R.string.ai_model_test_result_label),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            SupportingText(testOutput)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallActions(
    isImporting: Boolean,
    importedBytes: Long,
    totalBytes: Long,
    detectedPreset: ModelPreset?,
    onPickFile: () -> Unit,
    onImportDetected: (ModelPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (detectedPreset != null) {
            Text(
                text = stringResource(R.string.ai_model_detected_in_downloads, detectedPreset.displayName),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (detectedPreset != null) {
                PrimaryButton(
                    text = stringResource(R.string.ai_model_btn_import_detected),
                    onClick = { onImportDetected(detectedPreset) },
                    enabled = !isImporting,
                    modifier = Modifier.weight(1f),
                )
            }
            SecondaryButton(
                text = stringResource(R.string.ai_model_btn_import),
                onClick = onPickFile,
                enabled = !isImporting,
                modifier = Modifier.weight(1f),
            )
        }
        if (isImporting) {
            val megabytes = importedBytes / BYTES_PER_MB
            if (totalBytes > 0L) {
                val progress = (importedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                SupportingText(stringResource(R.string.ai_model_import_progress, megabytes, totalBytes / BYTES_PER_MB))
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                SupportingText(stringResource(R.string.ai_model_import_copied, megabytes))
            }
        }
    }
}

private const val BYTES_PER_MB = 1024L * 1024L

@Composable
private fun PresetCard(
    preset: ModelPreset,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SakshiCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(preset.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (preset.isRecommended) {
                    StatusChip(stringResource(R.string.ai_model_chip_recommended), tone = Tone.Brand)
                }
                Text(preset.sizeDescription, style = MaterialTheme.typography.labelLarge)
            }
            SupportingText(preset.description)
            SecondaryButton(
                text = stringResource(R.string.ai_model_btn_download),
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
