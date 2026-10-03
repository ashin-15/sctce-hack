package org.sakshi.app.screenshots

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.sakshi.app.people.AssignmentView
import org.sakshi.app.people.SenderClaimView
import org.sakshi.app.people.WhoIsWhoActions
import org.sakshi.app.people.WhoIsWhoScreen
import org.sakshi.app.people.WhoIsWhoState
import org.sakshi.app.review.CategoryView
import org.sakshi.app.review.CueMark
import org.sakshi.app.review.EventReviewActions
import org.sakshi.app.review.EventReviewScreen
import org.sakshi.app.review.EventReviewState
import org.sakshi.app.review.HistoryLine
import org.sakshi.app.timeline.BodyView
import org.sakshi.app.timeline.GapOutcome
import org.sakshi.app.timeline.SenderStatus
import org.sakshi.app.timeline.SenderView
import org.sakshi.app.timeline.TagKind
import org.sakshi.app.timeline.TagLine
import org.sakshi.app.timeline.TimeLabel
import org.sakshi.app.timeline.TimeReading
import org.sakshi.app.timeline.TimelineActions
import org.sakshi.app.timeline.TimelineEventRow
import org.sakshi.app.timeline.TimelineFilter
import org.sakshi.app.timeline.TimelineItem
import org.sakshi.app.timeline.TimelineScreen
import org.sakshi.app.timeline.TimelineUiState
import org.sakshi.app.timeline.TimelineView
import org.sakshi.app.ui.UiText
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.ArtifactId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.Boundary
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.BoundaryReviewStatus
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.CategoryAssessment
import org.sakshi.core.model.CategoryBasis
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.CommunicationStatus
import org.sakshi.core.model.Confidence
import org.sakshi.core.model.ConfidenceSemantics
import org.sakshi.core.model.ConfirmationScope
import org.sakshi.core.model.ConfirmationStatus
import org.sakshi.core.model.Coverage
import org.sakshi.core.model.CoverageContext
import org.sakshi.core.model.DedupStatus
import org.sakshi.core.model.Deduplication
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.EventKind
import org.sakshi.core.model.EventSender
import org.sakshi.core.model.EventSource
import org.sakshi.core.model.EvidenceReference
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.Locator
import org.sakshi.core.model.OutgoingCoverage
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.Representation
import org.sakshi.core.model.Retention
import org.sakshi.core.model.RetentionMode
import org.sakshi.core.model.ReviewPriority
import org.sakshi.core.model.ScopeId
import org.sakshi.core.model.SeverityAssessment
import org.sakshi.core.model.SeverityBasis
import org.sakshi.core.model.SourceKind
import org.sakshi.core.model.TextStatus
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.model.TimeBounds
import org.sakshi.core.model.TimePrecision
import org.sakshi.core.model.Timestamp
import org.sakshi.core.model.UnwantedContact
import org.sakshi.core.model.UserConfirmation
import org.sakshi.core.vault.SenderSelector
import org.sakshi.core.vault.StoredActor

/**
 * Renders the timeline, the event review and Who is who from invented state, next to [ScreenGallery]. These screens
 * normally read a vault; here the rows and the event are built by hand and nothing is saved or started. Opt-in like
 * [ScreenGallery]: run with `SAKSHI_SCREENSHOTS=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ScreenGalleryReview {
    private val harness = GalleryHarness()

    @get:Rule
    val rules = harness.rules

    private val zone = ZoneId.of("Asia/Kolkata")
    private val noTimeline = TimelineActions({}, {}, {}, {}, {}, { _, _, _ -> }, {}, {})
    private val noReview = EventReviewActions({}, {}, { _, _ -> }, {}, {}, {}, {}, { _, _ -> }, {}, {})
    private val noPeople = WhoIsWhoActions({}, {}, { _, _ -> }, { _, _ -> }, {}, {})

    private fun instant(text: String) = Instant.parse(text)

    private fun row(id: String, time: String, sender: String?, status: SenderStatus, direction: Direction, body: String, vararg tags: TagLine) =
        TimelineEventRow(
            eventId = id,
            time = TimeReading(TimeLabel.At(instant(time)), TimeBasis.SOURCE_CLAIM),
            orderNote = false,
            sender = SenderView(sender, status, if (status == SenderStatus.CONFIRMED) "Synthetic Alex" else null),
            direction = direction,
            body = BodyView.Message(body, body),
            tags = tags.toList(),
            needsReview = tags.any { it.kind == TagKind.SUGGESTION },
            tagged = tags.any { it.kind == TagKind.ACCEPTED || it.kind == TagKind.OWN },
        )

    private val timelineView = TimelineView(
        items = listOf(
            TimelineItem.DayHeader(java.time.LocalDate.of(2026, 9, 28)),
            TimelineItem.EventItem(
                row("e1", "2026-09-28T09:03:00Z", "synthetic-sam", SenderStatus.NOT_CONFIRMED, Direction.INCOMING, "you are an idiot, reply now", TagLine(TagKind.SUGGESTION, CategoryLabel.VERBAL_ABUSE)),
            ),
            TimelineItem.EventItem(
                row("e2", "2026-09-28T09:20:00Z", "synthetic-alex", SenderStatus.CONFIRMED, Direction.OUTGOING, "please stop messaging me", TagLine(TagKind.OWN, CategoryLabel.CONTACT_REQUEST)),
            ),
            TimelineItem.EventItem(
                row("e3", "2026-09-28T10:41:00Z", "synthetic-sam", SenderStatus.NOT_CONFIRMED, Direction.INCOMING, "why are you ignoring me", TagLine(TagKind.ACCEPTED, CategoryLabel.CONTROLLING_REQUEST), TagLine(TagKind.NOT_SURE, CategoryLabel.INTIMIDATION)),
            ),
            TimelineItem.GapItem("g1", instant("2026-09-28T12:00:00Z"), instant("2026-09-29T04:00:00Z")),
        ),
        total = 3,
        needsReview = 1,
    )

    @Test
    fun timelineWithMessages() = harness.shoot("timeline-events") {
        TimelineScreen(TimelineUiState(loaded = true, view = timelineView, zone = zone), GapOutcome.None, noTimeline)
    }

    @Test
    fun timelineNeedsReviewFilter() = harness.shoot("timeline-needs-review") {
        val view = timelineView.copy(items = timelineView.items.take(2), total = 3)
        TimelineScreen(TimelineUiState(loaded = true, filter = TimelineFilter.NEEDS_REVIEW, view = view, zone = zone), GapOutcome.None, noTimeline)
    }

    @Test
    fun timelineEmpty() = harness.shoot("timeline-empty") {
        TimelineScreen(TimelineUiState(loaded = true, zone = zone), GapOutcome.None, noTimeline)
    }

    private fun category(label: CategoryLabel, basis: CategoryBasis, status: CategoryReviewStatus) = CategoryAssessment(
        label = label,
        basis = basis,
        confidence = Confidence(null, ConfidenceSemantics.NOT_APPLICABLE, null),
        producerVersion = ScopeId("synthetic-producer-1"),
        evidenceReferenceIds = listOf(ReferenceId("ref-1")),
        reviewStatus = status,
    )

    private fun event(direction: Direction, categories: List<CategoryAssessment>) = Event(
        eventId = EventId("synthetic-e1"),
        caseId = CaseId("synthetic-case"),
        revision = 1,
        eventKind = EventKind.MESSAGE_OBSERVATION,
        observedAt = Timestamp("2026-09-28T09:03:00Z"),
        availableAt = Timestamp("2026-09-28T09:03:00Z"),
        timestamp = TimeBounds(Timestamp("2026-09-28T09:03:00Z"), Timestamp("2026-09-28T09:03:00Z"), TimeBasis.SOURCE_CLAIM, TimePrecision.MINUTE, null, null, null),
        sender = EventSender(ActorId("synthetic-actor"), "synthetic-sam", IdentityBasis.USER_ASSERTED, AssociationReview.CONFIRMED),
        source = EventSource(SourceKind.SELECTED_EXPORT, "synthetic-app", null, ScopeId("synthetic-conversation"), ScopeId("record-1"), ScopeId("synthetic-parser-1")),
        direction = direction,
        categories = categories,
        severity = SeverityAssessment(ReviewPriority.ORDINARY, SeverityBasis.UNKNOWN, emptyList()),
        evidenceReferences = listOf(
            EvidenceReference(ReferenceId("ref-1"), ArtifactId("synthetic-artifact"), "ab12cd34ef56".repeat(5) + "abcd", Representation.PRESERVED_IMPORT, Locator.WholeArtifact),
        ),
        userConfirmation = UserConfirmation(ConfirmationStatus.CONFIRMED, Timestamp("2026-09-28T09:03:00Z"), ConfirmationScope.PRESERVATION_AND_SELECTED_ANNOTATIONS),
        deduplication = Deduplication(DedupStatus.DISTINCT_OBSERVATION, null, ScopeId("synthetic-dedup-1")),
        coverage = Coverage(CoverageContext.COMPLETE_FOR_SELECTED_RANGE, TextStatus.AVAILABLE, OutgoingCoverage.INCLUDED_FOR_SELECTED_RANGE, emptyList()),
        boundary = Boundary(BoundaryMarker.NONE, null, BoundaryReviewStatus.NOT_APPLICABLE, CommunicationStatus.NOT_APPLICABLE, UnwantedContact.UNKNOWN),
        relationshipToPreviousEvents = emptyList(),
        retention = Retention(RetentionMode.CONFIRMED_VAULT, null, 0),
    )

    private val reviewBody = "you are an idiot, reply now"
    private val idiot = CueMark("ref-1", 8, 13, "idiot")

    private fun reviewState(direction: Direction = Direction.INCOMING) = EventReviewState(
        loaded = true,
        event = event(
            direction,
            listOf(
                category(CategoryLabel.VERBAL_ABUSE, CategoryBasis.RULE_SUGGESTION, CategoryReviewStatus.UNREVIEWED),
                category(CategoryLabel.EXPLICIT_THREAT, CategoryBasis.CLASSIFIER_SUGGESTION, CategoryReviewStatus.UNREVIEWED),
                category(CategoryLabel.CONTROLLING_REQUEST, CategoryBasis.RULE_SUGGESTION, CategoryReviewStatus.ACCEPTED),
                category(CategoryLabel.CONTACT_REQUEST, CategoryBasis.USER_TAG, CategoryReviewStatus.ACCEPTED),
            ),
        ),
        body = reviewBody,
        marks = listOf(idiot),
        categories = listOf(
            CategoryView(0, category(CategoryLabel.VERBAL_ABUSE, CategoryBasis.RULE_SUGGESTION, CategoryReviewStatus.UNREVIEWED), listOf(idiot)),
            CategoryView(1, category(CategoryLabel.EXPLICIT_THREAT, CategoryBasis.CLASSIFIER_SUGGESTION, CategoryReviewStatus.UNREVIEWED), emptyList()),
            CategoryView(2, category(CategoryLabel.CONTROLLING_REQUEST, CategoryBasis.RULE_SUGGESTION, CategoryReviewStatus.ACCEPTED), emptyList()),
            CategoryView(3, category(CategoryLabel.CONTACT_REQUEST, CategoryBasis.USER_TAG, CategoryReviewStatus.ACCEPTED), emptyList()),
        ),
        people = listOf(StoredActor(ActorId("synthetic-actor"), CaseId("synthetic-case"), "Synthetic Sam", IdentityBasis.USER_ASSERTED, AssociationReview.CONFIRMED)),
        history = listOf(
            HistoryLine(instant("2026-09-28T09:30:00Z"), UiText.Raw("You agreed: Controlling demand"), null),
            HistoryLine(instant("2026-09-28T09:31:00Z"), UiText.Raw("You disagreed: Intimidating wording"), UiText.Raw("Not enough context")),
        ),
    )

    @Test
    fun eventReview() = harness.shoot("review-event") { EventReviewScreen(reviewState(), zone, noReview) }

    @Test
    fun eventReviewOwnMessage() = harness.shoot("review-event-own") { EventReviewScreen(reviewState(Direction.OUTGOING), zone, noReview) }

    private val selector = SenderSelector("synthetic-sam", "synthetic-app", ScopeId("synthetic-conversation"))

    @Test
    fun whoIsWho() = harness.shoot("people-who-is-who") {
        WhoIsWhoScreen(
            WhoIsWhoState(
                loaded = true,
                claims = listOf(
                    SenderClaimView(selector, SourceKind.SELECTED_EXPORT, instant("2026-09-28T09:00:00Z"), 5, markedOwn = false, focused = true),
                    SenderClaimView(selector.copy(displayLabel = "synthetic-alex"), SourceKind.SELECTED_EXPORT, instant("2026-09-28T09:00:00Z"), 3, markedOwn = true, focused = false),
                ),
                assignments = listOf(
                    AssignmentView(selector.copy(displayLabel = "synthetic-jo"), ActorId("synthetic-actor"), "Synthetic Jo", listOf(EventId("e1"), EventId("e2")), SourceKind.SELECTED_TEXT, instant("2026-09-27T09:00:00Z")),
                ),
                people = listOf(StoredActor(ActorId("synthetic-actor"), CaseId("synthetic-case"), "Synthetic Jo", IdentityBasis.USER_ASSERTED, AssociationReview.CONFIRMED)),
            ),
            zone,
            noPeople,
        )
    }

    @Test
    fun whoIsWhoEmpty() = harness.shoot("people-empty") { WhoIsWhoScreen(WhoIsWhoState(loaded = true), zone, noPeople) }
}
