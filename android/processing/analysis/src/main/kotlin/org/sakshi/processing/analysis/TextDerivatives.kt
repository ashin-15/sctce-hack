package org.sakshi.processing.analysis

/** A stored text derivative: its id and the exact text. */
public class DerivativeText(public val id: String, public val text: String)

/** The derivative storage this module needs. [VaultTextDerivatives] is the production implementation. */
public interface TextDerivatives {
    /** The newest parsed-text derivative of the evidence, or null. */
    public suspend fun latestParsedText(evidenceId: String): DerivativeText?

    /** Stores [text] unchanged as a new parsed-text derivative of the evidence. */
    public suspend fun saveParsedText(evidenceId: String, text: String): DerivativeText

    /** The text of the derivative, or null if it does not exist. */
    public suspend fun text(derivativeId: String): String?
}

/** Tool identity recorded on derivatives written by this module. */
internal object DecoderTool {
    const val ID: String = "sakshi-text-decoder"
    const val VERSION: String = "1"
}
