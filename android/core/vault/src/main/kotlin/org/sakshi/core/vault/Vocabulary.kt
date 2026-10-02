package org.sakshi.core.vault

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Values of `evidence.acquisition_kind`. */
public object AcquisitionKind {
    public const val SHARED_TEXT: String = "shared_text"
    public const val SHARED_STREAM: String = "shared_stream"
    public const val SELECTED_DOCUMENT: String = "selected_document"
    public const val SELECTED_VISUAL_MEDIA: String = "selected_visual_media"
    public const val PASTED_TEXT: String = "pasted_text"
    public const val MANUAL_NOTE: String = "manual_note"

    internal val all: Set<String> =
        setOf(SHARED_TEXT, SHARED_STREAM, SELECTED_DOCUMENT, SELECTED_VISUAL_MEDIA, PASTED_TEXT, MANUAL_NOTE)
}

/** Values of `evidence.access_class`. */
public object AccessClass {
    public const val USER_MEDIATED: String = "user_mediated"
    public const val NOTIFICATION_OBSERVATION: String = "notification_observation"

    internal val all: Set<String> = setOf(USER_MEDIATED, NOTIFICATION_OBSERVATION)
}

/** `evidence.retention_mode` for evidence the user explicitly saved. */
internal const val RETENTION_CONFIRMED_VAULT: String = "confirmed_vault"

/** Builds a flat JSON object from strings, numbers and booleans. */
internal fun jsonObjectOf(vararg entries: Pair<String, Any>): JsonObject =
    JsonObject(entries.associate { (key, value) -> key to value.toJson() })

private fun Any.toJson(): JsonElement = when (this) {
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    else -> throw IllegalArgumentException("Unsupported JSON value type")
}
