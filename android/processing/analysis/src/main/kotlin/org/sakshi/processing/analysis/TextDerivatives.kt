package org.sakshi.processing.analysis

/** A stored text derivative: its id and the exact text. */
public class DerivativeText(public val id: String, public val text: String)

/**
 * Everything one transcript derivative holds, ready to store. [toolId] is the speech engine version and [toolVersion]
 * is the SHA-256 of the model file the engine used.
 */
public class TranscriptDraft(
    public val text: String,
    public val toolId: String,
    public val toolVersion: String,
    public val sourceMapJson: String,
    public val qualityJson: String,
)

/** The derivative storage this module needs. [VaultTextDerivatives] is the production implementation. */
public interface TextDerivatives {
    /** The newest parsed-text derivative of the evidence, or null. */
    public suspend fun latestParsedText(evidenceId: String): DerivativeText?

    /** Stores [text] unchanged as a new parsed-text derivative of the evidence. */
    public suspend fun saveParsedText(evidenceId: String, text: String): DerivativeText

    /** The text of the derivative, or null if it does not exist. */
    public suspend fun text(derivativeId: String): String?

    /** The newest OCR derivative of the evidence, or null. */
    public suspend fun latestOcr(evidenceId: String): OcrDerivative?

    /** Stores recognised text and its image regions as a new OCR derivative of the evidence, in one step. */
    public suspend fun saveOcr(evidenceId: String, draft: OcrDraft): OcrDerivative

    /** The newest transcript derivative of the evidence, or null. */
    public suspend fun latestTranscript(evidenceId: String): DerivativeText?

    /** Stores transcribed speech as a new transcript derivative of the evidence. */
    public suspend fun saveTranscript(evidenceId: String, draft: TranscriptDraft): DerivativeText
}

/** Tool identity recorded on derivatives written by this module. */
internal object DecoderTool {
    const val ID: String = "sakshi-text-decoder"
    const val VERSION: String = "1"
}
