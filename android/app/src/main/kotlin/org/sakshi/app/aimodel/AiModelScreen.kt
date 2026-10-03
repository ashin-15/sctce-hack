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
            contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
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
                DownloadInstructionsCard(
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
            SectionHeader(stringResource(R.string.ai_model_installed_heading))
            if (installed != null) {
                LabelValue("Model", installed.displayName)
                LabelValue("Status", installed.statusDescription)
                LabelValue(stringResource(R.string.ai_model_file_label, ""), installed.filename)
                LabelValue(stringResource(R.string.ai_model_size_label, ""), installed.formattedSize)
                LabelValue("Hash", installed.sha256Prefix)

                PrimaryButton(
                    text = if (isTesting) stringResource(R.string.ai_model_testing) else stringResource(R.string.ai_model_btn_test),
                    onClick = onTestInference,
                    enabled = !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                )

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
    importedBytes: Long,
    totalBytes: Long,
    detectedPreset: ModelPreset?,
    onPickFile: () -> Unit,
    onImportDetected: (ModelPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    SakshiCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionHeader("Step 2: Install downloaded model")
            SupportingText(stringResource(R.string.ai_model_download_body))

            if (detectedPreset != null) {
                Text(
                    text = stringResource(R.string.ai_model_detected_in_downloads, detectedPreset.displayName),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                PrimaryButton(
                    text = stringResource(R.string.ai_model_btn_import_detected),
                    onClick = { onImportDetected(detectedPreset) },
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SecondaryButton(
                text = stringResource(R.string.ai_model_btn_import),
                onClick = onPickFile,
                enabled = !isImporting,
                modifier = Modifier.fillMaxWidth(),
            )

            if (isImporting) {
                if (totalBytes > 0L) {
                    val progress = (importedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    val currentMb = importedBytes / (1024 * 1024)
                    val totalMb = totalBytes / (1024 * 1024)
                    val percent = (progress * 100).toInt()
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SupportingText("$currentMb MB / $totalMb MB ($percent%)")
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val currentMb = importedBytes / (1024 * 1024)
                    SupportingText("$currentMb MB copied...")
                }
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
