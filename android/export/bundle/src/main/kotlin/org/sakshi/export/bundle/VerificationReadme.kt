package org.sakshi.export.bundle

/** Fixed text written to `verification/README.txt`. */
internal object VerificationReadme {
    val text: String = buildString {
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
        appendLine("  no_cross_case       No event belongs to another case.")
        appendLine("  omitted             Counts of items left out. They cannot be checked and are not listed.")
        appendLine()
        appendLine("Compare the signing key id with a value you obtained by another route before relying on it.")
        appendLine()
        appendLine("Limits:")
        BundleFormat.LIMITS.forEach { appendLine("  $it") }
        appendLine()
        appendLine(BundleFormat.CLOSING_SENTENCE)
    }
}
