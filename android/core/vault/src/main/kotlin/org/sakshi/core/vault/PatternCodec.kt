package org.sakshi.core.vault

import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.sakshi.core.database.PatternEntity
import org.sakshi.core.database.PatternSupportEntity
import org.sakshi.core.integrity.CanonicalJson
import org.sakshi.core.integrity.Sha256
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.EventId
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.ScopeId
import org.sakshi.core.temporal.ActorScope
import org.sakshi.core.temporal.CountBounds
import org.sakshi.core.temporal.EventRef
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.temporal.Limitation
import org.sakshi.core.temporal.Measurements
import org.sakshi.core.temporal.PatternRecord
import org.sakshi.core.temporal.PatternType
import org.sakshi.core.temporal.SupportRole

/** A pattern record turned into the rows that store it, with the content id the rows are stored under. */
internal class EncodedPattern(val entity: PatternEntity, val support: List<PatternSupportEntity>) {
    val id: String get() = entity.id
}

/**
 * Maps a [PatternRecord] to the `pattern` and `pattern_support` columns and back, without a schema change.
 *
 * - `actor_scope` holds the scope as a small JSON object (kind plus ids and the source label), so the scope's
 *   display label survives.
 * - `measurements_json` holds an envelope: the pattern key, the gap ids, the engine's assessment status (the column
 *   `assessment_status` is overwritten by `stale`, so the engine's value is kept here) and the typed measurements.
 * - `pattern_support.role` is `"supporting:<ROLE>"` or `"context:<ROLE>"`, so both the kind and the engine's
 *   [SupportRole] are kept; row order is the engine's event order.
 *
 * The content id is the SHA-256 of the canonical JSON (RFC 8785) of everything the description rests on: case,
 * view, key, rule version, scope, window, clock bases, every event reference, measurements, limitations, gap ids
 * and the engine's status. The knowledge cutoff, the generation time and the interpretation sentence are left out,
 * so recomputing the same description later gives the same id.
 */
internal object PatternCodec {
    private const val FORMAT: Int = 1
    private const val ROLE_SEPARATOR: Char = ':'
    private const val KIND_SUPPORTING: String = "supporting"
    private const val KIND_CONTEXT: String = "context"

    fun encode(record: PatternRecord, interpretation: String?, generatedAt: Instant): EncodedPattern {
        val measurements = envelope(record)
        val limitations = limitationsJson(record)
        val id = Sha256.hex(Sha256.digest(CanonicalJson.encode(content(record, measurements, limitations))))
        val entity = PatternEntity(
            id = id,
            caseId = record.caseId.value,
            type = enumName(record.type),
            ruleVersion = record.ruleVersion,
            actorScope = scopeJson(record.actorScope).toString(),
            evidenceView = viewName(record.view),
            knowledgeCutoff = record.knowledgeCutoff.toString(),
            windowStart = record.windowStart?.toString(),
            windowEnd = record.windowEnd?.toString(),
            clockBasis = clockBases(record),
            measurementsJson = measurements.toString(),
            interpretationText = interpretation,
            limitationsJson = limitations.toString(),
            assessmentStatus = enumName(record.status),
            generatedAt = generatedAt.toString(),
        )
        val support = record.supportingEvents.map { it.row(id, KIND_SUPPORTING) } +
            record.contextEvents.map { it.row(id, KIND_CONTEXT) }
        return EncodedPattern(entity, support)
    }

    fun id(record: PatternRecord): String = encode(record, null, Instant.EPOCH).id

    /**
     * @throws IllegalStateException when a stored value is not one this version knows.
     * @throws IllegalArgumentException when the stored JSON or a stored value is malformed.
     * @throws NoSuchElementException when a stored JSON field is missing.
     * @throws java.time.DateTimeException when a stored time or duration is malformed.
     */
    fun decode(entity: PatternEntity, support: List<PatternSupportEntity>): PatternRecord {
        val envelope = Json.parseToJsonElement(entity.measurementsJson).jsonObject
        val refs = support.map { it.ref() }
        return PatternRecord(
            patternKey = envelope.string("pattern_key"),
            type = parseEnum(entity.type),
            ruleVersion = entity.ruleVersion,
            caseId = CaseId(entity.caseId),
            actorScope = scope(Json.parseToJsonElement(checkNotNull(entity.actorScope) { "Stored scope is missing" }).jsonObject),
            view = parseView(entity.evidenceView),
            knowledgeCutoff = Instant.parse(entity.knowledgeCutoff),
            windowStart = entity.windowStart?.let(Instant::parse),
            windowEnd = entity.windowEnd?.let(Instant::parse),
            clockBases = if (entity.clockBasis.isEmpty()) emptySet() else entity.clockBasis.split(',').map { Codecs.timeBasis.parse(it) }.toSet(),
            supportingEvents = refs.filter { it.first == KIND_SUPPORTING }.map { it.second },
            contextEvents = refs.filter { it.first == KIND_CONTEXT }.map { it.second },
            measurements = measurements(envelope.getValue("measurements").jsonObject),
            limitations = Json.parseToJsonElement(entity.limitationsJson).jsonArray.map { parseEnum<Limitation>(it.jsonPrimitive.content) }.toSet(),
            gapIds = envelope.getValue("gap_ids").jsonArray.map { ReferenceId(it.jsonPrimitive.content) },
            status = parseEnum(envelope.string("assessment_status")),
        )
    }

    private fun content(record: PatternRecord, measurements: JsonObject, limitations: JsonArray): JsonObject = buildJsonObject {
        put("format", FORMAT)
        put("case_id", record.caseId.value)
        put("view", viewName(record.view))
        put("pattern_key", record.patternKey)
        put("type", enumName(record.type))
        put("rule_version", record.ruleVersion)
        put("scope", scopeJson(record.actorScope))
        put("window_start", record.windowStart?.toString())
        put("window_end", record.windowEnd?.toString())
        put("clock_bases", clockBases(record))
        put("supporting", refsJson(record.supportingEvents))
        put("context", refsJson(record.contextEvents))
        put("measurements", measurements)
        put("limitations", limitations)
        put("status", enumName(record.status))
    }

    private fun refsJson(refs: List<EventRef>): JsonArray = buildJsonArray {
        refs.forEach {
            add(buildJsonObject {
                put("event_id", it.eventId.value)
                put("revision", it.revision)
                put("role", enumName(it.role))
            })
        }
    }

    private fun envelope(record: PatternRecord): JsonObject = buildJsonObject {
        put("format", FORMAT)
        put("pattern_key", record.patternKey)
        put("assessment_status", enumName(record.status))
        put("gap_ids", buildJsonArray { record.gapIds.forEach { add(JsonPrimitive(it.value)) } })
        put("measurements", measurementsJson(record.measurements))
    }

    private fun limitationsJson(record: PatternRecord): JsonArray =
        buildJsonArray { record.limitations.sortedBy { it.ordinal }.forEach { add(JsonPrimitive(enumName(it))) } }

    private fun clockBases(record: PatternRecord): String =
        record.clockBases.sortedBy { it.ordinal }.joinToString(",") { Codecs.timeBasis.name(it) }

    private fun EventRef.row(patternId: String, kind: String) =
        PatternSupportEntity(patternId, eventId.value, revision, "$kind$ROLE_SEPARATOR${enumName(role)}")

    private fun PatternSupportEntity.ref(): Pair<String, EventRef> {
        val kind = role.substringBefore(ROLE_SEPARATOR)
        check(kind == KIND_SUPPORTING || kind == KIND_CONTEXT) { "Stored support kind is not known" }
        return kind to EventRef(EventId(eventId), eventRevision, parseEnum(role.substringAfter(ROLE_SEPARATOR)))
    }

    private fun scopeJson(scope: ActorScope): JsonObject = buildJsonObject {
        when (scope) {
            is ActorScope.Confirmed -> {
                put("kind", "confirmed")
                put("actor_id", scope.actorId.value)
            }
            is ActorScope.Unresolved -> {
                put("kind", "unresolved")
                put("source_app", scope.sourceApp)
                put("conversation_scope_id", scope.conversationScopeId?.value)
                put("display_label", scope.displayLabel)
            }
        }
    }

    private fun scope(json: JsonObject): ActorScope = when (val kind = json.string("kind")) {
        "confirmed" -> ActorScope.Confirmed(ActorId(json.string("actor_id")))
        "unresolved" -> ActorScope.Unresolved(
            json.stringOrNull("source_app"),
            json.stringOrNull("conversation_scope_id")?.let(::ScopeId),
            json.stringOrNull("display_label"),
        )
        else -> error("Stored scope kind $kind is not known")
    }

    private fun measurementsJson(m: Measurements): JsonObject = buildJsonObject {
        when (m) {
            is Measurements.RepeatedContact -> {
                put("kind", PatternType.REPEATED_CONTACT.name)
                put("total", boundsJson(m.total))
                put("timed_contacts", m.timedContacts)
                put("first_at", m.firstAt?.toString())
                put("last_at", m.lastAt?.toString())
                put("span", m.span?.toString())
                put("unique_days", m.uniqueDays)
                put("episodes", m.episodes)
                put(
                    "window_maxima",
                    buildJsonArray {
                        m.windowMaxima.forEach {
                            add(buildJsonObject {
                                put("size", it.size.toString())
                                put("bounds", boundsJson(it.bounds))
                            })
                        }
                    },
                )
                put("marked_unwanted", m.markedUnwanted)
                put("marked_wanted", m.markedWanted)
                put("all_marked_unwanted", m.allMarkedUnwanted)
            }
            is Measurements.RecurrenceAfterBoundary -> {
                put("kind", PatternType.RECURRENCE_AFTER_BOUNDARY.name)
                put("after_boundary", boundsJson(m.afterBoundary))
                put("episodes", m.episodes)
                put("marker", Codecs.boundaryMarker.name(m.marker))
                put("communication", Codecs.communicationStatus.name(m.communication))
                put("boundary_at", m.boundaryAt?.toString())
                put("first_counted_at", m.firstCountedAt?.toString())
                put("last_counted_at", m.lastCountedAt?.toString())
                put("ended_by_resumption", m.endedByResumption)
                put("all_marked_unwanted", m.allMarkedUnwanted)
            }
            is Measurements.WordingTransition -> {
                put("kind", PatternType.WORDING_TRANSITION.name)
                put("earlier", Codecs.categoryLabel.name(m.earlier))
                put("later", Codecs.categoryLabel.name(m.later))
                put("earlier_at", m.earlierAt.toString())
                put("later_at", m.laterAt.toString())
                put("gap", m.gap.toString())
            }
            is Measurements.DensityChange -> {
                put("kind", PatternType.DENSITY_CHANGE.name)
                put("previous", boundsJson(m.previous))
                put("current", boundsJson(m.current))
                put("previous_bin_start", m.previousBinStart.toString())
                put("current_bin_start", m.currentBinStart.toString())
                put("bin_size", m.binSize.toString())
            }
        }
    }

    private fun measurements(json: JsonObject): Measurements = when (val kind = json.string("kind")) {
        PatternType.REPEATED_CONTACT.name -> Measurements.RepeatedContact(
            total = bounds(json.getValue("total")),
            timedContacts = json.int("timed_contacts"),
            firstAt = json.stringOrNull("first_at")?.let(Instant::parse),
            lastAt = json.stringOrNull("last_at")?.let(Instant::parse),
            span = json.stringOrNull("span")?.let(Duration::parse),
            uniqueDays = json.int("unique_days"),
            episodes = json.int("episodes"),
            windowMaxima = json.getValue("window_maxima").jsonArray.map {
                val entry = it.jsonObject
                Measurements.WindowMaximum(Duration.parse(entry.string("size")), bounds(entry.getValue("bounds")))
            },
            markedUnwanted = json.int("marked_unwanted"),
            markedWanted = json.int("marked_wanted"),
            allMarkedUnwanted = json.getValue("all_marked_unwanted").jsonPrimitive.boolean,
        )
        PatternType.RECURRENCE_AFTER_BOUNDARY.name -> Measurements.RecurrenceAfterBoundary(
            afterBoundary = bounds(json.getValue("after_boundary")),
            episodes = json.int("episodes"),
            marker = Codecs.boundaryMarker.parse(json.string("marker")),
            communication = Codecs.communicationStatus.parse(json.string("communication")),
            boundaryAt = json.stringOrNull("boundary_at")?.let(Instant::parse),
            firstCountedAt = json.stringOrNull("first_counted_at")?.let(Instant::parse),
            lastCountedAt = json.stringOrNull("last_counted_at")?.let(Instant::parse),
            endedByResumption = json.getValue("ended_by_resumption").jsonPrimitive.boolean,
            allMarkedUnwanted = json.getValue("all_marked_unwanted").jsonPrimitive.boolean,
        )
        PatternType.WORDING_TRANSITION.name -> Measurements.WordingTransition(
            earlier = Codecs.categoryLabel.parse(json.string("earlier")),
            later = Codecs.categoryLabel.parse(json.string("later")),
            earlierAt = Instant.parse(json.string("earlier_at")),
            laterAt = Instant.parse(json.string("later_at")),
            gap = Duration.parse(json.string("gap")),
        )
        PatternType.DENSITY_CHANGE.name -> Measurements.DensityChange(
            previous = bounds(json.getValue("previous")),
            current = bounds(json.getValue("current")),
            previousBinStart = Instant.parse(json.string("previous_bin_start")),
            currentBinStart = Instant.parse(json.string("current_bin_start")),
            binSize = Duration.parse(json.string("bin_size")),
        )
        else -> error("Stored measurements kind $kind is not known")
    }

    private fun boundsJson(bounds: CountBounds): JsonObject = buildJsonObject {
        put("lower", bounds.lower)
        put("upper", bounds.upper)
    }

    private fun bounds(element: JsonElement): CountBounds =
        element.jsonObject.let { CountBounds(it.int("lower"), it.int("upper")) }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.stringOrNull(key: String): String? =
        getValue(key).let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }

    private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

    fun enumName(value: Enum<*>): String = value.name.lowercase()

    inline fun <reified E : Enum<E>> parseEnum(text: String): E =
        checkNotNull(enumValues<E>().firstOrNull { it.name.equals(text, ignoreCase = true) }) {
            "Stored value is not a known ${E::class.simpleName}"
        }

    fun viewName(view: EvidenceView): String = enumName(view)

    fun parseView(text: String): EvidenceView = parseEnum(text)
}
