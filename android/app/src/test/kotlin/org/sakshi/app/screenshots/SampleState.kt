package org.sakshi.app.screenshots

import android.net.Uri
import java.time.Instant
import java.time.ZoneId
import org.sakshi.acquisition.importer.AnalysisState
import org.sakshi.acquisition.importer.ImportFailure
import org.sakshi.acquisition.importer.ImportMechanism
import org.sakshi.acquisition.importer.ImportReport
import org.sakshi.acquisition.importer.ItemKind
import org.sakshi.acquisition.importer.ItemOutcome
import org.sakshi.acquisition.importer.PendingBatch
import org.sakshi.acquisition.importer.PendingItem
import org.sakshi.acquisition.importer.Rejection
import org.sakshi.app.evidence.EvidenceKind
import org.sakshi.app.evidence.EvidenceRow
import org.sakshi.app.patterns.PatternCardView
import org.sakshi.app.patterns.SupportItem
import org.sakshi.app.timeline.TimeLabel
import org.sakshi.app.timeline.TimeReading
import org.sakshi.app.ui.UiText
import org.sakshi.core.database.CaseStatus
import org.sakshi.core.database.SupportState
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.temporal.AssessmentStatus
import org.sakshi.core.temporal.PatternType
import org.sakshi.core.vault.CaseSummary
import org.sakshi.core.vault.PatternReview
import org.sakshi.core.vault.ReviewReason

/** Invented cases, evidence and patterns. Every name and sentence is synthetic and none is real-world evidence. */
internal object SampleState {
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private val monday: Instant = Instant.parse("2026-09-28T15:30:00Z")

    fun case(id: String, title: String, count: Int, archived: Boolean = false) = CaseSummary(
        id = id,
        title = title,
        status = if (archived) CaseStatus.ARCHIVED else CaseStatus.ACTIVE,
        createdAt = "2026-09-2${count % 9}T10:00:00Z",
        evidenceCount = count,
    )

    val activeCases = listOf(
        case("c1", "synthetic case one", 12),
        case("c2", "Messages from synthetic-sam", 3),
        case("c3", "synthetic - സാക്ഷി കേസ്", 0),
    )

    val archivedCases = listOf(
        case("c4", "synthetic old case", 7, archived = true),
        case("c5", "synthetic - साक्षी मामला", 2, archived = true),
    )

    val evidenceRows = listOf(
        EvidenceRow("e1", "2026-09-24T10:00:00Z", 1_820, EvidenceKind.TEXT, SupportState.ANALYZED),
        EvidenceRow("e2", "2026-09-25T10:00:00Z", 48_210, EvidenceKind.TEXT_FILE, SupportState.SAVED),
        EvidenceRow("e3", "2026-09-26T10:00:00Z", 2_411_000, EvidenceKind.IMAGE, SupportState.PARTIAL),
        EvidenceRow("e4", "2026-09-26T11:00:00Z", 6_300_000, EvidenceKind.AUDIO),
        EvidenceRow("e5", "2026-09-27T10:00:00Z", 38_000_000, EvidenceKind.VIDEO),
        EvidenceRow("e6", "2026-09-27T12:00:00Z", 310_000, EvidenceKind.PDF),
        EvidenceRow("e7", "2026-09-28T09:00:00Z", 540, EvidenceKind.NOTE),
        EvidenceRow("e8", "2026-09-28T10:00:00Z", 1_100_000, EvidenceKind.ARCHIVE),
        EvidenceRow("e9", "2026-09-28T11:00:00Z", 90_000, EvidenceKind.FILE),
    )

    val pendingBatch = PendingBatch(
        ImportMechanism.SHARE_SEND_MULTIPLE,
        listOf(
            PendingItem.Text(0, "synthetic-sam: why do you ignore me\nsynthetic-sam: എന്തുകൊണ്ട് മറുപടി തരുന്നില്ല"),
            PendingItem.Stream(
                1,
                Uri.parse("content://synthetic.authority/chat-export"),
                "text/plain",
                "synthetic-chat.txt",
                48_210L,
                ItemKind.TEXT_FILE,
                "synthetic.authority",
            ),
            PendingItem.Stream(
                2,
                Uri.parse("content://synthetic.authority/picture"),
                "image/png",
                "synthetic-screenshot.png",
                2_411_000L,
                ItemKind.IMAGE,
                "synthetic.authority",
            ),
            PendingItem.Rejected(3, Rejection.UNREADABLE),
        ),
        referrerClaim = "synthetic-app",
    )

    val report = ImportReport(
        listOf(
            ItemOutcome.Saved(0, "e1", "00".repeat(32), 120, "text/plain", "text/plain", AnalysisState.READY_FOR_TEXT_ANALYSIS, emptyList()),
            ItemOutcome.Saved(1, "e2", "11".repeat(32), 48_210, "text/plain", "text/plain", AnalysisState.READY_FOR_TEXT_ANALYSIS, listOf("e0")),
            ItemOutcome.Saved(2, "e3", "22".repeat(32), 2_411_000, "image/png", "image/png", AnalysisState.PRESERVED_NOT_ANALYSED, emptyList()),
            ItemOutcome.Failed(3, ImportFailure.UNREADABLE),
            ItemOutcome.Skipped(4, Rejection.TOO_MANY_ITEMS),
        ),
    )

    private fun support(id: String, minutesAfter: Long, text: String?) =
        SupportItem(id, TimeReading(TimeLabel.At(monday.plusSeconds(minutesAfter * 60)), TimeBasis.SOURCE_CLAIM), text)

    private val supportItems = listOf(
        support("s1", 0, "why do you ignore me"),
        support("s2", 2, "answer me"),
        support("s3", 5, "reply now"),
        support("s4", 9, null),
    )

    private val limitations = listOf(
        "Only the messages you imported are counted.",
        "Messages sent outside this chat are not known.",
    )

    private fun card(key: String, type: PatternType, status: AssessmentStatus, review: PatternReview, reviewable: Boolean, reason: String? = null) =
        PatternCardView(
            key = key,
            type = type,
            status = status,
            title = UiText.Raw("Repeated messages after you asked for no contact"),
            statusText = UiText.Raw("Supported description"),
            observed = "6 messages from synthetic-sam in 30 minutes, after a message from you on 24 September that asked them to stop.",
            interpretation = "The messages continue after the request. This describes the records only.",
            limitations = limitations,
            support = supportItems,
            storedId = key,
            review = review,
            reviewReason = reason,
            reviewable = reviewable,
        )

    val patternCards = listOf(
        card("p1", PatternType.RECURRENCE_AFTER_BOUNDARY, AssessmentStatus.SUPPORTED_DESCRIPTION, PatternReview.NOT_REVIEWED, reviewable = true),
        card("p2", PatternType.REPEATED_CONTACT, AssessmentStatus.SUPPORTED_DESCRIPTION, PatternReview.ACCEPTED, reviewable = true),
        card("p3", PatternType.WORDING_TRANSITION, AssessmentStatus.CANDIDATE, PatternReview.REJECTED, reviewable = true, reason = ReviewReason.SIGNAL_ABSENT),
        card("p4", PatternType.DENSITY_CHANGE, AssessmentStatus.INSUFFICIENT_CONTEXT, PatternReview.MARKED_UNKNOWN, reviewable = true),
        card("p5", PatternType.REPEATED_CONTACT, AssessmentStatus.NOT_OBSERVED, PatternReview.NOT_REVIEWED, reviewable = false),
    )
}
