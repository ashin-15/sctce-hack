package org.sakshi.processing.analysis

/** What callers need from [TextAnalysis]; a test can supply its own. */
public interface TextAnalyser {
    /**
     * Analyses the evidence. For an export, [exportOptions] is required before events can be written; without it
     * only the derivative is stored and [AnalysisOutcome.NeedsExportOptions] asks the open questions.
     */
    public suspend fun analyse(evidenceId: String, exportOptions: ExportOptions? = null): AnalysisOutcome
}
