package org.sakshi.acquisition.importer

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.AuditVerification

class ManualNoteTest : ImporterTestBase() {
    private val at: Instant = Instant.parse("2026-10-02T10:00:00Z")

    private fun note(
        text: String = "synthetic note",
        time: String? = "last Tuesday evening",
        sender: String? = "synthetic sender",
        app: String? = "synthetic app",
    ) = ManualNote(text, time, sender, app, ViewOnceStatus.USER_REPORTED, ContentAvailability.CONTEXT_ONLY)

    @Test
    fun validationBounds() {
        assertNull(note().problem())
        assertEquals(Rejection.EMPTY_TEXT, note(text = " \n ").problem())
        assertNull(note(text = "a".repeat(20_000)).problem())
        assertEquals(Rejection.TEXT_TOO_LONG, note(text = "a".repeat(20_001)).problem())
        assertNull(note(text = "  " + "a".repeat(20_000) + "  ").problem())
        assertNull(note(time = "t".repeat(200)).problem())
        assertEquals(Rejection.TEXT_TOO_LONG, note(time = "t".repeat(201)).problem())
        assertEquals(Rejection.TEXT_TOO_LONG, note(sender = "s".repeat(201)).problem())
        assertEquals(Rejection.TEXT_TOO_LONG, note(app = "s".repeat(201)).problem())
        assertFailsWith<IllegalArgumentException> { note(text = "").normalised() }
    }

    @Test
    fun normalisationTrimsAndBlanksBecomeNull() {
        val clean = note(text = "  hello  ", time = "  ", sender = " x ", app = "").normalised()
        assertEquals("hello", clean.text)
        assertNull(clean.incidentTimeText)
        assertEquals("x", clean.claimedSender)
        assertNull(clean.sourceAppClaim)
    }

    @Test
    fun roundTripKeepsUnicodeAndFreeTextTime() {
        val original = note(text = "ഇത് यह 😀 \"quoted\"\n", time = "around 9ish, maybe 21:30")
        val decoded = ManualNoteCodec.decode(ManualNoteCodec.encode(original, at))
        assertEquals(original.normalised(), decoded.note)
        assertEquals(at, decoded.writtenAt)
    }

    @Test
    fun canonicalBytesAreStableAndSorted() {
        val bytes = ManualNoteCodec.encode(note(app = null), at)
        assertContentEquals(bytes, ManualNoteCodec.encode(note(app = null), at))
        val expected = "{\"claimed_sender\":\"synthetic sender\",\"content_availability\":\"context_only\"," +
            "\"incident_time_text\":\"last Tuesday evening\",\"schema\":\"sakshi-manual-note/1\"," +
            "\"source_app_claim\":null,\"text\":\"synthetic note\",\"view_once_status\":\"user_reported\"," +
            "\"written_at\":\"2026-10-02T10:00:00Z\"}"
        assertEquals(expected, bytes.toString(Charsets.UTF_8))
    }

    @Test
    fun decodeIsStrict() {
        val good = ManualNoteCodec.encode(note(), at).toString(Charsets.UTF_8)
        fun bad(text: String) = assertFailsWith<IllegalArgumentException>(text) { ManualNoteCodec.decode(text.toByteArray()) }
        bad(good.replace("sakshi-manual-note/1", "sakshi-manual-note/2"))
        bad(good.replace("\"text\":\"synthetic note\",", ""))
        bad(good.replace("{", "{\"extra\":1,"))
        bad(good.replace("user_reported", "bogus"))
        bad(good.replace("2026-10-02T10:00:00Z", "yesterday"))
        bad(good.replace("\"synthetic note\"", "5"))
        bad(good.replace("\"synthetic note\"", "\"\""))
        bad("[]")
        bad("not json")
        bad("")
    }

    @Test
    fun commitNoteStoresCanonicalBytesWithNoteKindAndMime() {
        val secret = "synthetic-secret-note-text-31337"
        val outcome = runBlocking { importer().commitNote(caseId, note(text = secret)) }
        val saved = assertIs<ItemOutcome.Saved>(outcome)
        assertEquals(ManualNoteCodec.MIME_TYPE, saved.declaredMime)
        assertEquals(AnalysisState.READY_FOR_TEXT_ANALYSIS, saved.analysisState)
        val stored = original(saved.evidenceId)
        assertContentEquals(ManualNoteCodec.encode(note(text = secret), FIXED_INSTANT), stored)
        assertEquals(secret, ManualNoteCodec.decode(stored).note.text)
        val row = runBlocking { vault.evidence.observeForCase(caseId).first().single() }
        assertEquals(AcquisitionKind.MANUAL_NOTE, row.acquisitionKind)
        assertTrue(runBlocking { vault.audit.verify() } is AuditVerification.Valid)
        assertNotNull(blobNames().singleOrNull())
    }

    @Test
    fun invalidNoteIsSkippedNotSaved() {
        val outcome = runBlocking { importer().commitNote(caseId, note(text = "  ")) }
        assertEquals(ItemOutcome.Skipped(0, Rejection.EMPTY_TEXT), outcome)
        assertEquals(0, rowCount())
    }

    @Test
    fun noteInUnknownCaseFails() {
        val outcome = runBlocking { importer().commitNote("no-such-case", note()) }
        assertEquals(ItemOutcome.Failed(0, ImportFailure.CASE_UNAVAILABLE), outcome)
    }
}
