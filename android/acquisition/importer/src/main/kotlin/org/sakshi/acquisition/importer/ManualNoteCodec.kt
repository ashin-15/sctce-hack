package org.sakshi.acquisition.importer

import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.sakshi.core.integrity.CanonicalJson

/** Canonical JSON form of a [ManualNote], so equal notes always give equal bytes. */
public object ManualNoteCodec {
    public const val MIME_TYPE: String = "application/vnd.sakshi.note+json"
    public const val SCHEMA: String = "sakshi-manual-note/1"

    private const val TEXT = "text"
    private const val INCIDENT_TIME = "incident_time_text"
    private const val SENDER = "claimed_sender"
    private const val SOURCE_APP = "source_app_claim"
    private const val VIEW_ONCE = "view_once_status"
    private const val AVAILABILITY = "content_availability"
    private const val WRITTEN_AT = "written_at"
    private const val SCHEMA_KEY = "schema"
    private val KEYS = setOf(SCHEMA_KEY, TEXT, INCIDENT_TIME, SENDER, SOURCE_APP, VIEW_ONCE, AVAILABILITY, WRITTEN_AT)

    /** @throws IllegalArgumentException if the note is not valid. */
    public fun encode(note: ManualNote, writtenAt: Instant): ByteArray {
        val clean = note.normalised()
        val json = JsonObject(
            mapOf(
                SCHEMA_KEY to JsonPrimitive(SCHEMA),
                TEXT to JsonPrimitive(clean.text),
                INCIDENT_TIME to optional(clean.incidentTimeText),
                SENDER to optional(clean.claimedSender),
                SOURCE_APP to optional(clean.sourceAppClaim),
                VIEW_ONCE to JsonPrimitive(clean.viewOnceStatus.name.lowercase()),
                AVAILABILITY to JsonPrimitive(clean.contentAvailability.name.lowercase()),
                WRITTEN_AT to JsonPrimitive(writtenAt.toString()),
            ),
        )
        return CanonicalJson.encode(json)
    }

    /** @throws IllegalArgumentException for malformed bytes, another schema version, or missing or extra fields. */
    public fun decode(bytes: ByteArray): StoredManualNote {
        val root = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject
            ?: throw IllegalArgumentException("Not a note")
        require(root.keys == KEYS) { "Unexpected note fields" }
        require(string(root, SCHEMA_KEY) == SCHEMA) { "Unknown note schema" }
        val note = ManualNote(
            text = string(root, TEXT),
            incidentTimeText = optionalString(root, INCIDENT_TIME),
            claimedSender = optionalString(root, SENDER),
            sourceAppClaim = optionalString(root, SOURCE_APP),
            viewOnceStatus = enumOf(ViewOnceStatus.entries, string(root, VIEW_ONCE)),
            contentAvailability = enumOf(ContentAvailability.entries, string(root, AVAILABILITY)),
        )
        require(note.problem() == null) { "Note is not valid" }
        val writtenAt = try {
            Instant.parse(string(root, WRITTEN_AT))
        } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("Bad note time")
        }
        return StoredManualNote(note, writtenAt)
    }

    private fun optional(value: String?): JsonElement = if (value == null) JsonNull else JsonPrimitive(value)

    private fun string(root: JsonObject, key: String): String {
        val primitive = root[key] as? JsonPrimitive
        require(primitive != null && primitive.isString) { "Bad note field" }
        return primitive.content
    }

    private fun optionalString(root: JsonObject, key: String): String? =
        if (root[key] is JsonNull) null else string(root, key)

    private fun <E : Enum<E>> enumOf(entries: List<E>, wire: String): E =
        requireNotNull(entries.firstOrNull { it.name.lowercase() == wire }) { "Bad note value" }
}
