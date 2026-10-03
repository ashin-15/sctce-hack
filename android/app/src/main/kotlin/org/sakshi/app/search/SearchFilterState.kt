package org.sakshi.app.search

import android.content.res.Resources
import androidx.annotation.StringRes
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.sakshi.app.R
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.SourceKind
import org.sakshi.core.vault.SearchFilters
import org.sakshi.core.vault.SearchScope

/**
 * Where a message came from, in the words the review screen already uses. The review screen names three kinds and
 * calls everything else "A saved item", so [OTHER_ITEM] stands for every other [SourceKind].
 */
enum class SourceChoice(val kinds: Set<SourceKind>, @StringRes val label: Int) {
    EXPORT(setOf(SourceKind.SELECTED_EXPORT), R.string.review_source_export),
    TEXT(setOf(SourceKind.SELECTED_TEXT), R.string.review_source_text),
    NOTE(setOf(SourceKind.MANUAL_ENTRY), R.string.review_source_note),
    OTHER_ITEM(
        setOf(
            SourceKind.NOTIFICATION_EXCERPT,
            SourceKind.SELECTED_IMAGE,
            SourceKind.SELECTED_AUDIO,
            SourceKind.SELECTED_VIDEO,
            SourceKind.SELECTED_DOCUMENT,
        ),
        R.string.review_source_other,
    ),
}

/** A person of the case that the search can be narrowed to. */
data class PersonChoice(val id: ActorId, val label: String)

/** What the person typed in a date box, read strictly. */
sealed interface DayInput {
    data object Empty : DayInput

    data class Valid(val day: LocalDate) : DayInput

    /** Not a real calendar day in day/month/year form. */
    data object Invalid : DayInput
}

/** What is wrong with the dates, if anything. */
enum class DateProblem { FROM_INVALID, UNTIL_INVALID, FROM_AFTER_UNTIL }

/**
 * Reads a day typed as day, month and four-digit year, separated by "/", "-" or "." (for example 03/10/2026).
 * Anything else, including a day that does not exist such as 31/02/2026, is [DayInput.Invalid]; nothing is guessed.
 */
fun parseDay(text: String): DayInput {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return DayInput.Empty
    val match = DAY_PATTERN.matchEntire(trimmed) ?: return DayInput.Invalid
    return try {
        DayInput.Valid(LocalDate.of(match.groupValues[4].toInt(), match.groupValues[3].toInt(), match.groupValues[1].toInt()))
    } catch (_: DateTimeException) {
        DayInput.Invalid
    }
}

/** Day, separator, month, the same separator again, four-digit year. */
private val DAY_PATTERN = Regex("""(\d{1,2})([/.-])(\d{1,2})\2(\d{4})""")

private val SUMMARY_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")

/**
 * Every filter the person can set, as typed. Held in the view model so it survives opening a result and coming back.
 * Dates stay as text so a half-typed date is not lost; they are read with [parseDay].
 */
data class SearchFilterState(
    val scope: SearchScope = SearchScope.ALL_PRESERVED_TEXT,
    val personId: ActorId? = null,
    val sources: Set<SourceChoice> = emptySet(),
    val fromText: String = "",
    val untilText: String = "",
) {
    val from: DayInput get() = parseDay(fromText)

    val until: DayInput get() = parseDay(untilText)

    val dateProblem: DateProblem?
        get() {
            val from = from
            val until = until
            return when {
                from is DayInput.Invalid -> DateProblem.FROM_INVALID
                until is DayInput.Invalid -> DateProblem.UNTIL_INVALID
                from is DayInput.Valid && until is DayInput.Valid && from.day.isAfter(until.day) -> DateProblem.FROM_AFTER_UNTIL
                else -> null
            }
        }

    /** True when any filter narrows the search, including a date that is typed but not valid. */
    val isActive: Boolean
        get() = scope != SearchScope.ALL_PRESERVED_TEXT || personId != null || sources.isNotEmpty() ||
            fromText.isNotBlank() || untilText.isNotBlank()

    /**
     * What the library is asked for. "From" is the start of that day and "until" the last millisecond of that day, in
     * [zone], so both days are included. Null while the dates have a [dateProblem], because nothing sensible can be asked.
     */
    fun toSearchFilters(zone: ZoneId): SearchFilters? {
        if (dateProblem != null) return null
        val from = (from as? DayInput.Valid)?.day?.atStartOfDay(zone)?.toInstant()
        val until = (until as? DayInput.Valid)?.day?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.minusMillis(1)
        return SearchFilters(
            from = from,
            until = until,
            actorId = personId,
            sourceKinds = sources.flatMapTo(HashSet()) { it.kinds },
            scope = scope,
        )
    }

    /** True when a date is set, so a message with no known time cannot match. */
    val hasDates: Boolean get() = fromText.isNotBlank() || untilText.isNotBlank()
}

/**
 * One line naming the filters in force, for showing beside the results. Says "none" when nothing narrows the search.
 * It names filters; it never says how many messages they leave.
 */
fun filterSummary(filters: SearchFilterState, people: List<PersonChoice>, resources: Resources): String {
    val parts = buildList {
        if (filters.scope == SearchScope.ACCEPTED_FINDINGS_ONLY) add(resources.getString(R.string.search_scope_accepted))
        filters.personId?.let { id ->
            val label = people.firstOrNull { it.id == id }?.label ?: resources.getString(R.string.search_summary_person_unknown)
            add(resources.getString(R.string.search_summary_person, label))
        }
        if (filters.sources.isNotEmpty()) {
            val names = SourceChoice.entries.filter { it in filters.sources }.joinToString(", ") { resources.getString(it.label) }
            add(resources.getString(R.string.search_summary_source, names))
        }
        dateSummary(filters, resources)?.let(::add)
    }
    return if (parts.isEmpty()) {
        resources.getString(R.string.search_summary_none)
    } else {
        resources.getString(R.string.search_summary, parts.joinToString("; "))
    }
}

private fun dateSummary(filters: SearchFilterState, resources: Resources): String? {
    val from = (filters.from as? DayInput.Valid)?.day?.format(SUMMARY_DAY)
    val until = (filters.until as? DayInput.Valid)?.day?.format(SUMMARY_DAY)
    return when {
        from != null && until != null -> resources.getString(R.string.search_summary_dates_between, from, until)
        from != null -> resources.getString(R.string.search_summary_dates_from, from)
        until != null -> resources.getString(R.string.search_summary_dates_until, until)
        filters.hasDates -> resources.getString(R.string.search_summary_dates_invalid)
        else -> null
    }
}
