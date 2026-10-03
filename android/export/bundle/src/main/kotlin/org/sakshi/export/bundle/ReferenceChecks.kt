package org.sakshi.export.bundle

import org.sakshi.core.model.Event

/** Resolves every pointer inside the bundle's documents. */
internal object ReferenceChecks {
    private const val NAME: String = "references"

    /** [omittedAnchors] counts event anchors whose artefact is listed as not included; null when not evaluated. */
    class Outcome(val check: Check, val omittedAnchors: Int?)

    fun check(view: BundleView, events: List<Event>?): Outcome {
        if (events == null) return Outcome(Check(NAME, CheckStatus.FAILED, "not evaluated: events could not be read"), null)
        val documents = try {
            Documents(
                BundleDocuments.parseFindings(view.loadVerified(BundleFormat.FINDINGS)),
                BundleDocuments.parseCorrections(view.loadVerified(BundleFormat.CORRECTIONS)),
                BundleDocuments.parsePatterns(view.loadVerified(BundleFormat.PATTERNS)),
                BundleDocuments.parseProvenance(view.loadVerified(BundleFormat.PROVENANCE)),
            )
        } catch (e: BundleReadException) {
            return Outcome(Check(NAME, CheckStatus.FAILED, "documents not read: ${e.message}"), null)
        } catch (e: IllegalArgumentException) {
            return Outcome(Check(NAME, CheckStatus.FAILED, "a document is malformed: ${safe(e.message.orEmpty())}"), null)
        }
        val byKey = events.associateBy { it.eventId.value to it.revision }
        val problems = mutableListOf<String>()
        findings(documents.findings, byKey, problems)
        patterns(documents.patterns, byKey, problems)
        provenance(view, documents.provenance, problems)
        val anchors = AnchorGraph.resolve(events, documents.provenance)
        problems += anchors.problems
        val outside = outsideCount(documents.corrections, byKey, documents)
        if (problems.isNotEmpty()) return Outcome(Check(NAME, CheckStatus.FAILED, summarise(problems)), anchors.omittedAnchors)
        val check = Check(
            NAME,
            CheckStatus.PASSED,
            "${documents.findings.size} findings, ${documents.patterns.size} patterns, ${documents.corrections.size} corrections " +
                "($outside target items outside this bundle), ${documents.provenance.nodes.size} provenance nodes, " +
                "${documents.provenance.edges.size} edges, every event anchor resolved " +
                "(${anchors.omittedAnchors} to items listed as not included)",
        )
        return Outcome(check, anchors.omittedAnchors)
    }

    private class Documents(
        val findings: List<Finding>,
        val corrections: List<Correction>,
        val patterns: List<Pattern>,
        val provenance: Provenance,
    )

    private fun findings(items: List<Finding>, byKey: Map<Pair<String, Int>, Event>, problems: MutableList<String>) {
        for (f in items) {
            val id = safe(f.id)
            val event = byKey[f.eventId to f.eventRevision]
            if (event == null) {
                problems += "finding $id cites an event revision that is not in the bundle"
                continue
            }
            val known = event.evidenceReferences.map { it.referenceId.value }.toSet()
            if (f.anchorReferenceIds.isEmpty()) problems += "finding $id has no anchor"
            if (!known.containsAll(f.anchorReferenceIds)) problems += "finding $id cites an anchor its event does not have"
        }
    }

    private fun patterns(items: List<Pattern>, byKey: Map<Pair<String, Int>, Event>, problems: MutableList<String>) {
        for (p in items) {
            val id = safe(p.id)
            if (p.status == "stale") problems += "pattern $id is stale"
            if (p.supporting.isEmpty()) problems += "pattern $id has no supporting events"
            if ((p.supporting + p.context).any { (it.eventId to it.revision) !in byKey }) {
                problems += "pattern $id cites an event revision that is not in the bundle"
            }
        }
    }

    private fun provenance(view: BundleView, graph: Provenance, problems: MutableList<String>) {
        val ids = graph.nodes.map { it.id }
        if (ids.toSet().size != ids.size) problems += "duplicate provenance node id"
        val known = ids.toSet()
        for (edge in graph.edges) {
            if (edge.from !in known || edge.to !in known) {
                problems += "provenance edge ${safe(edge.from)} -> ${safe(edge.to)} names a missing node"
            }
        }
        val covered = HashSet<String>()
        for (node in graph.nodes) {
            val path = pathOf(node) ?: continue
            val entry = view.entry(path)
            if (node.included) {
                when {
                    entry == null -> problems += "included node ${safe(node.id)} has no file"
                    entry.sha256 != node.sha256 -> problems += "node ${safe(node.id)} hash differs from its file"
                    else -> covered += path
                }
            } else if (entry != null) {
                problems += "node ${safe(node.id)} is marked as not included but a file exists"
            }
        }
        for (file in view.manifest.files) {
            val role = PathRules.roleOf(file.path)
            if ((role == BundleFormat.ROLE_ORIGINAL || role == BundleFormat.ROLE_DERIVATIVE) && file.path !in covered) {
                problems += "file ${safe(file.path)} has no included provenance node"
            }
        }
    }

    private fun pathOf(node: ProvenanceNode): String? = when (node.kind) {
        ProvenanceKind.EVIDENCE -> "${BundleFormat.EVIDENCE_DIR}/${node.id}"
        ProvenanceKind.DERIVATIVE -> "${BundleFormat.DERIVATIVES_DIR}/${node.id}"
        ProvenanceKind.REPORT -> BundleFormat.REPORT
        else -> null
    }?.takeIf { PathRules.roleOf(it) != null }

    private fun outsideCount(items: List<Correction>, byKey: Map<Pair<String, Int>, Event>, documents: Documents): Int =
        items.count { c ->
            val inside = when (c.targetType) {
                "event" -> if (c.targetRevision == null) byKey.keys.any { it.first == c.targetId } else (c.targetId to c.targetRevision) in byKey
                "finding" -> documents.findings.any { it.id == c.targetId }
                "pattern" -> documents.patterns.any { it.id == c.targetId }
                else -> false
            }
            !inside
        }
}
