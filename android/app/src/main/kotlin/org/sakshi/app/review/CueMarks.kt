package org.sakshi.app.review

import org.sakshi.core.model.CodePointSpan
import org.sakshi.core.model.Event
import org.sakshi.core.model.Locator
import org.sakshi.core.model.toUtf16Range

/** One matched span inside the message text. [start] and [end] are UTF-16 indices of the displayed body. */
data class CueMark(val referenceId: String, val start: Int, val end: Int, val quote: String)

/**
 * Finds the cue spans of an event inside its body text. The event stores them as code point ranges of the whole
 * derivative; here they are moved to the start of the body and converted to the UTF-16 indices that text drawing uses,
 * so an emoji or other characters before the cue do not shift the mark.
 */
object CueMarks {
    fun of(event: Event, body: String): List<CueMark> {
        val bodyReference = event.evidenceReferences.firstOrNull() ?: return emptyList()
        val bodySpan = (bodyReference.locator as? Locator.Text)?.toCodePointSpan() ?: return emptyList()
        return event.evidenceReferences.drop(1).mapNotNull { reference ->
            val span = (reference.locator as? Locator.Text)?.toCodePointSpan() ?: return@mapNotNull null
            if (span.start < bodySpan.start || span.end > bodySpan.end || span.start == span.end) return@mapNotNull null
            val relative = CodePointSpan(span.start - bodySpan.start, span.end - bodySpan.start)
            val range = try {
                relative.toUtf16Range(body)
            } catch (_: IllegalArgumentException) {
                return@mapNotNull null
            }
            CueMark(reference.referenceId.value, range.first, range.last + 1, body.substring(range.first, range.last + 1))
        }
    }

    /** The marks that belong to one category, in the order the category lists them. */
    fun forCategory(marks: List<CueMark>, referenceIds: List<String>): List<CueMark> =
        referenceIds.mapNotNull { id -> marks.firstOrNull { it.referenceId == id } }
}
