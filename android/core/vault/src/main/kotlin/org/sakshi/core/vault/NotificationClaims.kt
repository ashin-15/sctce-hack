package org.sakshi.core.vault

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.sakshi.core.model.Direction
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.TextStatus

/**
 * What the publishing app and the collector claimed about one notification excerpt. Every value is a claim kept as
 * received: none is verified. [sourceClaimTimeMs] is the time the source app claims and [collectorWallMs] is when
 * Sakshi saw the notification; they are stored as separate fields, never compared and never merged.
 * [sourceClaimTimeBasis] is the wire name of the publisher field the claim came from.
 */
public data class NotificationClaims(
    val sourceAppClaim: String,
    val conversationScopeClaim: String,
    val conversationTitleClaim: String?,
    val isGroupClaim: Boolean?,
    val senderLabelClaim: String?,
    val identityBasis: IdentityBasis,
    val direction: Direction,
    val textStatus: TextStatus,
    val summaryOnly: Boolean,
    val sourceClaimTimeMs: Long?,
    val sourceClaimTimeBasis: String,
    val notificationPostTimeMs: Long,
    val collectorWallMs: Long,
    val collectorElapsedRealtimeMs: Long,
    val collectorSessionId: String,
    val removalReasonCode: Int?,
    val removalObservedWallMs: Long?,
    val corroboratingObservations: Int,
    val observedAsActiveSnapshot: Boolean,
) {
    /** The value for `capture_metadata.exif_json`: one object under the key [ROOT_KEY]. */
    public fun toJson(): String = JsonObject(mapOf(ROOT_KEY to body())).toString()

    private fun body(): JsonObject = JsonObject(
        mapOf(
            "source_app_claim" to JsonPrimitive(sourceAppClaim),
            "conversation_scope_claim" to JsonPrimitive(conversationScopeClaim),
            "conversation_title_claim" to JsonPrimitive(conversationTitleClaim),
            "is_group_claim" to JsonPrimitive(isGroupClaim),
            "sender_label_claim" to JsonPrimitive(senderLabelClaim),
            "identity_basis" to JsonPrimitive(Codecs.identityBasis.name(identityBasis)),
            "direction" to JsonPrimitive(Codecs.direction.name(direction)),
            "text_status" to JsonPrimitive(Codecs.textStatus.name(textStatus)),
            "summary_only" to JsonPrimitive(summaryOnly),
            "source_claim_time_ms" to JsonPrimitive(sourceClaimTimeMs),
            "source_claim_time_basis" to JsonPrimitive(sourceClaimTimeBasis),
            "notification_post_time_ms" to JsonPrimitive(notificationPostTimeMs),
            "collector_wall_ms" to JsonPrimitive(collectorWallMs),
            "collector_elapsed_realtime_ms" to JsonPrimitive(collectorElapsedRealtimeMs),
            "collector_session_id" to JsonPrimitive(collectorSessionId),
            "removal_reason_code" to JsonPrimitive(removalReasonCode),
            "removal_observed_wall_ms" to JsonPrimitive(removalObservedWallMs),
            "corroborating_observations" to JsonPrimitive(corroboratingObservations),
            "observed_as_active_snapshot" to JsonPrimitive(observedAsActiveSnapshot),
        ),
    )

    public companion object {
        /** Key of the object inside `capture_metadata.exif_json`. */
        public const val ROOT_KEY: String = "notification_claims"

        /** Reads claims written by [toJson]. Returns null when [json] is absent, damaged or not notification claims. */
        public fun decode(json: String?): NotificationClaims? {
            if (json == null) return null
            return try {
                val root = Json.parseToJsonElement(json).jsonObject[ROOT_KEY]?.jsonObject ?: return null
                parse(root)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: IllegalStateException) {
                null
            }
        }

        private fun parse(o: JsonObject): NotificationClaims = NotificationClaims(
            sourceAppClaim = o.text("source_app_claim"),
            conversationScopeClaim = o.text("conversation_scope_claim"),
            conversationTitleClaim = o.textOrNull("conversation_title_claim"),
            isGroupClaim = o.value("is_group_claim").booleanOrNull,
            senderLabelClaim = o.textOrNull("sender_label_claim"),
            identityBasis = Codecs.identityBasis.parse(o.text("identity_basis")),
            direction = Codecs.direction.parse(o.text("direction")),
            textStatus = Codecs.textStatus.parse(o.text("text_status")),
            summaryOnly = o.value("summary_only").boolean,
            sourceClaimTimeMs = o.value("source_claim_time_ms").longOrNull,
            sourceClaimTimeBasis = o.text("source_claim_time_basis"),
            notificationPostTimeMs = checkNotNull(o.value("notification_post_time_ms").longOrNull),
            collectorWallMs = checkNotNull(o.value("collector_wall_ms").longOrNull),
            collectorElapsedRealtimeMs = checkNotNull(o.value("collector_elapsed_realtime_ms").longOrNull),
            collectorSessionId = o.text("collector_session_id"),
            removalReasonCode = o.value("removal_reason_code").intOrNull,
            removalObservedWallMs = o.value("removal_observed_wall_ms").longOrNull,
            corroboratingObservations = checkNotNull(o.value("corroborating_observations").intOrNull),
            observedAsActiveSnapshot = o.value("observed_as_active_snapshot").boolean,
        )

        private fun JsonObject.value(key: String): JsonPrimitive =
            (this[key] ?: JsonNull).jsonPrimitive

        private fun JsonObject.text(key: String): String = checkNotNull(value(key).contentOrNull) { "Missing $key" }

        private fun JsonObject.textOrNull(key: String): String? = value(key).contentOrNull
    }
}
