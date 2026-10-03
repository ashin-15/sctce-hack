package org.sakshi.export.bundle

import org.sakshi.core.model.Event

/**
 * Resolves every evidence anchor of every event against the provenance graph (megaplan 21.3 step 3). An anchor
 * resolves when its artefact has a node and a node of the evidence reaches the event through an `anchors` edge.
 * The node says whether the bytes are in the bundle; a node marked as not included is the explicit omitted entry.
 */
internal object AnchorGraph {
    class Result(val problems: List<String>, val omittedAnchors: Int)

    fun resolve(events: List<Event>, graph: Provenance): Result {
        val nodes = graph.nodes.associateBy { it.id }
        val anchors = graph.edges.filter { it.relation == ProvenanceRelation.ANCHORS }.groupBy { it.to }
        val problems = mutableListOf<String>()
        var omitted = 0
        for (event in events) {
            val id = event.eventId.value
            val eventNodes = listOf("event:$id", id)
            val incoming = eventNodes.flatMap { anchors[it].orEmpty() }.map { it.from }.toSet()
            for (reference in event.evidenceReferences) {
                val artifact = reference.artifactId.value
                val node = nodes[artifact]
                if (node == null) {
                    problems += "event ${safe(id)} anchor ${safe(reference.referenceId.value)} names artefact ${safe(artifact)} that has no provenance entry"
                    continue
                }
                val sources = sourcesOf(artifact, graph, nodes)
                if (sources.none { it in incoming }) {
                    problems += "event ${safe(id)} anchor ${safe(reference.referenceId.value)} has no anchors edge from artefact ${safe(artifact)}"
                    continue
                }
                if (sources.none { nodes.getValue(it).included }) omitted++
            }
        }
        return Result(problems, omitted)
    }

    /** The artefact itself and any evidence node it was derived from, such as an included original. */
    private fun sourcesOf(artifact: String, graph: Provenance, nodes: Map<String, ProvenanceNode>): Set<String> {
        val parents = graph.edges
            .filter { it.relation == ProvenanceRelation.DERIVED_FROM && it.to == artifact }
            .map { it.from }
            .filter { nodes[it]?.kind == ProvenanceKind.EVIDENCE }
        return (parents + artifact).toSet()
    }
}
