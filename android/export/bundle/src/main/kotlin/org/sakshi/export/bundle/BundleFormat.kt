package org.sakshi.export.bundle

/** Names and fixed text of the `sakshi-bundle/1` format. */
internal object BundleFormat {
    const val FORMAT: String = "sakshi-bundle/1"
    const val EVENT_SCHEMA: String = "urn:sakshi:event:1"
    const val CLOCK_BASIS: String = "device_clock"
    const val ALGORITHM: String = "ECDSA-P256-SHA256"
    const val JCA_SIGNATURE: String = "SHA256withECDSA"

    const val MANIFEST: String = "manifest.json"
    const val SIGNATURE: String = "manifest.sig"
    const val SIGNER: String = "signer.json"
    const val EVENTS: String = "events.jsonl"
    const val FINDINGS: String = "findings.json"
    const val CORRECTIONS: String = "corrections.json"
    const val PATTERNS: String = "patterns.json"
    const val PROVENANCE: String = "provenance.json"
    const val REPORT: String = "report.pdf"
    const val README: String = "verification/README.txt"
    const val EVIDENCE_DIR: String = "evidence"
    const val DERIVATIVES_DIR: String = "derivatives"
    const val VERIFICATION_DIR: String = "verification"

    const val ROLE_EVENTS: String = "events"
    const val ROLE_FINDINGS: String = "findings"
    const val ROLE_CORRECTIONS: String = "corrections"
    const val ROLE_PATTERNS: String = "patterns"
    const val ROLE_PROVENANCE: String = "provenance"
    const val ROLE_ORIGINAL: String = "evidence_original"
    const val ROLE_DERIVATIVE: String = "derivative"
    const val ROLE_REPORT: String = "report"
    const val ROLE_README: String = "readme"

    /** Files that the manifest does not list because it signs them or is signed over them. */
    val UNLISTED: List<String> = listOf(MANIFEST, SIGNATURE, SIGNER)

    /** Files every bundle must list. */
    val REQUIRED_LISTED: List<String> = listOf(EVENTS, FINDINGS, CORRECTIONS, PATTERNS, PROVENANCE, README)

    val DIRECTORIES: Set<String> = setOf(EVIDENCE_DIR, DERIVATIVES_DIR, VERIFICATION_DIR)

    val LIMITS: List<String> = listOf(
        "hash != authenticity",
        "signature != identity",
        "integrity != truth",
        "integrity != legal admissibility",
        "device clock != trusted time",
    )

    const val CLOSING_SENTENCE: String =
        "This check compares the bundle with itself and with the signing key inside it. " +
            "It does not show that the evidence is real or who created it."
}

/** Rules for the relative paths a manifest may list. */
internal object PathRules {
    private val SEGMENT: Regex = Regex("[A-Za-z0-9._-]{1,128}")

    private val FIXED_ROLES: Map<String, String> = mapOf(
        BundleFormat.EVENTS to BundleFormat.ROLE_EVENTS,
        BundleFormat.FINDINGS to BundleFormat.ROLE_FINDINGS,
        BundleFormat.CORRECTIONS to BundleFormat.ROLE_CORRECTIONS,
        BundleFormat.PATTERNS to BundleFormat.ROLE_PATTERNS,
        BundleFormat.PROVENANCE to BundleFormat.ROLE_PROVENANCE,
        BundleFormat.REPORT to BundleFormat.ROLE_REPORT,
        BundleFormat.README to BundleFormat.ROLE_README,
    )

    fun isSegment(value: String): Boolean = SEGMENT.matches(value) && value != "." && value != ".."

    /** The role a file at [path] must have, or null when the path is not allowed in a bundle. */
    fun roleOf(path: String): String? {
        FIXED_ROLES[path]?.let { return it }
        val parts = path.split('/')
        if (parts.size != 2 || !isSegment(parts[1])) return null
        return when (parts[0]) {
            BundleFormat.EVIDENCE_DIR -> BundleFormat.ROLE_ORIGINAL
            BundleFormat.DERIVATIVES_DIR -> BundleFormat.ROLE_DERIVATIVE
            else -> null
        }
    }
}
