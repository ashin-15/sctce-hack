package org.sakshi.core.vault

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Before
import org.sakshi.core.database.CaseEntity
import org.sakshi.core.database.CaseStatus
import org.sakshi.core.database.SakshiSchema
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventSchemaAdapter
import org.sakshi.core.model.IdentityBasis

/** Event store tests over the in-memory database, with a recording audit log. */
abstract class EventStoreTestBase : VaultTestBase() {
    protected lateinit var recording: RecordingAuditLog
    protected lateinit var store: EventStore
    protected lateinit var actors: ActorRegistry

    @Before
    fun openEventStore() {
        recording = RecordingAuditLog(db, clock)
        store = EventStore(db, recording, clock, ids, Dispatchers.IO)
        actors = ActorRegistry(db, recording, ids, Dispatchers.IO)
    }

    protected suspend fun insertCase(id: String) {
        db.caseDao().insert(CaseEntity(id, "synthetic-title", "2026-10-02T10:00:00Z", CaseStatus.ACTIVE))
    }

    protected suspend fun insertActor(caseId: String, id: String) {
        actors.create(CaseId(caseId), "synthetic-label-$id", IdentityBasis.USER_ASSERTED, AssociationReview.CONFIRMED, ActorId(id))
    }

    /** Case plus the two standard actors. */
    protected suspend fun standardCase(id: String = EventFixtures.CASE) {
        insertCase(id)
        insertActor(id, EventFixtures.ACTOR)
        insertActor(id, EventFixtures.OTHER_ACTOR)
    }

    protected suspend fun saveOk(event: Event) {
        assertEquals(SaveResult.Saved(event.eventId, event.revision), store.save(event))
    }

    /** Saves, loads back and asserts equality of the event and of its schema JSON. */
    protected suspend fun assertRoundTrip(event: Event) {
        saveOk(event)
        val loaded = assertNotNull(store.load(event.eventId, event.revision))
        assertEquals(event, loaded)
        assertEquals(EventSchemaAdapter.toJsonElement(event), EventSchemaAdapter.toJsonElement(loaded))
    }

    protected suspend fun rowCounts(): List<Int> = withContext(Dispatchers.IO) {
        listOf(
            SakshiSchema.EVENT, SakshiSchema.EVENT_REVISION, SakshiSchema.EVIDENCE_ANCHOR, SakshiSchema.FINDING,
            SakshiSchema.FINDING_ANCHOR, SakshiSchema.EVENT_LINK, SakshiSchema.BOUNDARY, SakshiSchema.AUDIT_RECORD,
        ).map { table ->
            db.query("SELECT COUNT(*) FROM $table", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }
        }
    }
}
