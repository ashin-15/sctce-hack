package org.sakshi.processing.analysis

import org.sakshi.core.vault.DerivativeKind
import org.sakshi.core.vault.DerivativeStore

/** [TextDerivatives] over the vault's [DerivativeStore]; saved text is the decoder or recogniser output, stored unchanged. */
public class VaultTextDerivatives(private val store: DerivativeStore) : TextDerivatives {
    override suspend fun latestParsedText(evidenceId: String): DerivativeText? =
        store.latest(evidenceId, DerivativeKind.PARSED_TEXT)?.let { DerivativeText(it.id, it.text) }

    override suspend fun saveParsedText(evidenceId: String, text: String): DerivativeText {
        val saved = store.save(evidenceId, DerivativeKind.PARSED_TEXT, text, DecoderTool.ID, DecoderTool.VERSION)
        return DerivativeText(saved.id, saved.text)
    }

    override suspend fun text(derivativeId: String): String? = store.get(derivativeId)?.text

    override suspend fun latestOcr(evidenceId: String): OcrDerivative? =
        store.latest(evidenceId, DerivativeKind.OCR)?.let { OcrDerivative(it.id, it.text, it.sourceMapJson) }

    override suspend fun saveOcr(evidenceId: String, draft: OcrDraft): OcrDerivative {
        val saved = store.saveWithRegions(
            evidenceId = evidenceId,
            kind = DerivativeKind.OCR,
            text = draft.text,
            toolId = draft.toolId,
            toolVersion = draft.toolVersion,
            regions = draft.regions,
            sourceMapJson = draft.sourceMapJson,
            qualityJson = draft.qualityJson,
        )
        return OcrDerivative(saved.id, saved.text, saved.sourceMapJson)
    }

    override suspend fun latestTranscript(evidenceId: String): DerivativeText? =
        store.latest(evidenceId, DerivativeKind.TRANSCRIPT)?.let { DerivativeText(it.id, it.text) }

    override suspend fun saveTranscript(evidenceId: String, draft: TranscriptDraft): DerivativeText {
        val saved = store.save(
            evidenceId = evidenceId,
            kind = DerivativeKind.TRANSCRIPT,
            text = draft.text,
            toolId = draft.toolId,
            toolVersion = draft.toolVersion,
            sourceMapJson = draft.sourceMapJson,
            qualityJson = draft.qualityJson,
        )
        return DerivativeText(saved.id, saved.text)
    }
}
