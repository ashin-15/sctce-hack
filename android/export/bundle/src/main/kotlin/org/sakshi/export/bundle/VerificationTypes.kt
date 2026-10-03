package org.sakshi.export.bundle

public enum class CheckStatus { PASSED, FAILED, NOT_APPLICABLE }

/** One verification step. [detail] holds paths, ids and counts only. */
public data class Check(val name: String, val status: CheckStatus, val detail: String)

public enum class Verdict { CONSISTENT, INCONSISTENT, UNREADABLE }

public data class VerificationReport(
    val verdict: Verdict,
    val signerKeyId: String?,
    val checks: List<Check>,
    val omitted: Map<String, Int>,
    val limits: List<String>,
    /**
     * Statements about what this bundle cannot show because it was left out or removed: omitted files and records,
     * anchors to items that are not included, and text the person removed. They do not change [verdict].
     */
    val unverifiable: List<String> = emptyList(),
)

/** Upper bounds applied to the untrusted bundle. */
public data class VerifierLimits(
    val maxFiles: Int = 5_000,
    val maxManifestBytes: Long = 8L * 1024 * 1024,
    val maxJsonBytes: Long = 64L * 1024 * 1024,
    val maxFileBytes: Long = 2L * 1024 * 1024 * 1024,
)
