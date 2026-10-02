package org.sakshi.export.bundle

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.sakshi.core.integrity.CanonicalJson
import org.sakshi.core.model.EpistemicStatus

private const val FINDINGS_SCHEMA: String = "sakshi-findings/1"
private const val CORRECTIONS_SCHEMA: String = "sakshi-corrections/1"
private const val PATTERNS_SCHEMA: String = "sakshi-patterns/1"
private const val PROVENANCE_SCHEMA: String = "sakshi-provenance/1"

private val EXPORTABLE_STATUSES: Map<String, EpistemicStatus> = mapOf(
    "inferred" to EpistemicStatus.INFERRED,
    "user_reported" to EpistemicStatus.USER_REPORTED,
)

/** Hand written canonical JSON forms of the four document files. */
internal object BundleDocuments {
    fun findings(items: List<Finding>): ByteArray = encode(FINDINGS_SCHEMA, items.map(::findingJson))

    fun corrections(items: List<Correction>): ByteArray = encode(CORRECTIONS_SCHEMA, items.map(::correctionJson))

    fun patterns(items: List<Pattern>): ByteArray = encode(PATTERNS_SCHEMA, items.map(::patternJson))

    fun provenance(value: Provenance): ByteArray = CanonicalJson.encode(
        jsonObject(
            "schema" to JsonPrimitive(PROVENANCE_SCHEMA),
            "nodes" to JsonArray(value.nodes.map(::nodeJson)),
            "edges" to JsonArray(value.edges.map(::edgeJson)),
        ),
    )

    fun parseFindings(bytes: ByteArray): List<Finding> = items(bytes, FINDINGS_SCHEMA).map(::finding)

    fun parseCorrections(bytes: ByteArray): List<Correction> = items(bytes, CORRECTIONS_SCHEMA).map(::correction)

    fun parsePatterns(bytes: ByteArray): List<Pattern> = items(bytes, PATTERNS_SCHEMA).map(::pattern)

    fun parseProvenance(bytes: ByteArray): Provenance {
        val root = root(bytes, PROVENANCE_SCHEMA)
        return Provenance(root.objects("nodes").map(::node), root.objects("edges").map(::edge))
    }

    private fun encode(schema: String, items: List<JsonElement>): ByteArray =
        CanonicalJson.encode(jsonObject("schema" to JsonPrimitive(schema), "items" to JsonArray(items)))

    private fun root(bytes: ByteArray, schema: String): JsonObject {
        val root = JsonInput.parse(String(bytes, Charsets.UTF_8)) as? JsonObject
            ?: throw IllegalArgumentException("document must be an object")
        require(root.str("schema") == schema) { "unexpected schema, expected $schema" }
        return root
    }

    private fun items(bytes: ByteArray, schema: String): List<JsonObject> = root(bytes, schema).objects("items")

    private fun findingJson(f: Finding): JsonObject = jsonObject(
        "id" to JsonPrimitive(f.id),
        "event_id" to JsonPrimitive(f.eventId),
        "event_revision" to JsonPrimitive(f.eventRevision),
        "label" to JsonPrimitive(f.label),
        "source_label" to jsonString(f.sourceLabel),
        "basis" to JsonPrimitive(f.basis),
        "epistemic_status" to JsonPrimitive(f.epistemicStatus.name.lowercase()),
        "confidence" to jsonObject(
            "value" to f.confidence.value?.let { JsonPrimitive(it) },
            "semantics" to JsonPrimitive(f.confidence.semantics),
        ),
        "producer_version" to JsonPrimitive(f.producerVersion),
        "anchor_reference_ids" to jsonStrings(f.anchorReferenceIds),
        "review_status" to JsonPrimitive(f.reviewStatus),
    )

    private fun finding(o: JsonObject): Finding {
        val status = o.str("epistemic_status")
        val confidence = o.obj("confidence")
        return Finding(
            id = o.str("id"),
            eventId = o.str("event_id"),
            eventRevision = o.int("event_revision"),
            label = o.str("label"),
            sourceLabel = o.strOrNull("source_label"),
            basis = o.str("basis"),
            epistemicStatus = EXPORTABLE_STATUSES[status]
                ?: throw IllegalArgumentException("epistemic_status must be inferred or user_reported"),
            confidence = FindingConfidence(confidence.optionalDouble("value"), confidence.str("semantics")),
            producerVersion = o.str("producer_version"),
            anchorReferenceIds = o.strings("anchor_reference_ids"),
            reviewStatus = o.str("review_status"),
        )
    }

    private fun correctionJson(c: Correction): JsonObject = jsonObject(
        "id" to JsonPrimitive(c.id),
        "target_type" to JsonPrimitive(c.targetType),
        "target_id" to JsonPrimitive(c.targetId),
        "target_revision" to c.targetRevision?.let { JsonPrimitive(it) },
        "action" to JsonPrimitive(c.action),
        "reason_code" to jsonString(c.reasonCode),
        "decided_at" to JsonPrimitive(c.decidedAt),
    )

    private fun correction(o: JsonObject): Correction = Correction(
        id = o.str("id"),
        targetType = o.str("target_type"),
        targetId = o.str("target_id"),
        targetRevision = if (o["target_revision"] == null) null else o.int("target_revision"),
        action = o.str("action"),
        reasonCode = o.strOrNull("reason_code"),
        decidedAt = o.str("decided_at"),
    )

    private fun referenceJson(r: PatternReference): JsonObject = jsonObject(
        "event_id" to JsonPrimitive(r.eventId),
        "revision" to JsonPrimitive(r.revision),
        "role" to JsonPrimitive(r.role),
    )

    private fun reference(o: JsonObject): PatternReference =
        PatternReference(o.str("event_id"), o.int("revision"), o.str("role"))

    private fun patternJson(p: Pattern): JsonObject = jsonObject(
        "id" to JsonPrimitive(p.id),
        "type" to JsonPrimitive(p.type),
        "rule_version" to JsonPrimitive(p.ruleVersion),
        "status" to JsonPrimitive(p.status),
        "evidence_view" to JsonPrimitive(p.evidenceView),
        "knowledge_cutoff" to JsonPrimitive(p.knowledgeCutoff),
        "supporting" to JsonArray(p.supporting.map(::referenceJson)),
        "context" to JsonArray(p.context.map(::referenceJson)),
        "limitations" to jsonStrings(p.limitations),
        "observed_text" to JsonPrimitive(p.observedText),
        "interpretation_text" to jsonString(p.interpretationText),
    )

    private fun pattern(o: JsonObject): Pattern = Pattern(
        id = o.str("id"),
        type = o.str("type"),
        ruleVersion = o.str("rule_version"),
        status = o.str("status"),
        evidenceView = o.str("evidence_view"),
        knowledgeCutoff = o.str("knowledge_cutoff"),
        supporting = o.objects("supporting").map(::reference),
        context = o.objects("context").map(::reference),
        limitations = o.strings("limitations"),
        observedText = o.str("observed_text"),
        interpretationText = o.strOrNull("interpretation_text"),
    )

    private fun nodeJson(n: ProvenanceNode): JsonObject = jsonObject(
        "id" to JsonPrimitive(n.id),
        "kind" to JsonPrimitive(n.kind.wire),
        "sha256" to jsonString(n.sha256),
        "included" to JsonPrimitive(n.included),
    )

    private fun node(o: JsonObject): ProvenanceNode {
        val kind = o.str("kind")
        return ProvenanceNode(
            id = o.str("id"),
            kind = ProvenanceKind.entries.firstOrNull { it.wire == kind }
                ?: throw IllegalArgumentException("unknown provenance kind"),
            sha256 = o.strOrNull("sha256"),
            included = o.bool("included"),
        )
    }

    private fun edgeJson(e: ProvenanceEdge): JsonObject = jsonObject(
        "from" to JsonPrimitive(e.from),
        "to" to JsonPrimitive(e.to),
        "relation" to JsonPrimitive(e.relation.wire),
    )

    private fun edge(o: JsonObject): ProvenanceEdge {
        val relation = o.str("relation")
        return ProvenanceEdge(
            from = o.str("from"),
            to = o.str("to"),
            relation = ProvenanceRelation.entries.firstOrNull { it.wire == relation }
                ?: throw IllegalArgumentException("unknown provenance relation"),
        )
    }
}
