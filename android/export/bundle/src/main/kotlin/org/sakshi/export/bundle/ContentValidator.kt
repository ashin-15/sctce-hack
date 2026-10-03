package org.sakshi.export.bundle

import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import org.sakshi.core.model.ConfirmationStatus
import org.sakshi.core.model.EpistemicStatus
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventSchemaAdapter

private val HEX_64: Regex = Regex("[0-9a-f]{64}")

/** Checks the invariants the writer enforces before any byte is written. */
internal object ContentValidator {
    /** @return digests of originals and derivatives keyed by bundle path. */
    fun validate(c: BundleContent): Map<String, StreamDigest> {
        require(PathRules.isSegment(c.snapshotId)) { "snapshotId must match [A-Za-z0-9._-]{1,128}" }
        require(c.caseId.isNotEmpty() && c.caseId.length <= MAX_ID) { "caseId must be 1..$MAX_ID characters" }
        requireTimestamp(c.createdAt)
        require(HEX_64.matches(c.auditChainHead)) { "auditChainHead must be 64 lowercase hex characters" }
        require(c.events.isNotEmpty() || c.originals.isNotEmpty()) { "Selection is empty: no events and no originals" }

        val events = validateEvents(c)
        validateFindings(c, events)
        validatePatterns(c, events)
        require(c.corrections.map { it.id }.toSet().size == c.corrections.size) { "Duplicate correction id" }
        val digests = validateFiles(c)
        validateProvenance(c, digests)
        val anchors = AnchorGraph.resolve(c.events, c.provenance)
        require(anchors.problems.isEmpty()) { anchors.problems.first() }
        return digests
    }

    private fun requireTimestamp(value: String) {
        try {
            OffsetDateTime.parse(value)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("createdAt is not an RFC 3339 timestamp", e)
        }
    }

    private fun validateEvents(c: BundleContent): Map<Pair<String, Int>, Event> {
        val byKey = HashMap<Pair<String, Int>, Event>()
        for (event in c.events) {
            val key = event.eventId.value to event.revision
            require(EventSchemaAdapter.fromJson(EventSchemaAdapter.toJson(event)) == event) {
                "Event ${key.first} does not survive a schema round trip"
            }
            require(event.caseId.value == c.caseId) { "Event ${key.first} belongs to another case" }
            require(event.userConfirmation.status == ConfirmationStatus.CONFIRMED) {
                "Event ${key.first} is not user-confirmed"
            }
            require(byKey.put(key, event) == null) { "Duplicate event ${key.first} revision ${key.second}" }
        }
        return byKey
    }

    private fun validateFindings(c: BundleContent, events: Map<Pair<String, Int>, Event>) {
        require(c.findings.map { it.id }.toSet().size == c.findings.size) { "Duplicate finding id" }
        for (f in c.findings) {
            require(f.epistemicStatus == EpistemicStatus.INFERRED || f.epistemicStatus == EpistemicStatus.USER_REPORTED) {
                "Finding ${f.id} must be inferred or user_reported"
            }
            val event = requireNotNull(events[f.eventId to f.eventRevision]) {
                "Finding ${f.id} cites an event revision that is not in the bundle"
            }
            require(f.anchorReferenceIds.isNotEmpty()) { "Finding ${f.id} has no anchor" }
            val known = event.evidenceReferences.map { it.referenceId.value }.toSet()
            require(known.containsAll(f.anchorReferenceIds)) { "Finding ${f.id} cites an unknown anchor" }
        }
    }

    private fun validatePatterns(c: BundleContent, events: Map<Pair<String, Int>, Event>) {
        require(c.patterns.map { it.id }.toSet().size == c.patterns.size) { "Duplicate pattern id" }
        for (p in c.patterns) {
            require(p.status != "stale") { "Pattern ${p.id} is stale" }
            require(p.supporting.isNotEmpty()) { "Pattern ${p.id} has no supporting events" }
            for (ref in p.supporting + p.context) {
                require(events.containsKey(ref.eventId to ref.revision)) {
                    "Pattern ${p.id} cites an event revision that is not in the bundle"
                }
            }
        }
    }

    private fun validateFiles(c: BundleContent): Map<String, StreamDigest> {
        val seen = HashSet<String>()
        val digests = LinkedHashMap<String, StreamDigest>()
        val groups = listOf(BundleFormat.EVIDENCE_DIR to c.originals, BundleFormat.DERIVATIVES_DIR to c.derivatives)
        for ((dir, files) in groups) {
            for (file in files) {
                require(PathRules.isSegment(file.opaqueId)) { "Bad opaque id '${file.opaqueId.take(MAX_ID)}'" }
                require(seen.add(file.opaqueId)) { "Duplicate opaque id ${file.opaqueId}" }
                digests["$dir/${file.opaqueId}"] = digestOf(file)
            }
        }
        c.reportPdf?.let { digests[BundleFormat.REPORT] = digestOf(it) }
        return digests
    }

    private fun digestOf(file: BundleFile): StreamDigest {
        require(file.length >= 0) { "File ${file.opaqueId} has a negative length" }
        val digest = file.open().use { digestStream(it, file.length) }
        require(digest.length == file.length && !digest.exceededLimit) { "File ${file.opaqueId} does not match its declared length" }
        return digest
    }

    private fun validateProvenance(c: BundleContent, digests: Map<String, StreamDigest>) {
        val nodes = c.provenance.nodes
        require(nodes.map { it.id }.toSet().size == nodes.size) { "Duplicate provenance node id" }
        val ids = nodes.map { it.id }.toSet()
        for (edge in c.provenance.edges) {
            require(edge.from in ids && edge.to in ids) { "Provenance edge ${edge.from} -> ${edge.to} names a missing node" }
        }
        val expected = mapOf(
            ProvenanceKind.EVIDENCE to BundleFormat.EVIDENCE_DIR,
            ProvenanceKind.DERIVATIVE to BundleFormat.DERIVATIVES_DIR,
        )
        val covered = HashSet<String>()
        for (node in nodes) {
            val path = expected[node.kind]?.let { "$it/${node.id}" } ?: BundleFormat.REPORT.takeIf { node.kind == ProvenanceKind.REPORT }
            if (!node.included) {
                require(path == null || path !in digests) {
                    "Node ${node.id} is marked as not included but a file exists"
                }
                continue
            }
            if (path == null) continue
            val digest = requireNotNull(digests[path]) { "Included node ${node.id} has no file" }
            require(node.sha256 == digest.sha256) { "Node ${node.id} hash differs from its file" }
            covered += path
        }
        val uncovered = digests.keys.filter { it != BundleFormat.REPORT } - covered
        require(uncovered.isEmpty()) { "File ${uncovered.first()} has no included provenance node" }
    }

    private const val MAX_ID: Int = 128
}
