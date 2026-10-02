package org.sakshi.export.bundle

import org.sakshi.core.model.EpistemicStatus

/** Confidence of a finding; [semantics] says what [value] means. */
public data class FindingConfidence(val value: Double?, val semantics: String)

/** A suggested label for an event. Only inferred or user-reported statements are exported. */
public data class Finding(
    val id: String,
    val eventId: String,
    val eventRevision: Int,
    val label: String,
    val sourceLabel: String?,
    val basis: String,
    val epistemicStatus: EpistemicStatus,
    val confidence: FindingConfidence,
    val producerVersion: String,
    val anchorReferenceIds: List<String>,
    val reviewStatus: String,
)

/** A user decision about an earlier item. The target may lie outside the bundle. */
public data class Correction(
    val id: String,
    val targetType: String,
    val targetId: String,
    val targetRevision: Int?,
    val action: String,
    val reasonCode: String?,
    val decidedAt: String,
)

/** Reference from a pattern to one revision of an event. */
public data class PatternReference(val eventId: String, val revision: Int, val role: String)

/** A repetition or progression across several events, with its limits stated. */
public data class Pattern(
    val id: String,
    val type: String,
    val ruleVersion: String,
    val status: String,
    val evidenceView: String,
    val knowledgeCutoff: String,
    val supporting: List<PatternReference>,
    val context: List<PatternReference>,
    val limitations: List<String>,
    val observedText: String,
    val interpretationText: String?,
)

/** Kinds of node in the provenance graph. */
public enum class ProvenanceKind(internal val wire: String) {
    EVIDENCE("evidence"),
    DERIVATIVE("derivative"),
    EVENT("event"),
    FINDING("finding"),
    CORRECTION("correction"),
    PATTERN("pattern"),
    REPORT("report"),
}

/** Relations between provenance nodes. */
public enum class ProvenanceRelation(internal val wire: String) {
    DERIVED_FROM("derived_from"),
    ANCHORS("anchors"),
    SUPPORTS("supports"),
    REVIEWED_BY("reviewed_by"),
    CITED_BY("cited_by"),
}

/** A node; [included] says whether the bundle holds its bytes (evidence, derivatives and the report). */
public data class ProvenanceNode(val id: String, val kind: ProvenanceKind, val sha256: String?, val included: Boolean)

public data class ProvenanceEdge(val from: String, val to: String, val relation: ProvenanceRelation)

public data class Provenance(val nodes: List<ProvenanceNode>, val edges: List<ProvenanceEdge>)
