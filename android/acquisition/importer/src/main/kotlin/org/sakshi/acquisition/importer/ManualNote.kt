package org.sakshi.acquisition.importer

/** Whether the user reports that the content was a View Once item. This records their statement only. */
public enum class ViewOnceStatus { NOT_APPLICABLE, UNKNOWN, USER_REPORTED }

/** What the user holds of the content the note describes. */
public enum class ContentAvailability { NOT_APPLICABLE, NOT_ACQUIRED, CONTEXT_ONLY, INDEPENDENT_RECORDING_OR_COPY }

/**
 * A note written by the user. [incidentTimeText] is the user's own words and is never parsed into a time.
 * Use [normalised] before storing; [problem] says why a note is not acceptable.
 */
public data class ManualNote(
    val text: String,
    val incidentTimeText: String?,
    val claimedSender: String?,
    val sourceAppClaim: String?,
    val viewOnceStatus: ViewOnceStatus,
    val contentAvailability: ContentAvailability,
) {
    /** Null when the note is acceptable after trimming. */
    public fun problem(): Rejection? {
        val trimmed = text.trim()
        return when {
            trimmed.isEmpty() -> Rejection.EMPTY_TEXT
            trimmed.length > MAX_TEXT_CHARS -> Rejection.TEXT_TOO_LONG
            listOf(incidentTimeText, claimedSender, sourceAppClaim).any { (it?.trim()?.length ?: 0) > MAX_FIELD_CHARS } ->
                Rejection.TEXT_TOO_LONG
            else -> null
        }
    }

    /** Trims the text and the optional fields, turning blank optional fields into null. */
    public fun normalised(): ManualNote {
        require(problem() == null) { "Note is not valid" }
        return copy(
            text = text.trim(),
            incidentTimeText = incidentTimeText.blankToNull(),
            claimedSender = claimedSender.blankToNull(),
            sourceAppClaim = sourceAppClaim.blankToNull(),
        )
    }

    public companion object {
        public const val MAX_TEXT_CHARS: Int = 20_000
        public const val MAX_FIELD_CHARS: Int = 200
    }
}

private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/** A note as read back from the vault. */
public data class StoredManualNote(val note: ManualNote, val writtenAt: java.time.Instant)
