package org.sakshi.app.evidence

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import org.sakshi.acquisition.importer.ImportLimits
import org.sakshi.app.R
import org.sakshi.app.ui.components.IconTile
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.theme.Spacing

/** Callbacks for adding evidence to a case other than pasting, which the screen handles with a dialog. */
class AddEvidenceCallbacks(
    val onDocuments: (List<Uri>) -> Unit,
    val onMedia: (List<Uri>) -> Unit,
    val onNote: () -> Unit,
    /** Called just before a system picker opens and again when it returns, so the app is not locked meanwhile. */
    val onPickerOpening: () -> Unit,
    val onPickerClosed: () -> Unit,
)

/**
 * One "Add evidence" button pinned to the bottom of the screen where a thumb reaches it. It opens a sheet with the four
 * ways to add evidence. The picker launchers live here, outside the sheet, so their results still arrive after the sheet
 * has closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEvidenceActions(enabled: Boolean, callbacks: AddEvidenceCallbacks, onPaste: () -> Unit, modifier: Modifier = Modifier) {
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        callbacks.onPickerClosed()
        callbacks.onDocuments(uris)
    }
    val media = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(ImportLimits().maxItems)) { uris ->
        callbacks.onPickerClosed()
        callbacks.onMedia(uris)
    }
    var sheetOpen by remember { mutableStateOf(false) }
    val choose = { action: () -> Unit ->
        sheetOpen = false
        action()
    }
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PrimaryButton(
            stringResource(R.string.detail_add_evidence),
            { sheetOpen = true },
            Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.md),
            enabled = enabled,
            icon = Icons.Default.Add,
        )
    }
    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            AddEvidenceChoices(
                onFiles = {
                    choose {
                        callbacks.onPickerOpening()
                        documents.launch(arrayOf("*/*"))
                    }
                },
                onMedia = {
                    choose {
                        callbacks.onPickerOpening()
                        media.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }
                },
                onPaste = { choose(onPaste) },
                onNote = { choose(callbacks.onNote) },
            )
        }
    }
}

/** The four rows of the add-evidence sheet: an icon tile and a short label each. */
@Composable
internal fun AddEvidenceChoices(onFiles: () -> Unit, onMedia: () -> Unit, onPaste: () -> Unit, onNote: () -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.sm)) {
        ChoiceRow(ImageVector.vectorResource(R.drawable.ic_folder), R.string.detail_add_files, onFiles)
        ChoiceRow(ImageVector.vectorResource(R.drawable.ic_image), R.string.detail_add_media, onMedia)
        ChoiceRow(ImageVector.vectorResource(R.drawable.ic_clipboard), R.string.detail_paste, onPaste)
        ChoiceRow(Icons.Default.Edit, R.string.detail_write_note, onNote)
    }
}

@Composable
private fun ChoiceRow(icon: ImageVector, label: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = Spacing.touchTarget + Spacing.md)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        IconTile(icon)
        Text(stringResource(label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
    }
}
