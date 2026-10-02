package org.sakshi.core.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Converts between [Event] and the JSON interchange form defined by the event schema. */
public object EventSchemaAdapter {
    private val json: Json = Json {
        explicitNulls = true
        encodeDefaults = true
        ignoreUnknownKeys = false
        classDiscriminator = "kind"
    }

    public fun toJson(event: Event): String = json.encodeToString(Event.serializer(), event)

    public fun toJsonElement(event: Event): JsonElement = json.encodeToJsonElement(Event.serializer(), event)

    /** @throws IllegalArgumentException if [json] is malformed or violates the typed contract. */
    public fun fromJson(json: String): Event =
        try {
            this.json.decodeFromString(Event.serializer(), json)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Invalid event JSON: ${e.message}", e)
        }
}
