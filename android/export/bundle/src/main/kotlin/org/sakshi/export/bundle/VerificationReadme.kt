package org.sakshi.export.bundle

/** Fixed text written to `verification/README.txt`. */
internal object VerificationReadme {
    /** The notes for a bundle with nothing removed. */
    val text: String = text(RedactionSummary.NONE)

    /** The notes for a bundle whose manifest records [redactions]. Without redactions this is [text]. */
    fun text(redactions: RedactionSummary): String = buildString {
        appendLine("Sakshi evidence bundle - verification notes")
        appendLine()
        appendLine("How to run the verifier (offline, needs only a Java 21 runtime):")
        appendLine("  verify <path to this bundle directory>")
        appendLine("The verifier is the class org.sakshi.export.bundle.VerifierCli in the :export:bundle module.")
        appendLine("Exit code 0 means consistent, 1 means inconsistent, 2 means unreadable or bad usage.")
        appendLine()
        appendLine("What each check means:")
        appendLine("  layout              Only the expected files are present, no links, paths follow the rules.")
        appendLine("  manifest_canonical  manifest.json is byte-for-byte the canonical form of its own content.")
        appendLine("  signature           manifest.sig was made by the key in signer.json over manifest.json.")
        appendLine("                      This shows which key signed, not who holds that key.")
        appendLine("  file_hashes         Every listed file has the listed size and SHA-256.")
        appendLine("  merkle_root         The listed hashes combine to the root named in the manifest.")
        appendLine("  events              Every line of events.jsonl is a schema-valid, user-confirmed event of this case.")
        appendLine("  references          Findings, patterns, corrections and provenance point at items that exist.")
        appendLine("                      Every event anchor resolves to a file in this bundle or to an entry marked")
        appendLine("                      as not included.")
        appendLine("  no_cross_case       No event belongs to another case.")
        appendLine("  omitted             Counts of items left out. They cannot be checked and are not listed.")
        appendLine()
        appendLine("The verifier also lists what it cannot check because it was left out or removed.")
        appendLine("File names under evidence/ and derivatives/ are random identifiers, not the names the files had.")
        appendLine()
        appendLine("An included original in evidence/ is the exact saved file, byte for byte. It may carry details inside")
        appendLine("the file itself, such as camera, device or location metadata, that nothing else in this bundle lists.")
        appendLine("Look at such files before you share the bundle.")
        if (!redactions.isEmpty) {
            appendLine()
            appendLine("Removed text:")
            appendLine("  The person who made this bundle removed ${redactions.passageCount} passages from the quoted text of")
            appendLine("  ${redactions.eventCount} records. Each one is replaced by the marker ${Redactor.MARKER} in the report")
            appendLine("  and in derivatives/. The removed text and a hash of it are not in this bundle.")
            appendLine("  The verifier checks the bytes that are present and the signed record that text was removed.")
            appendLine("  It cannot check the removed text, and it cannot show that the removal was correct.")
            if (redactions.originalMayHoldRemovedContent) {
                appendLine("  Warning: an original file included in evidence/ is the source of removed text and still")
                appendLine("  contains it, because originals are stored byte for byte.")
            }
        }
        appendLine()
        appendLine("Compare the signing key id with a value you obtained by another route before relying on it.")
        appendLine()
        appendLine("Limits:")
        BundleFormat.LIMITS.forEach { appendLine("  $it") }
        appendLine()
        appendLine(BundleFormat.CLOSING_SENTENCE)
    }
}
