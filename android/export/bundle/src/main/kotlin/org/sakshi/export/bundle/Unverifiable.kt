package org.sakshi.export.bundle

/** Plain statements of what a bundle cannot show about what it leaves out or removes (megaplan 21.3 step 5, 21.4). */
internal object Unverifiable {
    fun statements(omitted: OmittedCounts, redactions: RedactionSummary, omittedAnchors: Int): List<String> {
        val lines = mutableListOf<String>()
        if (omitted.evidenceCount > 0) lines += "${omitted.evidenceCount} saved files were left out. Their bytes cannot be checked from this bundle."
        if (omitted.derivativeCount > 0) lines += "${omitted.derivativeCount} extracted texts were left out. They cannot be checked from this bundle."
        if (omitted.eventCount > 0) lines += "${omitted.eventCount} records were left out. They cannot be checked from this bundle."
        if (omittedAnchors > 0) {
            lines += "$omittedAnchors event anchors point at saved items that are listed as not included. " +
                "The anchors are recorded, the items are not checked."
        }
        if (!redactions.isEmpty) {
            lines += "The person who made this bundle removed ${redactions.passageCount} passages from ${redactions.eventCount} records. " +
                "The bytes present and the signed record of the removal are checked. " +
                "The removed text cannot be checked, and nothing here shows that the removal was correct."
            if (redactions.originalMayHoldRemovedContent) {
                lines += "Warning: an included original file is the source of removed text and still contains it."
            }
        }
        return lines
    }
}
