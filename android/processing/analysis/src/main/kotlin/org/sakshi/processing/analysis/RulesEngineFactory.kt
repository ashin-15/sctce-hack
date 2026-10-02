package org.sakshi.processing.analysis

import org.sakshi.processing.text.BenchCueList
import org.sakshi.processing.text.RulesEngine

/** Builds the rules engine the pipeline uses. */
public object RulesEngineFactory {
    /**
     * Demo configuration: the benchmark cue list with [requireReviewedCues] false.
     *
     * The cue lists are not native-speaker reviewed and match phrases without context, so every result is a
     * suggestion for human review only. An unreviewed list should not be enabled for real-world use; an app that
     * ships beyond a demo passes `requireReviewedCues = true`, which withholds matches of unreviewed languages.
     */
    public fun default(requireReviewedCues: Boolean = false): RulesEngine =
        RulesEngine(BenchCueList.V1, requireReviewedCues)
}
