package org.sakshi.export.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.model.CategoryBasis
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.Confidence
import org.sakshi.core.model.ConfidenceSemantics
import org.sakshi.core.model.EpistemicStatus
import org.sakshi.core.model.EventId
import org.sakshi.core.temporal.fixtures.SyntheticTimelines
import org.sakshi.core.vault.DecisionTargets
import org.sakshi.core.vault.ReviewReason
import org.sakshi.core.vault.ReviewResult

class ReportBuilderTest : ReportTestBase() {
    private fun build(selection: ReportSelection, options: ReportOptions = ReportOptions()) =
        runBlocking { builder().build(selection, options) }

    @Test
    fun timelineAHasTheStructureOfMegaplan201() {
        val input = SyntheticTimelines.a()
        store(input)
        val model = built(build(selectAll(input))).model
        assertEquals("synthetic case title", model.title)
        assertEquals(7, model.timeline.size)
        assertEquals(7, model.events.size)
        assertEquals(listOf("synthetic-a0", "synthetic-a1", "synthetic-a2"), model.events.take(3).map { it.eventId })
        assertTrue(model.timeline.all { it.timeBasis == "as written in the export, to the minute" })
        assertEquals("1 Oct 2026, 09:00", model.timeline.first().timeText)
        assertEquals("confirmed by you, not an authenticated identity", model.timeline.last().identityBasis)
        assertTrue(model.events.all { it.observed.isNotEmpty() })
        assertTrue(model.events.flatMap { it.observed }.all { it.status == EpistemicStatus.OBSERVED })
        assertEquals("the whole saved item", model.events.first().observed.first().locator)
        val boundary = model.patterns.single { it.heading == "Contact after a boundary" }
        assertEquals(EpistemicStatus.PATTERN, boundary.status)
        assertEquals("description supported by the selected records", boundary.assessment)
        assertTrue(boundary.observed.contains("6"))
        assertTrue(boundary.limitations.isNotEmpty())
        assertTrue(model.patterns.any { it.heading == "Repeated contact" })
        assertTrue(model.integrity.limits == ReportText.LIMITS)
        assertTrue(model.integrity.derivativeNote.contains("not the evidence itself"))
        assertEquals(64, model.integrity.auditChainHead.length)
    }

    @Test
    fun patternSentencesUseTheSelectionsZone() {
        val input = SyntheticTimelines.a()
        store(input)
        val model = built(build(selectAll(input))).model
        val boundary = model.patterns.single { it.heading == "Contact after a boundary" }
        val repeated = model.patterns.single { it.heading == "Repeated contact" }
        assertTrue("+05:30" in repeated.observed, repeated.observed)
        assertTrue(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2} \\+05:30").containsMatchIn(repeated.observed + boundary.observed))
        assertFalse(Regex("\\d{4}-\\d{2}-\\d{2}T").containsMatchIn(repeated.observed + boundary.observed))
    }

    @Test
    fun timelineBShowsAChangeInWording() {
        val input = SyntheticTimelines.b()
        store(input)
        val model = built(build(selectAll(input))).model
        val change = model.patterns.single { it.heading == "Change in wording" }
        assertTrue(change.observed.contains("verbal abuse"))
        val tags = model.events.flatMap { it.inferred }
        assertEquals(3, tags.size)
        assertTrue(tags.all { it.status == EpistemicStatus.USER_REPORTED })
    }

    @Test
    fun boundaryStatementsOfTheUserAreKeptApartFromObservations() {
        val input = SyntheticTimelines.d()
        store(input)
        val model = built(build(selectAll(input))).model
        val note = model.events.single { it.eventId == "synthetic-d0" }
        assertTrue(note.observed.isEmpty())
        assertEquals(1, note.userStatements.size)
        assertEquals(EpistemicStatus.USER_REPORTED, note.userStatements.single().status)
        assertTrue(model.events.filter { it.eventId != "synthetic-d0" }.all { it.userStatements.isEmpty() })
    }

    private fun threeTags(input: org.sakshi.core.temporal.TemporalInput) = input.events.map { event ->
        if (event.eventId.value != "synthetic-b1") return@map event
        val template = event.categories.first().copy(
            basis = CategoryBasis.RULE_SUGGESTION,
            confidence = Confidence(0.7, ConfidenceSemantics.UNCALIBRATED_BOUNDED_SCORE, null),
        )
        event.copy(
            categories = listOf(
                template.copy(label = CategoryLabel.VERBAL_ABUSE, reviewStatus = CategoryReviewStatus.ACCEPTED),
                template.copy(label = CategoryLabel.IMPLIED_THREAT, reviewStatus = CategoryReviewStatus.UNREVIEWED),
                template.copy(label = CategoryLabel.INTIMIDATION, reviewStatus = CategoryReviewStatus.REJECTED),
            ),
        )
    }

    @Test
    fun onlyAcceptedTagsAreListedUnlessUnreviewedSuggestionsAreRequested() {
        val input = SyntheticTimelines.b()
        store(input, threeTags(input))
        val off = built(build(selectAll(input)))
        val block = off.model.events.single { it.eventId == "synthetic-b1" }
        assertEquals(listOf("Verbal abuse"), block.inferred.map { it.label })
        assertEquals(EpistemicStatus.INFERRED, block.inferred.single().status)
        assertEquals("accepted by you", block.inferred.single().reviewStatus)
        assertTrue(block.inferred.single().confidence.contains("not a probability"))
        assertTrue(block.unreviewed.isEmpty())
        assertEquals(1, off.bundleInputs.findings.count { it.eventId == "synthetic-b1" })
        assertTrue(off.model.unknowns.any { it.startsWith("1 suggestions you have not accepted") })

        val on = built(build(selectAll(input), ReportOptions(includeUnreviewedSuggestions = true)))
        val withOption = on.model.events.single { it.eventId == "synthetic-b1" }
        assertEquals(listOf("Implied threat"), withOption.unreviewed.map { it.label })
        assertEquals(EpistemicStatus.INFERRED, withOption.unreviewed.single().status)
        assertEquals("not reviewed by you", withOption.unreviewed.single().reviewStatus)
        val labels = on.bundleInputs.findings.filter { it.eventId == "synthetic-b1" }.map { it.label }
        assertEquals(listOf("verbal_abuse", "implied_threat"), labels)
        assertTrue(on.bundleInputs.findings.none { it.label == "intimidation" && it.eventId == "synthetic-b1" })
        assertTrue(on.model.unknowns.none { it.contains("have not accepted") })
        assertEquals(setOf(EpistemicStatus.INFERRED), on.bundleInputs.findings.filter { it.eventId == "synthetic-b1" }.map { it.epistemicStatus }.toSet())
    }

    @Test
    fun patternsCiteOnlySelectedEvents() {
        val input = SyntheticTimelines.a()
        store(input)
        val chosen = arrayOf("synthetic-a0", "synthetic-a1", "synthetic-a2", "synthetic-a3")
        val result = built(build(select(input, *chosen)))
        val allowed = chosen.toSet()
        assertTrue(result.model.patterns.flatMap { it.supportingEventIds }.all { it in allowed })
        assertTrue(result.bundleInputs.patterns.flatMap { p -> (p.supporting + p.context).map { it.eventId } }.all { it in allowed })
        val text = result.model.patterns.joinToString(" ") { it.observed + it.interpretation.orEmpty() }
        assertTrue(text.contains("3"))
        assertEquals(chosen.toSet(), result.bundleInputs.events.map { it.eventId.value }.toSet())

        val noBoundary = built(build(select(input, "synthetic-a1", "synthetic-a2", "synthetic-a3")))
        assertTrue(noBoundary.model.patterns.none { it.supportingEventIds.contains("synthetic-a0") })
        assertTrue(noBoundary.model.patterns.none { it.heading == "Contact after a boundary" && it.assessment.startsWith("description") })
    }

    @Test
    fun selectionsThatCannotBeExportedAreRefused() {
        val input = SyntheticTimelines.a()
        store(input)
        val pending = SyntheticTimelines.a().events.first().let { _ ->
            org.sakshi.core.temporal.fixtures.syntheticEvent("synthetic-pending") {
                caseId = input.caseId.value
                at("2026-10-01T10:00:00+05:30")
                pending()
            }
        }
        runBlocking { vault.events.save(pending) }
        val refusal = { selection: ReportSelection -> (build(selection) as ReportBuildResult.Refused).reason }
        assertEquals(RefusalReason.EMPTY_SELECTION, refusal(select(input)))
        assertEquals(RefusalReason.UNKNOWN_EVENT, refusal(select(input, "synthetic-a1", "synthetic-nope")))
        assertEquals(RefusalReason.UNCONFIRMED_EVENT, refusal(select(input, "synthetic-a1", "synthetic-pending")))
        assertEquals(RefusalReason.CASE_MISSING, refusal(select(input, "synthetic-a1").copy(caseId = org.sakshi.core.model.CaseId("synthetic-none"))))
        assertEquals(RefusalReason.UNKNOWN_EVIDENCE, refusal(select(input, "synthetic-a1", originals = setOf("synthetic-evidence"))))
    }

    @Test
    fun unknownsAreBuiltFromTheData() {
        val input = SyntheticTimelines.d()
        val untimed = org.sakshi.core.temporal.fixtures.syntheticEvent("synthetic-d9") {
            caseId = input.caseId.value
            actor = "synthetic-actor-d"
            untimed()
            observedAt = "2026-10-01T10:00:00+05:30"
        }
        store(input, input.events + untimed)
        gap(input.caseId, "2026-10-01T11:00:00+05:30", "2026-10-02T09:00:00+05:30")
        gap(input.caseId, null, null)
        val everything = select(input, *(input.events + untimed).map { it.eventId.value }.toTypedArray())
        val subset = everything.copy(eventIds = everything.eventIds.drop(1).toSet())
        val model = built(build(subset, ReportOptions(unsupportedLanguageItems = 2))).model
        val unknowns = model.unknowns
        assertTrue(unknowns.contains(ReportText.UNKNOWN_SENDER))
        assertTrue(unknowns.any { it == "No records cover the period from 1 Oct 2026, 11:00 to 2 Oct 2026, 09:00 (reason not recorded)." } || unknowns.any { it.startsWith("No records cover the period from 1 Oct 2026, 11:00") })
        assertTrue(unknowns.any { it == "No records cover an unknown period (reason not recorded)." || it.startsWith("No records cover an unknown period") })
        assertTrue(unknowns.contains("2 items are in a language this app does not support."))
        assertTrue(unknowns.contains("1 records have no established time."))
        assertTrue(unknowns.contains("1 other records of this case are not part of this report."))
        assertFalse(unknowns.any { it.contains("null") })
    }

    @Test
    fun omittedCountsAreHonest() {
        val input = SyntheticTimelines.a()
        store(input)
        val result = built(build(select(input, "synthetic-a1", "synthetic-a2")))
        assertEquals(5, result.bundleInputs.omitted.eventCount)
        assertEquals(0, result.bundleInputs.omitted.evidenceCount)
        assertTrue(result.model.scope.lines.contains("2 of 7 records in this case are included in this report."))
    }

    @Test
    fun reviewDecisionsBecomeCorrectionsAndRejectedTagsAreNotPrinted() {
        val input = SyntheticTimelines.b()
        store(input)
        val reviewed = runBlocking {
            vault.review.reviewCategory(EventId("synthetic-b1"), 0, CategoryReviewStatus.REJECTED, ReviewReason.SIGNAL_ABSENT)
        }
        assertTrue(reviewed is ReviewResult.Applied)
        val result = built(build(selectAll(input)))
        val correction = result.bundleInputs.corrections.single()
        assertEquals(DecisionTargets.category(EventId("synthetic-b1"), 2, 0), correction.targetId)
        assertEquals("signal_absent", correction.reasonCode)
        assertTrue(result.model.events.single { it.eventId == "synthetic-b1" }.inferred.isEmpty())
        assertTrue(result.bundleInputs.findings.none { it.eventId == "synthetic-b1" })
        assertEquals(2, result.bundleInputs.events.single { it.eventId.value == "synthetic-b1" }.revision)
    }
}
