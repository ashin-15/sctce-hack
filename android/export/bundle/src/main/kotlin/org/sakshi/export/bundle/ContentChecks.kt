package org.sakshi.export.bundle

import org.sakshi.core.model.ConfirmationStatus
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventSchemaAdapter

internal class EventsOutcome(val check: Check, val events: List<Event>?)

/** Checks of the event stream and the cross-case rule. */
internal object ContentChecks {
    fun events(view: BundleView): EventsOutcome {
        val data = try {
            view.loadVerified(BundleFormat.EVENTS)
        } catch (e: BundleReadException) {
            return EventsOutcome(Check("events", CheckStatus.FAILED, "events.jsonl not read: ${e.message}"), null)
        }
        val text = String(data, Charsets.UTF_8)
        val lines = if (text.isEmpty()) emptyList() else text.removeSuffix("\n").split('\n')
        val problems = mutableListOf<String>()
        val events = mutableListOf<Event>()
        val seen = HashSet<Pair<String, Int>>()
        lines.forEachIndexed { index, line ->
            val event = parseEvent(line)
            if (event == null) {
                problems += "line ${index + 1} is not a schema-valid event"
                return@forEachIndexed
            }
            events += event
            val id = safe(event.eventId.value)
            if (!seen.add(event.eventId.value to event.revision)) problems += "duplicate event $id revision ${event.revision}"
            if (event.caseId.value != view.manifest.caseId) problems += "event $id belongs to another case"
            if (event.userConfirmation.status != ConfirmationStatus.CONFIRMED) problems += "event $id is not user-confirmed"
        }
        val check = if (problems.isEmpty()) {
            Check("events", CheckStatus.PASSED, "${events.size} events, all schema-valid, confirmed and unique per revision")
        } else {
            Check("events", CheckStatus.FAILED, summarise(problems))
        }
        return EventsOutcome(check, if (problems.any { it.startsWith("line ") }) null else events)
    }

    private fun parseEvent(line: String): Event? = try {
        JsonInput.parse(line)
        EventSchemaAdapter.fromJson(line)
    } catch (e: IllegalArgumentException) {
        null
    }

    fun noCrossCase(view: BundleView, events: List<Event>?): Check {
        if (events == null) return Check("no_cross_case", CheckStatus.FAILED, "not evaluated: events could not be read")
        val foreign = events.filter { it.caseId.value != view.manifest.caseId }.map { safe(it.eventId.value) }
        return if (foreign.isEmpty()) {
            Check("no_cross_case", CheckStatus.PASSED, "all ${events.size} events belong to the case named in the manifest")
        } else {
            Check("no_cross_case", CheckStatus.FAILED, "events of another case: ${summarise(foreign)}")
        }
    }
}
