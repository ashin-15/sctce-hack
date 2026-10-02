package org.sakshi.acquisition.importer

/** Builds a batch from text the user pasted. */
public object TextReader {
    public fun fromPastedText(text: String, limits: ImportLimits = ImportLimits()): PendingBatch =
        PendingBatch(ImportMechanism.PASTE, listOf(textItem(0, text, limits)), referrerClaim = null)
}
