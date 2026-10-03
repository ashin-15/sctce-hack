package org.sakshi.export.report

import org.sakshi.core.integrity.Sha256
import org.sakshi.core.model.Event
import org.sakshi.core.model.Representation
import org.sakshi.export.bundle.Finding
import org.sakshi.export.bundle.Pattern
import org.sakshi.export.bundle.Provenance
import org.sakshi.export.bundle.ProvenanceEdge
import org.sakshi.export.bundle.ProvenanceKind
import org.sakshi.export.bundle.ProvenanceNode
import org.sakshi.export.bundle.ProvenanceRelation

/** An original that goes into the bundle: the stored evidence id, the file id used in the bundle, its hash. */
internal class IncludedOriginal(val evidenceId: String, val opaqueId: String, val sha256: String)

/**
 * Builds the provenance graph evidence -> derivative -> event -> finding -> pattern -> report. Only included
 * originals and the report carry a hash; omitted items appear without one (megaplan 21.5).
 */
internal object ProvenanceAssembler {
    const val REPORT_NODE: String = "report"

    fun assemble(
        events: List<Event>,
        findings: List<Finding>,
        patterns: List<Pattern>,
        originals: List<IncludedOriginal>,
        reportSha256: String,
        redactedCopies: List<RedactedCopy> = emptyList(),
    ): Provenance {
        val nodes = LinkedHashMap<String, ProvenanceNode>()
        val edges = LinkedHashSet<ProvenanceEdge>()
        val included = originals.associateBy { it.evidenceId }
        for (original in originals) {
            nodes[original.opaqueId] = ProvenanceNode(original.opaqueId, ProvenanceKind.EVIDENCE, original.sha256, true)
        }
        for (event in events) {
            val eventNode = "event:${event.eventId.value}"
            nodes[eventNode] = ProvenanceNode(eventNode, ProvenanceKind.EVENT, null, false)
            edges += ProvenanceEdge(eventNode, REPORT_NODE, ProvenanceRelation.CITED_BY)
            for (reference in event.evidenceReferences) {
                val artifact = reference.artifactId.value
                val original = included[artifact]
                val source = original?.opaqueId ?: artifact
                if (source !in nodes) nodes[source] = ProvenanceNode(source, kindOf(reference.representation), null, false)
                if (original != null && original.opaqueId != artifact) {
                    if (artifact !in nodes) nodes[artifact] = ProvenanceNode(artifact, ProvenanceKind.EVIDENCE, null, false)
                    edges += ProvenanceEdge(original.opaqueId, artifact, ProvenanceRelation.DERIVED_FROM)
                }
                edges += ProvenanceEdge(source, eventNode, ProvenanceRelation.ANCHORS)
            }
        }
        for (finding in findings) {
            val node = "finding:${finding.id}"
            nodes[node] = ProvenanceNode(node, ProvenanceKind.FINDING, null, false)
            edges += ProvenanceEdge("event:${finding.eventId}", node, ProvenanceRelation.SUPPORTS)
            edges += ProvenanceEdge(node, REPORT_NODE, ProvenanceRelation.CITED_BY)
        }
        for (pattern in patterns) {
            val node = "pattern:${pattern.id}"
            nodes[node] = ProvenanceNode(node, ProvenanceKind.PATTERN, null, false)
            pattern.supporting.map { it.eventId }.distinct().forEach {
                edges += ProvenanceEdge("event:$it", node, ProvenanceRelation.SUPPORTS)
            }
            edges += ProvenanceEdge(node, REPORT_NODE, ProvenanceRelation.CITED_BY)
        }
        for (copy in redactedCopies) {
            val bytes = copy.text.toByteArray(Charsets.UTF_8)
            nodes[copy.opaqueId] = ProvenanceNode(copy.opaqueId, ProvenanceKind.DERIVATIVE, Sha256.hex(Sha256.digest(bytes)), true)
            val source = included[copy.artifactId]?.opaqueId ?: copy.artifactId
            if (source !in nodes) nodes[source] = ProvenanceNode(source, ProvenanceKind.EVIDENCE, null, false)
            edges += ProvenanceEdge(copy.opaqueId, source, ProvenanceRelation.DERIVED_FROM)
        }
        nodes[REPORT_NODE] = ProvenanceNode(REPORT_NODE, ProvenanceKind.REPORT, reportSha256, true)
        return Provenance(nodes.values.toList(), edges.toList())
    }

    private fun kindOf(representation: Representation): ProvenanceKind = when (representation) {
        Representation.OCR_DERIVATIVE, Representation.TRANSCRIPT_DERIVATIVE -> ProvenanceKind.DERIVATIVE
        Representation.PRESERVED_IMPORT, Representation.NOTIFICATION_EXCERPT, Representation.MANUAL_STATEMENT ->
            ProvenanceKind.EVIDENCE
    }
}
