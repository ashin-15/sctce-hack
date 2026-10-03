package org.sakshi.app.capture

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.io.ByteArrayInputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import org.sakshi.acquisition.accessibility.AccessibleCapture
import org.sakshi.acquisition.accessibility.AccessibleTextCandidate
import org.sakshi.acquisition.projection.ProjectionCaptureController
import org.sakshi.acquisition.projection.ProjectionCaptureMode
import org.sakshi.acquisition.projection.ProjectionCaptureState
import org.sakshi.acquisition.projection.ProjectionDraft
import org.sakshi.app.R
import org.sakshi.app.SessionServices
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.QuietTextButton
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.theme.Spacing
import org.sakshi.core.database.CaseStatus
import org.sakshi.core.vault.AccessClass
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.ImportRequest

@Composable
fun VisibleCaptureScreen(
    services: SessionServices,
    onBack: () -> Unit,
    onAnalyse: (String, String) -> Unit,
    onOpenAccessSettings: (Intent) -> Unit,
    projection: ProjectionCaptureController,
    onRequestProjection: (ProjectionCaptureMode) -> Unit,
) {
    val context = LocalContext.current
    val capture = remember(context) { AccessibleCapture.from(context) }
    val status by capture.status.collectAsState()
    val candidates by capture.candidates.collectAsState()
    val projectionState by projection.state.collectAsState()
    val projectionDrafts by projection.drafts.collectAsState()
    val cases by remember(services) { services.vault.cases.observe() }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var caseId by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Int?>(null) }
    var previewDraftId by remember { mutableStateOf<String?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    SakshiScaffold(title = stringResource(R.string.capture_title), onBack = if (saving) null else onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SupportingText(stringResource(R.string.projection_disclosure))
            PrimaryButton(stringResource(R.string.projection_snapshot), {
                onRequestProjection(ProjectionCaptureMode.SNAPSHOT)
            }, enabled = projectionState !is ProjectionCaptureState.Capturing && projectionDrafts.isEmpty() && !saving)
            PrimaryButton(stringResource(R.string.projection_start_burst), {
                onRequestProjection(ProjectionCaptureMode.BURST)
            }, enabled = projectionState !is ProjectionCaptureState.Capturing && projectionDrafts.isEmpty() && !saving)
            when (projectionState) {
                is ProjectionCaptureState.Capturing -> SupportingText(stringResource(R.string.projection_active))
                is ProjectionCaptureState.Ready -> SupportingText(stringResource(R.string.projection_ready))
                is ProjectionCaptureState.Failed -> SupportingText(stringResource(R.string.projection_failed))
                ProjectionCaptureState.Idle -> Unit
            }
            QuietTextButton(
                stringResource(R.string.projection_stop),
                { projection.stop(context) },
                enabled = projectionState is ProjectionCaptureState.Capturing,
            )
            if (projectionDrafts.isNotEmpty()) {
                Text(stringResource(R.string.projection_review_title), style = MaterialTheme.typography.titleMedium)
                projectionDrafts.forEach { draft ->
                    ProjectionDraftCard(
                        draft = draft,
                        enabled = !saving,
                        onPreview = {
                            previewDraftId = draft.id
                            scope.launch {
                                try {
                                    val bytes = projection.readDraft(draft.id)
                                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                                    var sample = 1
                                    while (options.outWidth / sample > 720 || options.outHeight / sample > 720) sample *= 2
                                    val image = BitmapFactory.decodeByteArray(
                                        bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample },
                                    )
                                    if (image == null) notice = R.string.projection_preview_unavailable else previewBitmap = image
                                } catch (_: Exception) {
                                    notice = R.string.projection_preview_unavailable
                                }
                            }
                        },
                        onSave = {
                            val target = caseId
                            if (target != null && !saving) {
                                saving = true
                                scope.launch {
                                    try {
                                        val bytes = projection.readDraft(draft.id)
                                        val evidenceId = saveProjectionDraft(services, target, draft, bytes)
                                        projection.discardDraft(draft.id)
                                        onAnalyse(target, evidenceId)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        notice = R.string.capture_failed
                                    } finally {
                                        saving = false
                                    }
                                }
                            }
                        },
                        onDiscard = { projection.discardDraft(draft.id) },
                    )
                }
                if (caseId == null) SupportingText(stringResource(R.string.projection_need_case))
                QuietTextButton(stringResource(R.string.projection_discard), { projection.discardAll() }, enabled = !saving)
            } else if (projectionState !is ProjectionCaptureState.Capturing) {
                SupportingText(stringResource(R.string.projection_no_drafts))
            }
            SupportingText(stringResource(R.string.capture_disclosure))
            PrimaryButton(stringResource(R.string.capture_access), {
                try {
                    capture.enable()
                    onOpenAccessSettings(capture.settingsIntent())
                } catch (_: Exception) { notice = R.string.capture_failed }
            })
            Text(status.notice, style = MaterialTheme.typography.bodyMedium)
            AccessibleCapture.SUPPORTED_PACKAGES.sorted().forEach { name ->
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(checked = name in selected, onCheckedChange = { checked ->
                        selected = if (checked) selected + name else selected - name
                    }, enabled = !status.active)
                    Text(name, Modifier.weight(1f).padding(top = Spacing.sm))
                }
            }
            PrimaryButton(stringResource(R.string.capture_start), {
                notice = if (capture.startSession(selected)) R.string.capture_switch_apps else R.string.capture_not_ready
            }, enabled = selected.isNotEmpty() && !status.active && !saving)
            QuietTextButton(stringResource(R.string.capture_stop), { capture.stopSession() }, enabled = status.active)
            QuietTextButton(stringResource(R.string.capture_revoke), { capture.revoke() }, enabled = !saving)
            notice?.let { SupportingText(stringResource(it)) }
            Text(stringResource(R.string.capture_choose_case), style = MaterialTheme.typography.titleMedium)
            cases.filter { it.status != CaseStatus.ARCHIVED }.forEach { case ->
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(caseId == case.id, { if (it) caseId = case.id else caseId = null }, enabled = !saving)
                    Text(case.title, Modifier.weight(1f).padding(top = Spacing.sm))
                }
            }
            if (cases.isEmpty()) SupportingText(stringResource(R.string.capture_no_case))
            if (candidates.isEmpty()) SupportingText(stringResource(R.string.capture_empty))
            candidates.forEach { candidate ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(candidate.packageName, style = MaterialTheme.typography.labelLarge)
                    SupportingText(stringResource(R.string.capture_uncertain))
                    Text(candidate.text)
                    PrimaryButton(stringResource(R.string.capture_save_analyse), {
                        val target = caseId
                        if (target != null && !saving) {
                            saving = true
                            scope.launch {
                                try {
                                    val saved = saveVisibleSnapshot(services, target, candidate)
                                    capture.dismiss(candidate.id)
                                    onAnalyse(target, saved)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) { notice = R.string.capture_failed }
                                finally { saving = false }
                            }
                        }
                    }, enabled = caseId != null && !saving)
                    QuietTextButton(stringResource(R.string.capture_discard), { capture.dismiss(candidate.id) }, enabled = !saving)
                }
            }
        }
    }
    if (previewBitmap != null && previewDraftId != null) {
        AlertDialog(
            onDismissRequest = {
                previewBitmap?.recycle()
                previewBitmap = null
                previewDraftId = null
            },
            confirmButton = {
                OutlinedButton(onClick = {
                    previewBitmap?.recycle()
                    previewBitmap = null
                    previewDraftId = null
                }) { Text(stringResource(android.R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.projection_review_title)) },
            text = {
                Image(
                    previewBitmap!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.projection_review_title),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentScale = ContentScale.Fit,
                )
            },
        )
    }
}

@Composable
private fun ProjectionDraftCard(
    draft: ProjectionDraft,
    enabled: Boolean,
    onPreview: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.projection_draft_title, draft.frameIndex), style = MaterialTheme.typography.titleSmall)
        SupportingText(
            stringResource(
                R.string.projection_draft_details, draft.width, draft.height, draft.byteSize / 1024,
                DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(draft.observedAtMs)),
            ),
        )
        if (draft.blankOrUnavailable) SupportingText(stringResource(R.string.projection_blank_frame))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(onClick = onPreview, enabled = enabled) { Text(stringResource(R.string.projection_preview)) }
            PrimaryButton(stringResource(R.string.projection_save), onSave, enabled = enabled)
        }
        QuietTextButton(stringResource(R.string.projection_discard), onDiscard, enabled = enabled)
    }
}

private suspend fun saveProjectionDraft(
    services: SessionServices,
    caseId: String,
    draft: ProjectionDraft,
    bytes: ByteArray,
): String {
    val claims = JSONObject().put("screen_observation", JSONObject()
        .put("schema_version", 1).put("capture_mode", "media_projection")
        .put("collector_session_id", draft.sessionId).put("frame_index", draft.frameIndex)
        .put("observed_wall_time_ms", draft.observedAtMs).put("collector_elapsed_realtime_ms", draft.elapsedRealtimeMs)
        .put("image_width", draft.width).put("image_height", draft.height)
        .put("sha256", draft.sha256).put("byte_size", draft.byteSize)
        .put("capture_scope", "android_user_selected_scope_unknown")
        .put("message_send_time", "unknown").put("sender", "unknown").put("direction", "unknown")
        .put("frame_state", if (draft.blankOrUnavailable) "blank_or_unavailable" else "visible_output")).toString()
    val request = ImportRequest(
        caseId = caseId,
        acquisitionKind = AcquisitionKind.SELECTED_VISUAL_MEDIA,
        accessClass = AccessClass.USER_MEDIATED,
        importerMechanism = "media_projection",
        declaredMime = "image/png",
        claimedOrigin = "SCREEN_OBSERVATION",
        displayNameClaim = "screen_capture_" + draft.frameIndex + ".png",
        uriAuthorityClaim = null,
        maxPlaintextBytes = 16L * 1024 * 1024,
        captureClaimsJson = claims,
    )
    return ByteArrayInputStream(bytes).use { services.vault.evidence.import(request, it).id }
}

/** Saves exact text separately from the collector's claims, after explicit review. */
internal suspend fun saveVisibleSnapshot(services: SessionServices, caseId: String, candidate: AccessibleTextCandidate): String {
    val bytes = candidate.text.toByteArray(Charsets.UTF_8)
    val metadata = JSONObject().put("visible_text_snapshot", JSONObject()
        .put("source_app_claim", candidate.packageName)
        .put("observed_wall_ms", candidate.observedWallMs)
        .put("collector_elapsed_realtime_ms", candidate.collectorElapsedRealtimeMs)
        .put("collector_session_id", candidate.collectorSessionId)
        .put("sender", JSONObject.NULL).put("direction", "unknown")
        .put("message_boundaries", "unknown").put("text_status", "extraction_uncertain")).toString()
    val request = ImportRequest(
        caseId, AcquisitionKind.VISIBLE_TEXT_SNAPSHOT, AccessClass.USER_MEDIATED,
        "accessibility_visible_text", "text/plain; charset=utf-8", candidate.packageName,
        null, null, 65_536L, metadata,
    )
    return ByteArrayInputStream(bytes).use { services.vault.evidence.import(request, it).id }
}
