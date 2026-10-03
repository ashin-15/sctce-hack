package org.sakshi.core.vault

import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sakshi.core.database.EventRevisionEntity
import org.sakshi.core.database.FindingReviewStatus
import org.sakshi.core.database.SakshiDatabase
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.ConfirmationStatus
import org.sakshi.core.model.SourceKind

/** One occurrence with context around it. The match is `[matchStartInSnippet, matchEndInSnippet)` in UTF-16 units of [snippet]. */
public data class SearchMatch(
    val snippet: String,
    val matchStartInSnippet: Int,
    val matchEndInSnippet: Int,
)

/**
 * Matches in one derivative that fall inside one event, or inside no event when [eventId] is null (text that has not
 * been turned into events yet). A hit in a note the person wrote has [derivativeKind] [NOTE_DERIVATIVE_KIND] and the
 * note's evidence id as [derivativeId]; see [isNote]. The words in a note are the person's own statement, not
 * observed evidence. [actorLabel] is the person or sender name as stored; [timestamp] is the earliest time
 * the source gave for the event, or null when it is not known. Import time is never shown as message time.
 */
public data class SearchHit(
    val evidenceId: String,
    val derivativeId: String,
    val derivativeKind: String,
    val eventId: String?,
    val actorLabel: String?,
    val timestamp: String?,
    val matches: List<SearchMatch>,
) {
    /** Unique within one [SearchResults]. */
    val key: String get() = "$derivativeId/${eventId.orEmpty()}"

    /** True when the matches are in a note the person wrote rather than in text derived from evidence. */
    val isNote: Boolean get() = derivativeKind == NOTE_DERIVATIVE_KIND

    public companion object {
        /** The [derivativeKind] of a hit in a manual note. Not a stored derivative kind: notes have no derivative. */
        public const val NOTE_DERIVATIVE_KIND: String = "user_note"
    }
}

/** The searchable text of one manual note: the evidence id of the note and the person's own words. */
public class NoteText(public val evidenceId: String, public val text: String)

/**
 * Supplies the text of the manual notes of a case to [VaultEvidenceSearch]. The vault cannot decode notes itself
 * (the note format belongs to the importer), so the module that can supplies this. Implementations must return only
 * notes of [caseId] and only text the person wrote, never metadata fields; a note that cannot be read is left out.
 * The returned text is used for one search call and not kept.
 */
public interface NoteTextSource {
    public suspend fun texts(caseId: CaseId): List<NoteText>
}

/** [limitReached] means more matches exist than were returned; the list is not every occurrence. */
public data class SearchResults(val hits: List<SearchHit>, val limitReached: Boolean) {
    public companion object {
        public val EMPTY: SearchResults = SearchResults(emptyList(), false)
    }
}

/** Which text a search reads. */
public enum class SearchScope {
    /** Every preserved text derivative of the case, whether or not it has been reviewed. */
    ALL_PRESERVED_TEXT,

    /**
     * Only text inside events that have at least one accepted category in their newest revision. A category is
     * accepted when its review status is `accepted`: the person accepted a suggestion with
     * [ReviewCoordinator.reviewCategory], or added their own tag with [ReviewCoordinator.addUserTag] (stored as
     * accepted). Rejected, uncertain and unreviewed categories do not count, and a later rejection of the
     * category removes it again. Text that is not part of any event is never included.
     */
    ACCEPTED_FINDINGS_ONLY,
}

/**
 * Optional narrowing of a search. The default filters nothing. Every filter other than the date range and
 * [scope] reads the newest revision of the event the match falls in.
 *
 * Event-level filters are [from], [until], [actorId], [confirmation], [sourceKinds] and
 * [SearchScope.ACCEPTED_FINDINGS_ONLY]. While any of them is set, text that is not part of an event (a hit with a
 * null event id) is left out, because it has nothing to compare.
 *
 * Date range: both ends are inclusive. An event matches when its time bounds overlap `[from, until]`, that is
 * its latest time is not before [from] and its earliest time is not after [until]. The bounds are the event
 * revision's earliest and latest times; a missing end is taken from the other, so an event with one known time is
 * a point. An event whose time is not known at all matches only when neither [from] nor [until] is set. Import
 * time is never used instead.
 *
 * [actorId] matches the event's confirmed person. [confirmation] matches the event's user confirmation status.
 * [sourceKinds] matches the event's source kind; empty means all kinds.
 */
public data class SearchFilters(
    val from: Instant? = null,
    val until: Instant? = null,
    val actorId: ActorId? = null,
    val confirmation: ConfirmationStatus? = null,
    val sourceKinds: Set<SourceKind> = emptySet(),
    val scope: SearchScope = SearchScope.ALL_PRESERVED_TEXT,
) {
    /** True when the filters narrow nothing. */
    val isUnfiltered: Boolean get() = !requiresEvent

    internal val requiresEvent: Boolean
        get() = from != null || until != null || actorId != null || confirmation != null || sourceKinds.isNotEmpty() ||
            scope == SearchScope.ACCEPTED_FINDINGS_ONLY
}

/** Lexical search inside one case. Evidence of other cases is never read. */
public interface EvidenceSearch {
    /**
     * Case-insensitive search for [query] (trimmed) in the newest revision of each derivative kind of each evidence
     * item of the case. A blank query gives [SearchResults.EMPTY].
     */
    public suspend fun search(caseId: CaseId, query: String): SearchResults

    /**
     * Like [search] with [filters] applied before the match limit, so [SearchResults.limitReached] refers to
     * filtered matches. Filtered searching is optional: the default only serves unfiltered requests.
     *
     * @throws UnsupportedOperationException if [filters] narrow the search and the implementation cannot apply them.
     */
    public suspend fun search(caseId: CaseId, query: String, filters: SearchFilters): SearchResults {
        if (!filters.isUnfiltered) throw UnsupportedOperationException("Filtered search is not supported")
        return search(caseId, query)
    }
}

/** [EvidenceSearch] over the vault database. Plaintext is read per call and not kept. */
public class VaultEvidenceSearch(
    private val database: SakshiDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxMatches: Int = DEFAULT_MAX_MATCHES,
) : EvidenceSearch {
    @Volatile
    private var noteSource: NoteTextSource? = null

    /**
     * Makes the text of manual notes searchable by reading it through [source]; null (the default) turns it off
     * again. Without a source, search reads derivatives only.
     */
    public fun useNoteTextSource(source: NoteTextSource?) {
        noteSource = source
    }

    override suspend fun search(caseId: CaseId, query: String): SearchResults = search(caseId, query, SearchFilters())

    override suspend fun search(caseId: CaseId, query: String, filters: SearchFilters): SearchResults {
        val needle = query.trim()
        if (needle.isEmpty()) return SearchResults.EMPTY
        return withContext(dispatcher) {
            val derivatives = database.derivativeDao().getLatestInCase(caseId.value)
            val notes = noteSource
            if (derivatives.isEmpty() && notes == null) return@withContext SearchResults.EMPTY
            val events = database.eventDao()
            val latest = events.getLatestRevisions(caseId.value, Long.MAX_VALUE).associateBy { it.eventId }
            val actors = events.getActors(caseId.value).associate { it.id to it.displayLabel }
            val acceptedEvents = if (filters.scope == SearchScope.ACCEPTED_FINDINGS_ONLY) acceptedEventIds(caseId, latest) else emptySet()
            val allowedEvents = if (filters.requiresEvent) {
                latest.values.filter { matches(it, filters, acceptedEvents) }.mapTo(HashSet()) { it.eventId }
            } else {
                null
            }
            val currentAnchors = events.getAnchorsForCase(caseId.value)
                .filter { anchor -> latest[anchor.eventId]?.revision == anchor.eventRevision }
            val spansByArtifact = currentAnchors
                .mapNotNull { anchor ->
                    val start = anchor.startCp ?: return@mapNotNull null
                    val end = anchor.endCp ?: return@mapNotNull null
                    anchor.artifactId to EventSpan(anchor.eventId, start, end)
                }
                .groupBy({ it.first }, { it.second })

            val hits = ArrayList<SearchHit>()
            var found = 0
            var limitReached = false
            for (derivative in derivatives) {
                val spans = spansByArtifact[derivative.id].orEmpty()
                val grouped = LinkedHashMap<String?, MutableList<SearchMatch>>()
                for (occurrence in occurrences(derivative.text, needle)) {
                    val event = spans.filter { it.start < occurrence.endCp && occurrence.startCp < it.end }.minByOrNull { it.end - it.start }
                    if (allowedEvents != null && (event == null || event.eventId !in allowedEvents)) continue
                    if (found == maxMatches) {
                        limitReached = true
                        break
                    }
                    found++
                    grouped.getOrPut(event?.eventId) { mutableListOf() } += snippet(derivative.text, occurrence.start, occurrence.end)
                }
                for ((eventId, matches) in grouped) {
                    val revision = eventId?.let(latest::get)
                    hits += SearchHit(
                        evidenceId = derivative.evidenceId,
                        derivativeId = derivative.id,
                        derivativeKind = derivative.kind,
                        eventId = eventId,
                        actorLabel = revision?.let { labelOf(it, actors) },
                        timestamp = revision?.tsEarliest?.ifBlank { null },
                        matches = matches,
                    )
                }
                if (limitReached) break
            }
            if (notes != null && !limitReached) {
                val eventsByNote = currentAnchors
                    .mapNotNull { anchor -> anchor.evidenceId?.let { it to anchor.eventId } }
                    .groupBy({ it.first }, { it.second })
                val inCase = database.evidenceDao().getIdsForCase(caseId.value).toHashSet()
                for (note in notes.texts(caseId)) {
                    if (note.evidenceId !in inCase) continue
                    val candidates = eventsByNote[note.evidenceId].orEmpty().filter { allowedEvents == null || it in allowedEvents }
                    if (allowedEvents != null && candidates.isEmpty()) continue
                    val eventId = candidates.minOrNull()
                    val matches = ArrayList<SearchMatch>()
                    for (occurrence in occurrences(note.text, needle)) {
                        if (found == maxMatches) {
                            limitReached = true
                            break
                        }
                        found++
                        matches += snippet(note.text, occurrence.start, occurrence.end)
                    }
                    if (matches.isNotEmpty()) {
                        val revision = eventId?.let(latest::get)
                        hits += SearchHit(
                            evidenceId = note.evidenceId,
                            derivativeId = note.evidenceId,
                            derivativeKind = SearchHit.NOTE_DERIVATIVE_KIND,
                            eventId = eventId,
                            actorLabel = revision?.let { labelOf(it, actors) },
                            timestamp = revision?.tsEarliest?.ifBlank { null },
                            matches = matches,
                        )
                    }
                    if (limitReached) break
                }
            }
            SearchResults(hits, limitReached)
        }
    }

    /** Events whose newest revision has a category accepted by the person; read from this case's findings only. */
    private suspend fun acceptedEventIds(caseId: CaseId, latest: Map<String, EventRevisionEntity>): Set<String> =
        database.findingDao().getForCase(caseId.value)
            .filter { it.reviewStatusAtImport == FindingReviewStatus.ACCEPTED && latest[it.eventId]?.revision == it.eventRevision }
            .mapTo(HashSet()) { it.eventId }

    private fun matches(revision: EventRevisionEntity, filters: SearchFilters, acceptedEvents: Set<String>): Boolean {
        if (filters.actorId != null && revision.actorId != filters.actorId.value) return false
        if (filters.confirmation != null && revision.confirmationStatus != Codecs.confirmationStatus.name(filters.confirmation)) return false
        if (filters.sourceKinds.isNotEmpty() && revision.sourceKind !in filters.sourceKinds.map(Codecs.sourceKind::name)) return false
        if (filters.scope == SearchScope.ACCEPTED_FINDINGS_ONLY && revision.eventId !in acceptedEvents) return false
        return overlaps(revision, filters)
    }

    private fun overlaps(revision: EventRevisionEntity, filters: SearchFilters): Boolean {
        if (filters.from == null && filters.until == null) return true
        val earliest = revision.tsEarliestEpochMs ?: revision.tsLatestEpochMs ?: return false
        val latest = revision.tsLatestEpochMs ?: earliest
        val fromOk = filters.from?.let { latest >= it.toEpochMilli() } ?: true
        val untilOk = filters.until?.let { earliest <= it.toEpochMilli() } ?: true
        return fromOk && untilOk
    }

    private fun labelOf(revision: EventRevisionEntity, actors: Map<String, String?>): String? =
        revision.actorId?.let { actors[it] }?.ifBlank { null } ?: revision.senderDisplayLabel?.ifBlank { null }

    private class EventSpan(val eventId: String, val start: Int, val end: Int)

    /** UTF-16 range `[start, end)` and the same range in code points. */
    private class Occurrence(val start: Int, val end: Int, val startCp: Int, val endCp: Int)

    /** Non-overlapping case-insensitive occurrences, left to right, with code-point offsets counted in one pass. */
    private fun occurrences(text: String, needle: String): Sequence<Occurrence> = sequence {
        var from = 0
        var counted = 0
        var codePoints = 0
        while (from <= text.length - needle.length) {
            val at = text.indexOf(needle, from, ignoreCase = true)
            if (at < 0) break
            codePoints += text.codePointCount(counted, at)
            val length = text.codePointCount(at, at + needle.length)
            yield(Occurrence(at, at + needle.length, codePoints, codePoints + length))
            codePoints += length
            counted = at + needle.length
            from = counted
        }
    }

    private fun snippet(text: String, start: Int, end: Int): SearchMatch {
        val windowStart = safeBoundary(text, maxOf(0, start - CONTEXT_CHARS))
        val windowEnd = safeBoundary(text, minOf(text.length, end + CONTEXT_CHARS))
        val prefix = text.substring(windowStart, start).replace(WHITESPACE, " ").let { if (windowStart > 0) ELLIPSIS + it.trimStart() else it }
        val match = text.substring(start, end).replace(WHITESPACE, " ")
        val suffix = text.substring(end, windowEnd).replace(WHITESPACE, " ").let { if (windowEnd < text.length) it.trimEnd() + ELLIPSIS else it }
        return SearchMatch(prefix + match + suffix, prefix.length, prefix.length + match.length)
    }

    /** Moves [index] back by one when it would split a surrogate pair. */
    private fun safeBoundary(text: String, index: Int): Int =
        if (index in 1 until text.length && Character.isLowSurrogate(text[index]) && Character.isHighSurrogate(text[index - 1])) index - 1 else index

    private companion object {
        const val CONTEXT_CHARS: Int = 35
        const val DEFAULT_MAX_MATCHES: Int = 300
        const val ELLIPSIS: String = "\u2026"
        val WHITESPACE: Regex = Regex("\\s+")
    }
}
