package org.sakshi.acquisition.notifications

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.sakshi.core.model.Direction
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.OutgoingCoverage
import org.sakshi.core.model.SourceKind
import org.sakshi.core.model.TextStatus

class HandoffTest {
    private fun candidate(snapshot: NotificationSnapshot): ObservedCandidate =
        ObservedCandidate("id1", NotificationNormalizer.normalize(snapshot).messages.single())

    @Test
    fun `a messaging candidate hands off as a notification excerpt with every claim and both times`() {
        val handoff = candidate(snap(messages = listOf(msg("synthetic words", time = T0)), wall = T0 + 123_456)).toHandoff()
        assertEquals("synthetic words", handoff.text)
        assertEquals(SourceKind.NOTIFICATION_EXCERPT, handoff.sourceKind)
        assertEquals("notification_excerpt", handoff.acquisitionKind)
        assertEquals("notification_observation", handoff.accessClass)
        assertEquals(APP, handoff.sourceAppClaim)
        assertEquals("Synthetic Sender One", handoff.senderLabelClaim)
        assertEquals(IdentityBasis.APP_SCOPED_HINT, handoff.identityBasis)
        assertEquals(Direction.INCOMING, handoff.direction)
        assertEquals(OutgoingCoverage.UNKNOWN, handoff.outgoingCoverage)
        assertEquals(TextStatus.AVAILABLE, handoff.textStatus)
        assertFalse(handoff.summaryOnly)
        assertEquals(T0, handoff.sourceClaimTime.epochMs)
        assertEquals(T0 + 123_456, handoff.collectorTime.wallMs)
        assertEquals(SESSION, handoff.collectorTime.sessionId)
        assertFalse(handoff.observedAsActiveSnapshot)
    }

    @Test
    fun `summary only text is marked uncertain and truncated text is marked truncated`() {
        val summary = candidate(snap(title = "A", text = "an excerpt", summary = true)).toHandoff()
        assertEquals(TextStatus.EXTRACTION_UNCERTAIN, summary.textStatus)
        assertTrue(summary.summaryOnly)
        val cut = candidate(snap(messages = listOf(msg("cut", truncated = true)))).toHandoff()
        assertEquals(TextStatus.TRUNCATED, cut.textStatus)
    }

    @Test
    fun `no sender means an unknown identity and direction and an active snapshot is tagged`() {
        val handoff = candidate(snap(origin = SnapshotOrigin.ACTIVE_SNAPSHOT, text = "just text")).toHandoff()
        assertEquals(IdentityBasis.UNKNOWN, handoff.identityBasis)
        assertEquals(Direction.UNKNOWN, handoff.direction)
        assertNull(handoff.senderLabelClaim)
        assertTrue(handoff.observedAsActiveSnapshot)
    }

    @Test
    fun `lifecycle and corroboration travel with the hand off`() {
        val base = candidate(snap(messages = listOf(msg("x"))))
        val handoff = base.copy(corroboratingObservations = 2, removal = RemovalLifecycle(10, T0)).toHandoff()
        assertEquals(2, handoff.corroboratingObservations)
        assertEquals(10, handoff.removal?.platformReasonCode)
    }
}

class PackageAllowlistTest {
    @Test
    fun `the default is empty and contains nothing`() {
        assertTrue(PackageAllowlist.EMPTY.isEmpty)
        assertFalse(APP in PackageAllowlist.EMPTY)
    }

    @Test
    fun `add and remove`() {
        val list = PackageAllowlist.EMPTY.with(APP)
        assertTrue(APP in list)
        assertFalse(list.without(APP).names.contains(APP))
    }

    @Test
    fun `only valid package names are accepted`() {
        assertFailsWith<IllegalArgumentException> { PackageAllowlist.of(listOf("not a package")) }
        assertFailsWith<IllegalArgumentException> { PackageAllowlist.of(listOf("single")) }
        assertFalse(PackageAllowlist.isValidName(""))
        assertTrue(PackageAllowlist.isValidName("a.b_c.D1"))
    }

    @Test
    fun `the text form hides the names`() {
        assertFalse(PackageAllowlist.of(listOf(APP)).toString().contains(APP))
    }
}

class SettingsTest {
    private fun file(): File = File(createTempDirectory("sakshi-notification-settings").toFile(), "settings.properties")

    @Test
    fun `the defaults are the safe side`() {
        val state = NotificationSettings(file()).state.value
        assertFalse(state.enabled)
        assertFalse(state.paused)
        assertTrue(state.allowlist.isEmpty)
        assertFalse(state.lockScreenPreviewsOptIn)
        assertFalse(state.includeActiveOnConnect)
    }

    @Test
    fun `a change survives a round trip`() {
        val path = file()
        NotificationSettings(path).update {
            it.copy(
                enabled = true,
                paused = true,
                allowlist = PackageAllowlist.of(listOf(APP, "synthetic.second.app")),
                lockScreenPreviewsOptIn = true,
                includeActiveOnConnect = true,
                backgroundObservationOptIn = true,
                cueAlertsOptIn = true,
            )
        }
        val loaded = NotificationSettings(path).state.value
        assertTrue(loaded.enabled)
        assertTrue(loaded.paused)
        assertEquals(setOf(APP, "synthetic.second.app"), loaded.allowlist.names)
        assertTrue(loaded.lockScreenPreviewsOptIn)
        assertTrue(loaded.includeActiveOnConnect)
        assertTrue(loaded.backgroundObservationOptIn)
        assertTrue(loaded.cueAlertsOptIn)
    }

    @Test
    fun `the file holds configuration only`() {
        val path = file()
        NotificationSettings(path).update { it.copy(enabled = true, allowlist = PackageAllowlist.of(listOf(APP))) }
        val text = path.readText()
        assertTrue(text.contains(APP))
        assertEquals(
            setOf("enabled", "paused", "lock_screen_previews_opt_in", "include_active_on_connect", "allowlist", "background_observation_opt_in", "cue_alerts_opt_in"),
            text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.substringBefore('=') }.toSet(),
        )
    }

    @Test
    fun `a corrupt or invalid file means the defaults and drops bad names`() {
        val path = file()
        path.writeText("enabled=true\nallowlist=not a package,$APP\n")
        val state = NotificationSettings(path).state.value
        assertTrue(state.enabled)
        assertEquals(setOf(APP), state.allowlist.names)
        path.writeText("\u0000\u0001 garbage")
        assertFalse(NotificationSettings(path).state.value.enabled)
        assertNotNull(NotificationSettings(file()).state.value)
    }
}
