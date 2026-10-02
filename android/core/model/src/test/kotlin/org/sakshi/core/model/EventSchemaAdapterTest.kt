package org.sakshi.core.model

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SchemaValidatorsConfig
import com.networknt.schema.SpecVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class EventSchemaAdapterTest {
    private val mapper = ObjectMapper()
    private val schema: JsonSchema by lazy {
        val config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build()
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(mapper.readTree(TestSupport.schemaText()), config)
    }

    private fun schemaErrors(json: String): Int {
        val node: JsonNode = mapper.readTree(json)
        return schema.validate(node).size
    }

    @Test
    fun validFixtureHasNoSchemaErrors() {
        assertEquals(0, schemaErrors(TestSupport.fixture("event-valid.json")))
    }

    @Test
    fun validFixtureRoundTripsStructurally() {
        val text = TestSupport.fixture("event-valid.json")
        val original = Json.parseToJsonElement(text)
        val event = EventSchemaAdapter.fromJson(text)
        assertEquals(original, EventSchemaAdapter.toJsonElement(event))
        assertEquals(event, EventSchemaAdapter.fromJson(EventSchemaAdapter.toJson(event)))
        assertEquals(0, schemaErrors(EventSchemaAdapter.toJson(event)))
    }

    @Test
    fun nullableFieldsAreEmittedAsExplicitNulls() {
        val json = EventSchemaAdapter.toJsonElement(TestSupport.validEvent()) as JsonObject
        val timestamp = json.getValue("timestamp") as JsonObject
        assertTrue("collector_session_id" in timestamp)
        assertEquals(kotlinx.serialization.json.JsonNull, timestamp["monotonic_ms"])
        assertEquals(JsonPrimitive(1), json["schema_version"])
    }

    @Test
    fun timestampTextIsPreservedExactly() {
        val event = TestSupport.validEvent()
        assertEquals("2026-10-01T09:05:59.999+05:30", event.timestamp.latest?.iso)
        assertTrue(EventSchemaAdapter.toJson(event).contains("2026-10-01T09:05:59.999+05:30"))
    }

    @Test
    fun invalidFixturesProduceSchemaErrors() {
        val names = listOf(
            "event-invalid-date-time.json",
            "event-invalid-user-tag-probability.json",
            "event-invalid-candidate-expiry.json",
            "event-invalid-duplicate-canonical.json",
            "event-invalid-unknown-time-bounds.json",
            "event-invalid-calibration-version.json",
        )
        for (name in names) {
            assertTrue(schemaErrors(TestSupport.fixture(name)) > 0, "$name should fail the schema")
        }
    }

    @Test
    fun fromJsonRejectsUnknownProperty() {
        val text = TestSupport.fixture("event-valid.json").replaceFirst("{", "{\"extra\": 1,")
        assertFailsWith<IllegalArgumentException> { EventSchemaAdapter.fromJson(text) }
    }

    @Test
    fun fromJsonRejectsWrongSchemaVersion() {
        val text = TestSupport.fixture("event-valid.json").replace("\"schema_version\": 1", "\"schema_version\": 2")
        assertFailsWith<IllegalArgumentException> { EventSchemaAdapter.fromJson(text) }
    }

    @Test
    fun fromJsonRejectsMissingSchemaVersion() {
        val text = TestSupport.fixture("event-valid.json").replace("\"schema_version\": 1,", "")
        assertFailsWith<IllegalArgumentException> { EventSchemaAdapter.fromJson(text) }
    }

    @Test
    fun fromJsonRejectsInvalidTimestamp() {
        assertFailsWith<IllegalArgumentException> {
            EventSchemaAdapter.fromJson(TestSupport.fixture("event-invalid-date-time.json"))
        }
    }

    @Test
    fun fromJsonRejectsMissingNullableProperty() {
        val text = TestSupport.fixture("event-valid.json").replace("\"monotonic_ms\": null", "\"x_ms\": null")
        assertFailsWith<IllegalArgumentException> { EventSchemaAdapter.fromJson(text) }
    }

    @Test
    fun fromJsonRejectsMalformedJson() {
        assertFailsWith<IllegalArgumentException> { EventSchemaAdapter.fromJson("{not json") }
    }

    @Test
    fun kotlinBuiltEventCoversEveryLocatorAndRoundTrips() {
        val base = TestSupport.validEvent()
        val refs = listOf(
            reference("r1", Locator.WholeArtifact),
            reference("r2", Locator.Text(2, 5)),
            reference("r3", Locator.AudioTime(100, 900)),
            reference("r4", Locator.ImageOrPageRegion(pageIndex = null, regionId = ScopeId("region-1"))),
        )
        val event = base.copy(
            categories = listOf(
                CategoryAssessment(
                    label = CategoryLabel.IMPLIED_THREAT,
                    basis = CategoryBasis.CLASSIFIER_SUGGESTION,
                    confidence = Confidence(0.42, ConfidenceSemantics.UNCALIBRATED_BOUNDED_SCORE, null),
                    producerVersion = ScopeId("clf-v1"),
                    evidenceReferenceIds = listOf(ReferenceId("r2")),
                    reviewStatus = CategoryReviewStatus.UNREVIEWED,
                ),
            ),
            severity = base.severity.copy(evidenceReferenceIds = listOf(ReferenceId("r1"))),
            evidenceReferences = refs,
            userConfirmation = UserConfirmation(ConfirmationStatus.PENDING, null, ConfirmationScope.NOT_REVIEWED),
            relationshipToPreviousEvents = listOf(
                EventRelationship(
                    targetEventId = EventId("earlier-1"),
                    type = RelationshipType.RECURRENCE_CANDIDATE,
                    basis = RelationshipBasis.RULE,
                    confidence = Confidence(null, ConfidenceSemantics.UNKNOWN, null),
                    reviewStatus = RelationshipReviewStatus.UNREVIEWED,
                ),
            ),
            retention = Retention(RetentionMode.ENCRYPTED_CANDIDATE, Timestamp("2026-10-08T09:05:00Z"), 0),
        )
        val json = EventSchemaAdapter.toJson(event)
        assertEquals(0, schemaErrors(json), "schema errors for $json")
        assertEquals(event, EventSchemaAdapter.fromJson(json))
    }

    private fun reference(id: String, locator: Locator): EvidenceReference =
        EvidenceReference(ReferenceId(id), ArtifactId("art-$id"), null, Representation.PRESERVED_IMPORT, locator)
}
