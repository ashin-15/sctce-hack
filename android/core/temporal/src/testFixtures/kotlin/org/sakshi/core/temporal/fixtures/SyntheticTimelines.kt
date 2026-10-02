package org.sakshi.core.temporal.fixtures

import java.time.Instant
import java.time.ZoneOffset
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CommunicationStatus
import org.sakshi.core.model.DedupStatus
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.temporal.CoverageGap
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.temporal.GapReason
import org.sakshi.core.temporal.PatternConfig
import org.sakshi.core.temporal.TemporalInput

/**
 * Synthetic timelines A to F from the temporal patterns report, section 10.
 *
 * Everything here is designed test data. None of it is evidence and none of it is a measurement of any
 * detector. Times are written at +05:30 on 1 and 2 October 2026 and analysed in that zone.
 */
public object SyntheticTimelines {
    private val ZONE: ZoneOffset = ZoneOffset.ofHoursMinutes(5, 30)
    private val CUTOFF: Instant = Instant.parse("2026-10-03T00:00:00Z")
    private const val OCT1: String = "2026-10-01"
    private const val OCT2: String = "2026-10-02"

    /** Local time on 1 October (or [date]) at +05:30. */
    public fun local(time: String, date: String = OCT1): String = "${date}T$time:00+05:30"

    /** Default synthetic analysis time zone. */
    public val zone: ZoneOffset get() = ZONE

    private fun input(
        caseSuffix: String,
        events: List<Event>,
        gaps: List<CoverageGap> = emptyList(),
        view: EvidenceView,
    ): TemporalInput =
        TemporalInput(
            caseId = CaseId("synthetic-case-$caseSuffix"),
            events = events,
            gaps = gaps,
            knowledgeCutoff = CUTOFF,
            view = view,
            config = PatternConfig(zone = ZONE),
        )

    private fun contact(
        id: String,
        caseSuffix: String,
        time: String,
        actor: String,
        date: String = OCT1,
        configure: EventBuilder.() -> Unit = {},
    ): Event =
        syntheticEvent(id) {
            caseId = "synthetic-case-$caseSuffix"
            this.actor = actor
            at(local(time, date))
            configure()
        }

    /** A: harmless-looking texts after a selected stop-contact message. */
    public fun a(view: EvidenceView = EvidenceView.CONFIRMED_ONLY): TemporalInput {
        val stop =
            syntheticEvent("synthetic-a0") {
                caseId = "synthetic-case-a"
                at(local("09:00"))
                boundary(BoundaryMarker.DO_NOT_CONTACT, "synthetic-actor-a", CommunicationStatus.SUPPORTED_BY_SELECTED_EVIDENCE)
            }
        val times = listOf("09:05", "09:07", "09:10", "09:15", "09:20", "09:40")
        val contacts =
            times.mapIndexed { index, time ->
                contact("synthetic-a${index + 1}", "a", time, "synthetic-actor-a") { unwanted() }
            }
        return input("a", listOf(stop) + contacts, view = view)
    }

    /** B: verbal abuse, then intimidation, then an explicit harm statement 14 hours after the first. */
    public fun b(view: EvidenceView = EvidenceView.CONFIRMED_ONLY): TemporalInput =
        input(
            "b",
            listOf(
                contact("synthetic-b1", "b", "18:00", "synthetic-actor-b") { category(CategoryLabel.VERBAL_ABUSE) },
                contact("synthetic-b2", "b", "18:20", "synthetic-actor-b") { category(CategoryLabel.INTIMIDATION) },
                contact("synthetic-b3", "b", "08:00", "synthetic-actor-b", OCT2) {
                    category(CategoryLabel.EXPLICIT_THREAT)
                },
            ),
            view = view,
        )

    /** C: three messages in one hour, twelve in the next, no stop request and no unwanted marking. */
    public fun c(view: EvidenceView = EvidenceView.CONFIRMED_ONLY): TemporalInput {
        val earlier = listOf("08:10", "08:25", "08:50")
        val later = (0 until 12).map { "09:%02d".format(it * 5) }
        val events =
            (earlier + later).mapIndexed { index, time ->
                contact("synthetic-c${index + 1}", "c", time, "synthetic-actor-c")
            }
        return input("c", events, view = view)
    }

    /** D: a disengagement note, three contacts, a 22 hour coverage gap and two more contacts. */
    public fun d(view: EvidenceView = EvidenceView.CONFIRMED_ONLY): TemporalInput {
        val note =
            syntheticEvent("synthetic-d0") {
                caseId = "synthetic-case-d"
                at(local("10:00"))
                boundary(BoundaryMarker.USER_DISENGAGEMENT, "synthetic-actor-d", CommunicationStatus.NOT_COMMUNICATED)
            }
        val contacts =
            listOf(
                contact("synthetic-d1", "d", "10:10", "synthetic-actor-d"),
                contact("synthetic-d2", "d", "10:14", "synthetic-actor-d"),
                contact("synthetic-d3", "d", "10:20", "synthetic-actor-d"),
                contact("synthetic-d4", "d", "09:10", "synthetic-actor-d", OCT2),
                contact("synthetic-d5", "d", "09:15", "synthetic-actor-d", OCT2),
            )
        val gap =
            CoverageGap(
                id = ReferenceId("synthetic-g1"),
                caseId = CaseId("synthetic-case-d"),
                actorId = null,
                start = Instant.parse("2026-10-01T05:30:00Z"),
                end = Instant.parse("2026-10-02T03:30:00Z"),
                reason = GapReason.LISTENER_DISCONNECTED,
            )
        return input("d", listOf(note) + contacts, listOf(gap), view)
    }

    /** E: location and proof requests with a stated consequence; E2 is the user's own reply. */
    public fun e(view: EvidenceView = EvidenceView.CONFIRMED_ONLY): TemporalInput =
        input(
            "e",
            listOf(
                contact("synthetic-e1", "e", "20:00", "synthetic-actor-e") {
                    category(CategoryLabel.CONTROLLING_REQUEST)
                },
                syntheticEvent("synthetic-e2") {
                    caseId = "synthetic-case-e"
                    at(local("20:05"))
                    direction = Direction.OUTGOING
                    actor = null
                    associationReview = AssociationReview.UNKNOWN
                    identityBasis = IdentityBasis.UNKNOWN
                    displayLabel = "You"
                },
                contact("synthetic-e3", "e", "20:07", "synthetic-actor-e") {
                    category(CategoryLabel.CONTROLLING_REQUEST)
                },
                contact("synthetic-e4", "e", "20:10", "synthetic-actor-e") { category(CategoryLabel.INTIMIDATION) },
            ),
            view = view,
        )

    /**
     * F: five observations of three messages. F1 and F2 each have a repost recorded as the same
     * representation, so exactly three contacts result. The sender is not linked to a confirmed person.
     */
    public fun f(view: EvidenceView = EvidenceView.CONFIRMED_ONLY): TemporalInput =
        input(
            "f",
            listOf(
                alex("synthetic-f1", "f", "10:00", "synthetic-conversation-1", "synthetic-app-1"),
                alex("synthetic-f1-repost", "f", "10:00", "synthetic-conversation-1", "synthetic-app-1") {
                    dedup(DedupStatus.SAME_REPRESENTATION, "synthetic-f1")
                },
                alex("synthetic-f2", "f", "10:01", "synthetic-conversation-1", "synthetic-app-1"),
                alex("synthetic-f2-repost", "f", "10:01", "synthetic-conversation-1", "synthetic-app-1") {
                    dedup(DedupStatus.SAME_REPRESENTATION, "synthetic-f2")
                },
                alex("synthetic-f3", "f", "10:02", "synthetic-conversation-1", "synthetic-app-1"),
            ),
            view = view,
        )

    /**
     * F, second part: two different app and conversation scopes both show the label "Alex", each with two
     * contacts. They must stay separate scopes.
     */
    public fun fTwoAlex(view: EvidenceView = EvidenceView.CONFIRMED_ONLY): TemporalInput =
        input(
            "f",
            listOf(
                alex("synthetic-f4", "f", "11:00", "synthetic-conversation-1", "synthetic-app-1"),
                alex("synthetic-f5", "f", "11:05", "synthetic-conversation-1", "synthetic-app-1"),
                alex("synthetic-f6", "f", "11:00", "synthetic-conversation-2", "synthetic-app-2"),
                alex("synthetic-f7", "f", "11:05", "synthetic-conversation-2", "synthetic-app-2"),
            ),
            view = view,
        )

    private fun alex(
        id: String,
        caseSuffix: String,
        time: String,
        conversationId: String,
        app: String,
        configure: EventBuilder.() -> Unit = {},
    ): Event =
        contact(id, caseSuffix, time, "unused") {
            actor = null
            associationReview = AssociationReview.UNREVIEWED
            identityBasis = IdentityBasis.UNKNOWN
            displayLabel = "Alex"
            conversation = conversationId
            sourceApp = app
            configure()
        }

    /** All fixtures with their names, for tests that iterate over them. */
    public fun all(view: EvidenceView = EvidenceView.CONFIRMED_ONLY): Map<String, TemporalInput> =
        linkedMapOf(
            "A" to a(view),
            "B" to b(view),
            "C" to c(view),
            "D" to d(view),
            "E" to e(view),
            "F" to f(view),
            "F-two-alex" to fTwoAlex(view),
        )
}
