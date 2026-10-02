package org.sakshi.export.bundle

import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.sakshi.core.integrity.CanonicalJson
import org.sakshi.core.integrity.MerkleV2
import org.sakshi.core.integrity.Sha256
import org.sakshi.core.model.EpistemicStatus
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventSchemaAdapter

const val CASE_ID: String = "synthetic-case-1"
const val ORIGINAL_ID: String = "synthetic-original-1"
const val DERIVATIVE_ID: String = "synthetic-derivative-1"
const val ORIGINAL_LENGTH: Long = 3L * 1024 * 1024 + 17
const val ANCHOR: String = "demo-ref-schema-1"

/** Deterministic bytes generated on the fly, so large inputs never sit in memory. */
class PatternStream(private val length: Long, private val seed: Int) : InputStream() {
    private var position = 0L

    override fun read(): Int {
        if (position >= length) return -1
        return ((position++ * 31 + seed) and 0xff).toInt()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (position >= length) return -1
        val count = minOf(len.toLong(), length - position).toInt()
        for (i in 0 until count) b[off + i] = ((position++ * 31 + seed) and 0xff).toByte()
        return count
    }
}

fun patternFile(id: String, length: Long, seed: Int): BundleFile = BundleFile(id, length) { PatternStream(length, seed) }

fun patternHash(length: Long, seed: Int): String = digestStream(PatternStream(length, seed)).sha256

fun fixtureText(name: String): String =
    Files.readString(Path.of(requireNotNull(System.getProperty("sakshi.fixtures")) { "sakshi.fixtures not set" }).resolve(name))

/** A valid synthetic event derived from the shared fixture by changing ids with JSON manipulation. */
fun event(id: String, revision: Int = 1, caseId: String = CASE_ID, confirmation: String = "confirmed"): Event {
    val root = Json.parseToJsonElement(fixtureText("event-valid.json")) as JsonObject
    val confirmationObject = root["user_confirmation"] as JsonObject
    val changed = if (confirmation == "confirmed") {
        confirmationObject
    } else {
        JsonObject(
            confirmationObject + mapOf(
                "status" to JsonPrimitive(confirmation),
                "reviewed_at" to JsonNull,
                "scope" to JsonPrimitive("not_reviewed"),
            ),
        )
    }
    val edited = JsonObject(
        root + mapOf(
            "event_id" to JsonPrimitive(id),
            "case_id" to JsonPrimitive(caseId),
            "revision" to JsonPrimitive(revision),
            "user_confirmation" to changed,
        ),
    )
    return EventSchemaAdapter.fromJson(edited.toString())
}

fun sampleFinding(eventId: String = "synthetic-event-1", revision: Int = 1): Finding = Finding(
    id = "synthetic-finding-1",
    eventId = eventId,
    eventRevision = revision,
    label = "synthetic-label",
    sourceLabel = null,
    basis = "rule",
    epistemicStatus = EpistemicStatus.INFERRED,
    confidence = FindingConfidence(0.7, "uncalibrated_bounded_score"),
    producerVersion = "synthetic-rules-v1",
    anchorReferenceIds = listOf(ANCHOR),
    reviewStatus = "accepted",
)

fun samplePattern(status: String = "current", revision: Int = 1): Pattern = Pattern(
    id = "synthetic-pattern-1",
    type = "repetition",
    ruleVersion = "synthetic-rule-v1",
    status = status,
    evidenceView = "confirmed_only",
    knowledgeCutoff = "2026-10-01T10:00:00+05:30",
    supporting = listOf(PatternReference("synthetic-event-1", 1, "member"), PatternReference("synthetic-event-2", revision, "member")),
    context = emptyList(),
    limitations = listOf("Two events only."),
    observedText = "Two confirmed events carry the same label.",
    interpretationText = null,
)

fun sampleProvenance(): Provenance = Provenance(
    nodes = listOf(
        ProvenanceNode(ORIGINAL_ID, ProvenanceKind.EVIDENCE, patternHash(ORIGINAL_LENGTH, 1), true),
        ProvenanceNode(DERIVATIVE_ID, ProvenanceKind.DERIVATIVE, patternHash(2048, 2), true),
        ProvenanceNode("synthetic-event-1", ProvenanceKind.EVENT, null, false),
        ProvenanceNode("synthetic-event-2", ProvenanceKind.EVENT, null, false),
        ProvenanceNode("synthetic-finding-1", ProvenanceKind.FINDING, null, false),
        ProvenanceNode("synthetic-report-1", ProvenanceKind.REPORT, patternHash(512, 3), true),
    ),
    edges = listOf(
        ProvenanceEdge(DERIVATIVE_ID, ORIGINAL_ID, ProvenanceRelation.DERIVED_FROM),
        ProvenanceEdge(ORIGINAL_ID, "synthetic-event-1", ProvenanceRelation.ANCHORS),
        ProvenanceEdge("synthetic-event-1", "synthetic-finding-1", ProvenanceRelation.SUPPORTS),
    ),
)

fun sampleContent(
    events: List<Event> = listOf(event("synthetic-event-1"), event("synthetic-event-2")),
    findings: List<Finding> = listOf(sampleFinding()),
    patterns: List<Pattern> = listOf(samplePattern()),
    provenance: Provenance = sampleProvenance(),
    originals: List<BundleFile> = listOf(patternFile(ORIGINAL_ID, ORIGINAL_LENGTH, 1)),
    derivatives: List<BundleFile> = listOf(patternFile(DERIVATIVE_ID, 2048, 2)),
    report: BundleFile? = patternFile("report", 512, 3),
): BundleContent = BundleContent(
    snapshotId = "synthetic-snapshot-1",
    caseId = CASE_ID,
    createdAt = "2026-10-02T10:00:00+05:30",
    generator = GeneratorInfo("synthetic-app-1", listOf("synthetic-rule-v1"), listOf("synthetic-model-v1")),
    auditChainHead = "ab".repeat(32),
    omitted = OmittedCounts(evidenceCount = 2, derivativeCount = 1, eventCount = 3),
    events = events,
    findings = findings,
    corrections = listOf(
        Correction("synthetic-correction-1", "event", "synthetic-omitted-event", 1, "reject", "not_relevant", "2026-10-01T12:00:00+05:30"),
    ),
    patterns = patterns,
    provenance = provenance,
    originals = originals,
    derivatives = derivatives,
    reportPdf = report,
)

fun check(report: VerificationReport, name: String): Check = report.checks.single { it.name == name }

/** Rewrites manifest, signature and signer.json after a test changed files, as an attacker with a key could. */
internal object Resigner {
    fun resign(
        dir: Path,
        signer: ManifestSigner,
        fixMerkle: Boolean = true,
        pretty: Boolean = false,
        edit: (Manifest) -> Manifest = { it },
    ) {
        val old = Manifest.parse(JsonInput.parse(Files.readString(dir.resolve("manifest.json"))))
        val refreshed = old.files.map { file ->
            val path = dir.resolve(file.path)
            if (PathRules.roleOf(file.path) != null && Files.isRegularFile(path)) {
                val bytes = Files.readAllBytes(path)
                file.copy(sha256 = Sha256.hex(Sha256.digest(bytes)), size = bytes.size.toLong())
            } else {
                file
            }
        }
        var manifest = edit(old.copy(files = refreshed))
        if (fixMerkle) {
            val root = MerkleV2.root(manifest.files.map { Sha256.fromHex(it.sha256) })
            manifest = manifest.copy(merkleRoot = Sha256.hex(root))
        }
        val bytes = if (pretty) {
            Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), manifest.toJson()).toByteArray()
        } else {
            CanonicalJson.encode(manifest.toJson())
        }
        val spki = signer.publicKeySpkiDer
        Files.write(dir.resolve("manifest.json"), bytes)
        Files.write(dir.resolve("manifest.sig"), signer.sign(bytes))
        Files.write(dir.resolve("signer.json"), SignerFile.encode(spki, Sha256.hex(Sha256.digest(spki))))
    }
}

fun flipByte(path: Path, offset: Long = 0) {
    RandomAccessFile(path.toFile(), "rw").use {
        it.seek(offset)
        val value = it.read()
        it.seek(offset)
        it.write(value xor 0x01)
    }
}
