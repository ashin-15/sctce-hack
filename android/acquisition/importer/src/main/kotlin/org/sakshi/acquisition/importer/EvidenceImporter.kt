package org.sakshi.acquisition.importer

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.GeneralSecurityException
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.sakshi.core.crypto.BlobTooLargeException
import org.sakshi.core.vault.AccessClass
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.EvidenceRepository
import org.sakshi.core.vault.ImportRequest
import org.sakshi.core.vault.ImportedEvidence
import org.sakshi.core.vault.NotificationClaims

/**
 * Copies chosen items into the vault. Bytes are read while the URI grant is valid and no URI is kept.
 * Nothing is saved until [commit] or [commitNote] is called.
 */
public class EvidenceImporter(
    private val evidence: EvidenceRepository,
    private val resolver: ContentResolver,
    private val limits: ImportLimits = ImportLimits(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Instant = Instant::now,
) {
    /**
     * Saves the selected items of [batch] into [caseId]. Each item is independent: one failure does not undo
     * the others. [onProgress] is called after each selected item. Cancellation propagates and leaves no
     * partial evidence for the item being copied.
     *
     * The byte limit comes from the declared kind and is chosen before reading, so a lying claim can only pick
     * another limit, never lift it. The analysis state comes from the detected MIME when the bytes were
     * recognised, and from the declared kind otherwise.
     */
    public suspend fun commit(
        caseId: String,
        batch: PendingBatch,
        selectedIndexes: Set<Int>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportReport {
        val selected = batch.items.filter { it.index in selectedIndexes }
        val outcomes = ArrayList<ItemOutcome>(selected.size)
        for (item in selected) {
            coroutineContext.ensureActive()
            outcomes += when (item) {
                is PendingItem.Rejected -> ItemOutcome.Skipped(item.index, item.reason)
                is PendingItem.Text -> saveText(caseId, batch, item)
                is PendingItem.Stream -> saveStream(caseId, batch, item)
            }
            onProgress(outcomes.size, selected.size)
        }
        return ImportReport(outcomes)
    }

    /** Saves a note written by the user. An invalid note is reported as [ItemOutcome.Skipped]. */
    public suspend fun commitNote(caseId: String, note: ManualNote): ItemOutcome {
        note.problem()?.let { return ItemOutcome.Skipped(0, it) }
        val bytes = ManualNoteCodec.encode(note, clock())
        val request = ImportRequest(
            caseId = caseId,
            acquisitionKind = AcquisitionKind.MANUAL_NOTE,
            accessClass = AccessClass.USER_MEDIATED,
            importerMechanism = ImportMechanism.MANUAL_NOTE.wireName(),
            declaredMime = ManualNoteCodec.MIME_TYPE,
            claimedOrigin = null,
            displayNameClaim = null,
            uriAuthorityClaim = null,
            maxPlaintextBytes = bytes.size.toLong(),
        )
        return store(0, caseId, request, AnalysisState.READY_FOR_TEXT_ANALYSIS) { ByteArrayInputStream(bytes) }
    }

    /**
     * Saves text the person kept from an observed notification. The original is the UTF-8 text; the access class is
     * notification observation and the claims are recorded as capture metadata under
     * [NotificationClaims.ROOT_KEY]. A summary-only excerpt ("3 new messages") is preserved but reported as
     * [AnalysisState.PRESERVED_NOT_ANALYSED], because it is not a message. Identical bytes already in the case are
     * listed in [ItemOutcome.Saved.duplicateOf], as for every other kind.
     */
    public suspend fun commitNotificationExcerpt(caseId: String, excerpt: NotificationExcerptImport): ItemOutcome {
        if (excerpt.text.isEmpty()) return ItemOutcome.Skipped(0, Rejection.EMPTY_TEXT)
        val bytes = excerpt.text.toByteArray(Charsets.UTF_8)
        if (excerpt.text.length > limits.maxTextChars) return ItemOutcome.Skipped(0, Rejection.TEXT_TOO_LONG)
        val request = ImportRequest(
            caseId = caseId,
            acquisitionKind = AcquisitionKind.NOTIFICATION_EXCERPT,
            accessClass = AccessClass.NOTIFICATION_OBSERVATION,
            importerMechanism = NOTIFICATION_MECHANISM,
            declaredMime = TEXT_MIME,
            claimedOrigin = excerpt.claims.sourceAppClaim,
            displayNameClaim = null,
            uriAuthorityClaim = null,
            maxPlaintextBytes = limits.maxBytesFor(ItemKind.TEXT),
            captureClaimsJson = excerpt.claims.toJson(),
        )
        val outcome = store(0, caseId, request, AnalysisState.READY_FOR_TEXT_ANALYSIS) { ByteArrayInputStream(bytes) }
        return if (outcome is ItemOutcome.Saved && excerpt.claims.summaryOnly) {
            outcome.copy(analysisState = AnalysisState.PRESERVED_NOT_ANALYSED)
        } else {
            outcome
        }
    }

    private suspend fun saveText(caseId: String, batch: PendingBatch, item: PendingItem.Text): ItemOutcome {
        val bytes = item.text.toByteArray(Charsets.UTF_8)
        val request = ImportRequest(
            caseId = caseId,
            acquisitionKind = if (batch.mechanism == ImportMechanism.PASTE) {
                AcquisitionKind.PASTED_TEXT
            } else {
                AcquisitionKind.SHARED_TEXT
            },
            accessClass = AccessClass.USER_MEDIATED,
            importerMechanism = batch.mechanism.wireName(),
            declaredMime = TEXT_MIME,
            claimedOrigin = batch.referrerClaim,
            displayNameClaim = null,
            uriAuthorityClaim = null,
            maxPlaintextBytes = limits.maxBytesFor(ItemKind.TEXT),
        )
        return store(item.index, caseId, request, AnalysisState.READY_FOR_TEXT_ANALYSIS) { ByteArrayInputStream(bytes) }
    }

    private suspend fun saveStream(caseId: String, batch: PendingBatch, item: PendingItem.Stream): ItemOutcome {
        val request = ImportRequest(
            caseId = caseId,
            acquisitionKind = when (batch.mechanism) {
                ImportMechanism.DOCUMENT_PICKER -> AcquisitionKind.SELECTED_DOCUMENT
                ImportMechanism.PHOTO_PICKER -> AcquisitionKind.SELECTED_VISUAL_MEDIA
                else -> AcquisitionKind.SHARED_STREAM
            },
            accessClass = AccessClass.USER_MEDIATED,
            importerMechanism = batch.mechanism.wireName(),
            declaredMime = item.declaredMime,
            claimedOrigin = batch.referrerClaim,
            displayNameClaim = item.displayNameClaim,
            uriAuthorityClaim = item.uriAuthorityClaim,
            maxPlaintextBytes = limits.maxBytesFor(item.kind),
        )
        val job = coroutineContext[Job]
        return store(item.index, caseId, request, analysisStateOf(item.kind)) {
            openStream(item.uri, job)
        }
    }

    /** The caller owns the returned stream and closes it. */
    private suspend fun openStream(uri: Uri, job: Job?): InputStream? =
        withContext(dispatcher) { openRaw(uri) }?.let { CancellableInputStream(it, job) }

    private fun openRaw(uri: Uri): InputStream? = resolver.openInputStream(uri)

    /** Opens the source with [open], imports it, and maps every failure to an [ImportFailure]. */
    private suspend fun store(
        index: Int,
        caseId: String,
        request: ImportRequest,
        declaredState: AnalysisState,
        open: suspend () -> InputStream?,
    ): ItemOutcome {
        var input: InputStream? = null
        try {
            input = open() ?: return ItemOutcome.Failed(index, ImportFailure.UNREADABLE)
            val saved = evidence.import(request, input)
            return outcomeFor(index, caseId, request, saved, declaredState)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: FileNotFoundException) {
            return ItemOutcome.Failed(index, ImportFailure.UNREADABLE)
        } catch (_: BlobTooLargeException) {
            return ItemOutcome.Failed(index, ImportFailure.TOO_LARGE)
        } catch (_: IOException) {
            return ItemOutcome.Failed(index, ImportFailure.STORAGE_ERROR)
        } catch (_: SecurityException) {
            return ItemOutcome.Failed(index, ImportFailure.ACCESS_DENIED)
        } catch (_: GeneralSecurityException) {
            return ItemOutcome.Failed(index, ImportFailure.KEY_UNAVAILABLE)
        } catch (_: IllegalArgumentException) {
            return ItemOutcome.Failed(index, ImportFailure.CASE_UNAVAILABLE)
        } catch (_: IllegalStateException) {
            return ItemOutcome.Failed(index, ImportFailure.CASE_UNAVAILABLE)
        } catch (_: RuntimeException) {
            return ItemOutcome.Failed(index, ImportFailure.STORAGE_ERROR)
        } finally {
            closeQuietly(input)
        }
    }

    private suspend fun outcomeFor(
        index: Int,
        caseId: String,
        request: ImportRequest,
        saved: ImportedEvidence,
        declaredState: AnalysisState,
    ): ItemOutcome.Saved = ItemOutcome.Saved(
        index = index,
        evidenceId = saved.id,
        sha256 = saved.sha256,
        byteSize = saved.byteSize,
        declaredMime = request.declaredMime,
        detectedMime = saved.detectedMime,
        analysisState = saved.detectedMime?.let { analysisStateOf(ProviderClaims.kindOf(it)) } ?: declaredState,
        duplicateOf = evidence.findSameBytes(caseId, saved.sha256).filter { it != saved.id },
    )

    private fun analysisStateOf(kind: ItemKind): AnalysisState = when (kind) {
        ItemKind.TEXT, ItemKind.TEXT_FILE, ItemKind.IMAGE -> AnalysisState.READY_FOR_TEXT_ANALYSIS
        ItemKind.AUDIO, ItemKind.VIDEO, ItemKind.PDF, ItemKind.ARCHIVE, ItemKind.OTHER ->
            AnalysisState.PRESERVED_NOT_ANALYSED
    }

    private fun closeQuietly(input: InputStream?) {
        try {
            input?.close()
        } catch (_: IOException) {
            // The bytes are already copied or the copy has failed; a failing close changes neither.
        }
    }

    private fun ImportMechanism.wireName(): String = name.lowercase()

    private companion object {
        const val TEXT_MIME = "text/plain; charset=utf-8"
        const val NOTIFICATION_MECHANISM = "notification_listener"
    }
}
