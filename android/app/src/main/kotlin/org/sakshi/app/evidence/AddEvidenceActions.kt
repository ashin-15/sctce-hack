package org.sakshi.app.evidence

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import org.sakshi.acquisition.importer.ImportLimits
import org.sakshi.app.R
import org.sakshi.app.ui.components.SecondaryButton
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

/** Font scale from which the four buttons stack in one column instead of two rows of two. */
private const val STACK_FROM_FONT_SCALE = 1.3f

/** A fraction of the screen height above which the panel scrolls instead of growing. */
private const val MAX_PANEL_FRACTION = 0.45f

/**
 * The four ways to add evidence, as a panel pinned to the bottom of the screen where a thumb reaches it. Two rows of two
 * at normal text size, one stacked column at large text size. The panel scrolls if it would take over the screen.
 */
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
    val density = LocalDensity.current
    val stacked = density.fontScale >= STACK_FROM_FONT_SCALE
    val maxHeight = with(density) { (LocalWindowInfo.current.containerSize.height * MAX_PANEL_FRACTION).toDp() }
    val openFiles = {
        callbacks.onPickerOpening()
        documents.launch(arrayOf("*/*"))
    }
    val openMedia = {
        callbacks.onPickerOpening()
        media.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (stacked) {
                ActionButton(R.string.detail_add_files, enabled, Modifier.fillMaxWidth(), openFiles)
                ActionButton(R.string.detail_add_media, enabled, Modifier.fillMaxWidth(), openMedia)
                ActionButton(R.string.detail_paste, enabled, Modifier.fillMaxWidth(), onPaste)
                ActionButton(R.string.detail_write_note, enabled, Modifier.fillMaxWidth(), callbacks.onNote)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ActionButton(R.string.detail_add_files, enabled, Modifier.weight(1f), openFiles)
                    ActionButton(R.string.detail_add_media, enabled, Modifier.weight(1f), openMedia)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ActionButton(R.string.detail_paste, enabled, Modifier.weight(1f), onPaste)
                    ActionButton(R.string.detail_write_note, enabled, Modifier.weight(1f), callbacks.onNote)
                }
            }
        }
    }
}

@Composable
private fun ActionButton(label: Int, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    SecondaryButton(stringResource(label), onClick, modifier, enabled)
}
