package org.sakshi.core.temporal

import java.time.Duration
import java.time.Instant
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.BoundaryReviewStatus
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.CommunicationStatus
import org.sakshi.core.model.CoverageContext
import org.sakshi.core.model.UnwantedContact

private val BOUNDARY_MARKERS: Set<BoundaryMarker> =
    setOf(BoundaryMarker.DO_NOT_CONTACT, BoundaryMarker.USER_DISENGAGEMENT, BoundaryMarker.LIMITED_CONTACT)

private enum class TagState { ACCEPTED, UNREVIEWED }

private class BoundaryAnchor(val selected: Selected, val actorId: ActorId)

private fun ContactGroup.unwantedMark(): UnwantedContact = representative.event.boundary.unwantedContact

/** The four explicit demonstration rules. Each emits typed records; none yields a score or a prediction. */
internal class PatternReducer(
    private val input: TemporalInput,
    private val selected: List<Selected>,
    groups: List<ContactGroup>,
    private val projection: TemporalProjection,
) {
    private val config: PatternConfig = input.config
    private val factory = RecordFactory(input, projection)
    private val groupsByScope: Map<String, List<ContactGroup>> = groups.groupBy { it.scope.key }

    fun reduce(): List<PatternRecord> {
        val perScope =
            groupsByScope.values.flatMap { scoped ->
                repeatedContact(scoped) + wordingTransitions(scoped) + densityChanges(scoped)
            }
        return (perScope + recurrencesAfterBoundary()).sortedWith(patternOrder)
    }

    private fun contactParts(groups: List<ContactGroup>): Pair<List<Part>, List<Part>> {
        val supporting = groups.map { Part(it.representative, SupportRole.CONTACT) }
        return supporting to duplicateParts(groups)
    }

    private fun duplicateParts(groups: List<ContactGroup>): List<Part> =
        groups.flatMap { group -> group.members.drop(1).map { Part(it, SupportRole.POSSIBLE_DUPLICATE) } }

    private fun uncertainty(scoped: List<ContactGroup>, used: List<ContactGroup>): Set<Limitation> {
        val found = sortedSetOf<Limitation>()
        if (scoped.any { it.interval == null }) found += Limitation.TIME_UNCERTAIN
        if (used.any { it.clusterSize > 1 }) found += Limitation.DUPLICATE_UNCERTAINTY
        return found
    }

    // Rule 1

    private fun repeatedContact(scoped: List<ContactGroup>): List<PatternRecord> {
        val total = boundsOf(scoped)
        if (total.lower < config.repeatedContactMinimum) return emptyList()
        val scope = scoped.first().scope
        val timed = timedOf(scoped)
        val index = ContactIndex(timed)
        val first = timed.minOfOrNull { it.interval.earliest }
        val last = timed.maxOfOrNull { it.interval.latest }
        val window = if (first != null && last != null) Interval(first, last) else null
        val days = timed.map { it.interval.earliest.atZone(config.zone).toLocalDate() }.toSet().size
        val unwanted = scoped.count { it.unwantedMark() == UnwantedContact.USER_MARKED_UNWANTED }
        val measurements =
            Measurements.RepeatedContact(
                total = total,
                timedContacts = timed.size,
                firstAt = first,
                lastAt = last,
                span = window?.let { Duration.between(it.earliest, it.latest) },
                uniqueDays = days,
                episodes = projection.episodes(timed, scope),
                windowMaxima = config.countWindows.map { Measurements.WindowMaximum(it, index.maxWindow(it)) },
                markedUnwanted = unwanted,
                markedWanted = scoped.count { it.unwantedMark() == UnwantedContact.USER_MARKED_WANTED },
                allMarkedUnwanted = unwanted == scoped.size,
            )
        val (supporting, context) = contactParts(scoped)
        return listOf(
            factory.build(
                type = PatternType.REPEATED_CONTACT,
                keyTail = "all",
                scope = scope,
                window = window,
                gapWindow = window,
                supporting = supporting,
                context = context,
                measurements = measurements,
                status = AssessmentStatus.SUPPORTED_DESCRIPTION,
                extraLimitations = uncertainty(scoped, scoped) + Limitation.DEMO_THRESHOLD,
            ),
        )
    }

    // Rule 2

    private fun recurrencesAfterBoundary(): List<PatternRecord> {
        val anchors = selected.mapNotNull { boundaryAnchor(it) }
        val resumptions = selected.filter { it.event.boundary.marker == BoundaryMarker.USER_RESUMPTION }
        return anchors.map { recurrence(it, resumptions) }
    }

    private fun boundaryAnchor(entry: Selected): BoundaryAnchor? {
        val boundary = entry.event.boundary
        val actorId = boundary.actorId ?: return null
        val reviewed = boundary.reviewStatus == BoundaryReviewStatus.CONFIRMED
        return if (reviewed && boundary.marker in BOUNDARY_MARKERS) BoundaryAnchor(entry, actorId) else null
    }

    private fun recurrence(anchor: BoundaryAnchor, resumptions: List<Selected>): PatternRecord {
        val boundaryEvent = anchor.selected.event
        val scope = ActorScope.Confirmed(anchor.actorId)
        val scoped = groupsByScope[scope.key].orEmpty()
        val boundary = anchor.selected.interval
        val boundaryPart = Part(anchor.selected, SupportRole.BOUNDARY)
        val communication = boundaryEvent.boundary.communicationStatus
        val marker = boundaryEvent.boundary.marker
        val extra = boundaryLimitations(marker, communication)
        val keyTail = boundaryEvent.eventId.value
        if (boundary == null) {
            return factory.build(
                type = PatternType.RECURRENCE_AFTER_BOUNDARY,
                keyTail = keyTail,
                scope = scope,
                window = null,
                gapWindow = null,
                supporting = listOf(boundaryPart),
                context = emptyList(),
                measurements =
                    recurrenceMeasurements(
                        bounds = CountBounds(0, 0),
                        episodes = 0,
                        marker = marker,
                        communication = communication,
                        boundaryAt = null,
                        first = null,
                        last = null,
                        ended = false,
                        allUnwanted = false,
                    ),
                status = AssessmentStatus.INSUFFICIENT_CONTEXT,
                extraLimitations = extra + Limitation.TIME_UNCERTAIN,
            )
        }
        val resumption =
            resumptions
                .filter { r ->
                    r.event.boundary.reviewStatus == BoundaryReviewStatus.CONFIRMED &&
                        r.event.boundary.actorId == anchor.actorId &&
                        r.interval != null &&
                        boundary.definitelyBefore(r.interval)
                }
                .minWithOrNull(selectedOrder)
        val resumptionInterval = resumption?.interval
        val horizon = boundary.latest.plus(config.afterBoundaryWindow)
        val analysedEnd = resumptionInterval?.let { minOf(it.earliest, horizon) } ?: horizon
        val timed = timedOf(scoped)
        val wanted = mutableListOf<ContactGroup>()
        val possible = mutableListOf<TimedGroup>()
        for (entry in timed) {
            val interval = entry.interval
            val notBefore = !interval.definitelyBefore(boundary)
            val withinHorizon = interval.earliest <= horizon
            val beforeResumption = resumptionInterval == null || interval.earliest <= resumptionInterval.latest
            if (!(notBefore && withinHorizon && beforeResumption)) continue
            if (entry.group.unwantedMark() == UnwantedContact.USER_MARKED_WANTED) {
                wanted += entry.group
            } else {
                possible += entry
            }
        }
        val definite =
            possible.filter { entry ->
                val interval = entry.interval
                boundary.definitelyBefore(interval) &&
                    interval.latest <= horizon &&
                    (resumptionInterval == null || interval.latest < resumptionInterval.earliest)
            }
        val counted = possible.map { it.group }
        val bounds = CountBounds(definite.map { it.group.clusterKey }.toSet().size, counted.size)
        val firstCounted = possible.minOfOrNull { it.interval.earliest }
        val lastCounted = possible.maxOfOrNull { it.interval.latest }
        val window = if (firstCounted != null && lastCounted != null) Interval(firstCounted, lastCounted) else null
        val gapWindow = Interval(boundary.earliest, maxOf(boundary.earliest, analysedEnd))
        val gapInHorizon = projection.overlapping(scope, gapWindow.earliest, gapWindow.latest).isNotEmpty()
        val status =
            when {
                bounds.upper == 0 && gapInHorizon -> AssessmentStatus.INSUFFICIENT_CONTEXT
                bounds.upper == 0 -> AssessmentStatus.NOT_OBSERVED
                bounds.lower >= config.afterBoundaryMinimum -> AssessmentStatus.SUPPORTED_DESCRIPTION
                else -> AssessmentStatus.CANDIDATE
            }
        val thresholded = status == AssessmentStatus.SUPPORTED_DESCRIPTION || status == AssessmentStatus.CANDIDATE
        val uncertain = sortedSetOf<Limitation>()
        if (possible.size > definite.size || scoped.any { it.interval == null }) uncertain += Limitation.TIME_UNCERTAIN
        if (counted.any { it.clusterSize > 1 }) uncertain += Limitation.DUPLICATE_UNCERTAINTY
        if (thresholded) uncertain += Limitation.DEMO_THRESHOLD
        val supporting =
            listOf(boundaryPart) +
                counted.map { Part(it.representative, SupportRole.CONTACT) } +
                listOfNotNull(resumption?.let { Part(it, SupportRole.RESUMPTION) })
        val context =
            wanted.map { Part(it.representative, SupportRole.WANTED_CONTEXT) } + duplicateParts(counted)
        return factory.build(
            type = PatternType.RECURRENCE_AFTER_BOUNDARY,
            keyTail = keyTail,
            scope = scope,
            window = window,
            gapWindow = gapWindow,
            supporting = supporting,
            context = context,
            measurements =
                recurrenceMeasurements(
                    bounds = bounds,
                    episodes = projection.episodes(definite, scope),
                    marker = marker,
                    communication = communication,
                    boundaryAt = boundary.earliest,
                    first = firstCounted,
                    last = lastCounted,
                    ended = resumption != null,
                    allUnwanted =
                        counted.isNotEmpty() &&
                            counted.all { it.unwantedMark() == UnwantedContact.USER_MARKED_UNWANTED },
                ),
            status = status,
            extraLimitations = extra + uncertain,
        )
    }

    private fun recurrenceMeasurements(
        bounds: CountBounds,
        episodes: Int,
        marker: BoundaryMarker,
        communication: CommunicationStatus,
        boundaryAt: Instant?,
        first: Instant?,
        last: Instant?,
        ended: Boolean,
        allUnwanted: Boolean,
    ): Measurements.RecurrenceAfterBoundary =
        Measurements.RecurrenceAfterBoundary(
            afterBoundary = bounds,
            episodes = episodes,
            marker = marker,
            communication = communication,
            boundaryAt = boundaryAt,
            firstCountedAt = first,
            lastCountedAt = last,
            endedByResumption = ended,
            allMarkedUnwanted = allUnwanted,
        )

    private fun boundaryLimitations(marker: BoundaryMarker, communication: CommunicationStatus): Set<Limitation> {
        val found = sortedSetOf<Limitation>()
        val notShown =
            marker == BoundaryMarker.USER_DISENGAGEMENT ||
                communication == CommunicationStatus.NOT_COMMUNICATED ||
                communication == CommunicationStatus.UNKNOWN
        if (notShown) found += Limitation.BOUNDARY_NOT_COMMUNICATED
        if (communication == CommunicationStatus.USER_REPORTED) found += Limitation.BOUNDARY_USER_REPORTED
        if (communication == CommunicationStatus.SUPPORTED_BY_SELECTED_EVIDENCE) found += Limitation.DELIVERY_UNKNOWN
        return found
    }

    // Rule 3

    private fun ContactGroup.tag(label: CategoryLabel): TagState? {
        val reviews = representative.event.categories.filter { it.label == label }.map { it.reviewStatus }
        return when {
            CategoryReviewStatus.ACCEPTED in reviews -> TagState.ACCEPTED
            input.view == EvidenceView.CANDIDATE_PREVIEW && CategoryReviewStatus.UNREVIEWED in reviews ->
                TagState.UNREVIEWED
            else -> null
        }
    }

    private fun wordingTransitions(scoped: List<ContactGroup>): List<PatternRecord> {
        val timed = timedOf(scoped)
        val scope = scoped.first().scope
        val records = mutableListOf<PatternRecord>()
        for ((earlierLabel, laterLabel) in config.transitions) {
            val earlierSorted =
                timed
                    .filter { it.group.tag(earlierLabel) != null }
                    .sortedWith(compareBy({ it.interval.latest }, { it.group.id.value }))
            for (later in timed) {
                val laterTag = later.group.tag(laterLabel) ?: continue
                val earlier = nearestEarlier(earlierSorted, later) ?: continue
                val earlierTag = checkNotNull(earlier.group.tag(earlierLabel))
                val gap = Duration.between(earlier.interval.latest, later.interval.earliest)
                val accepted = laterTag == TagState.ACCEPTED && earlierTag == TagState.ACCEPTED
                val extra = sortedSetOf(Limitation.DEMO_THRESHOLD)
                if (!accepted) extra += Limitation.UNREVIEWED_TAGS
                if (scoped.any { it.interval == null }) extra += Limitation.TIME_UNCERTAIN
                if (earlier.group.clusterSize > 1 || later.group.clusterSize > 1) {
                    extra += Limitation.DUPLICATE_UNCERTAINTY
                }
                records +=
                    factory.build(
                        type = PatternType.WORDING_TRANSITION,
                        keyTail = "${earlierLabel.name}>${laterLabel.name}|${earlier.group.id.value}>${later.group.id.value}",
                        scope = scope,
                        window = Interval(earlier.interval.earliest, later.interval.latest),
                        gapWindow = Interval(earlier.interval.earliest, later.interval.latest),
                        supporting =
                            listOf(
                                Part(earlier.group.representative, SupportRole.EARLIER_CATEGORY),
                                Part(later.group.representative, SupportRole.LATER_CATEGORY),
                            ),
                        context = duplicateParts(listOf(earlier.group, later.group)),
                        measurements =
                            Measurements.WordingTransition(
                                earlier = earlierLabel,
                                later = laterLabel,
                                earlierAt = earlier.interval.earliest,
                                laterAt = later.interval.earliest,
                                gap = gap,
                            ),
                        status =
                            if (accepted) AssessmentStatus.SUPPORTED_DESCRIPTION else AssessmentStatus.CANDIDATE,
                        extraLimitations = extra,
                    )
            }
        }
        return records
    }

    /** Nearest definitely-earlier tagged contact within the window, ties broken by smallest event id. */
    private fun nearestEarlier(earlierSorted: List<TimedGroup>, later: TimedGroup): TimedGroup? {
        var low = 0
        var high = earlierSorted.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (earlierSorted[mid].interval.latest < later.interval.earliest) low = mid + 1 else high = mid
        }
        var best: TimedGroup? = null
        var index = low - 1
        while (index >= 0) {
            val candidate = earlierSorted[index]
            val currentBest = best
            if (currentBest != null && candidate.interval.latest != currentBest.interval.latest) break
            val gap = Duration.between(candidate.interval.latest, later.interval.earliest)
            if (gap > config.transitionWindow) break
            if (candidate.group.clusterKey != later.group.clusterKey) best = candidate
            index--
        }
        return best
    }

    // Rule 4

    private fun densityChanges(scoped: List<ContactGroup>): List<PatternRecord> {
        val timed = timedOf(scoped)
        if (timed.isEmpty()) return emptyList()
        val scope = scoped.first().scope
        val index = ContactIndex(timed)
        val size = config.densityBin
        val currentStarts =
            timed
                .mapNotNull { entry ->
                    val start = projection.binStart(entry.interval.earliest)
                    start.takeIf { it == projection.binStart(entry.interval.latest) }
                }
                .distinct()
                .sorted()
        val records = mutableListOf<PatternRecord>()
        for (currentStart in currentStarts) {
            val currentEnd = currentStart.plus(size)
            val current = index.countIn(currentStart, currentEnd)
            if (current.lower < config.densityMinimumCount) continue
            val previousStart = projection.binStart(currentStart.minusSeconds(1))
            val previous = index.countIn(previousStart, currentStart)
            if (previous.upper == 0 || current.lower < config.densityMinimumRatio * previous.upper) continue
            val inPrevious = index.possiblyIn(previousStart, currentStart).map { it.group }
            val inCurrent = index.possiblyIn(currentStart, currentEnd).map { it.group }
            val window = Interval(previousStart, currentEnd)
            val gapWindow = Interval(previousStart, currentEnd.minusNanos(1))
            val gapped = projection.overlapping(scope, gapWindow.earliest, gapWindow.latest).isNotEmpty()
            val incomplete =
                (inPrevious + inCurrent).any { group ->
                    group.members.any { it.event.coverage.context != CoverageContext.COMPLETE_FOR_SELECTED_RANGE }
                }
            val insufficient = gapped || incomplete
            val extra = sortedSetOf<Limitation>()
            if (scoped.any { it.interval == null }) extra += Limitation.TIME_UNCERTAIN
            if ((inPrevious + inCurrent).any { it.clusterSize > 1 }) extra += Limitation.DUPLICATE_UNCERTAINTY
            if (!insufficient) extra += Limitation.DEMO_THRESHOLD
            records +=
                factory.build(
                    type = PatternType.DENSITY_CHANGE,
                    keyTail = currentStart.toString(),
                    scope = scope,
                    window = window,
                    gapWindow = gapWindow,
                    supporting =
                        inPrevious.map { Part(it.representative, SupportRole.PREVIOUS_BIN) } +
                            inCurrent.map { Part(it.representative, SupportRole.CURRENT_BIN) },
                    context = duplicateParts(inPrevious + inCurrent),
                    measurements =
                        Measurements.DensityChange(
                            previous = previous,
                            current = current,
                            previousBinStart = previousStart,
                            currentBinStart = currentStart,
                            binSize = size,
                        ),
                    status =
                        if (insufficient) {
                            AssessmentStatus.INSUFFICIENT_CONTEXT
                        } else {
                            AssessmentStatus.SUPPORTED_DESCRIPTION
                        },
                    extraLimitations = extra,
                )
        }
        return records
    }
}

private fun compareNullsLastInstant(a: Instant?, b: Instant?): Int =
    when {
        a == null && b == null -> 0
        a == null -> 1
        b == null -> -1
        else -> a.compareTo(b)
    }

internal val patternOrder: Comparator<PatternRecord> =
    Comparator<PatternRecord> { a, b -> a.type.compareTo(b.type) }
        .thenComparing { record -> record.actorScope.key }
        .thenComparing { a, b -> compareNullsLastInstant(a.windowStart, b.windowStart) }
        .thenComparing { record -> record.patternKey }
