package org.sakshi.acquisition.importer

/** Why one item could not be saved. Deliberately carries no text from the source. */
public enum class ImportFailure {
    CASE_UNAVAILABLE,
    TOO_LARGE,
    UNREADABLE,
    ACCESS_DENIED,
    STORAGE_ERROR,
    KEY_UNAVAILABLE,
    CANCELLED,
}

/** What can be done with a saved item in the current phase. */
public enum class AnalysisState { READY_FOR_TEXT_ANALYSIS, PRESERVED_NOT_ANALYSED }

/** Result for one item of a commit, identified by its [PendingItem.index]. */
public sealed interface ItemOutcome {
    public val index: Int

    /** [duplicateOf] lists earlier evidence in the same case with identical bytes. The item is saved regardless. */
    public data class Saved(
        override val index: Int,
        val evidenceId: String,
        val sha256: String,
        val byteSize: Long,
        val declaredMime: String?,
        val detectedMime: String?,
        val analysisState: AnalysisState,
        val duplicateOf: List<String>,
    ) : ItemOutcome

    public data class Failed(override val index: Int, val reason: ImportFailure) : ItemOutcome

    public data class Skipped(override val index: Int, val reason: Rejection) : ItemOutcome
}

public data class ImportReport(val outcomes: List<ItemOutcome>)
