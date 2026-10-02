package org.sakshi.app.review

import org.sakshi.app.R
import org.sakshi.app.ui.UiText
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.res
import org.sakshi.core.vault.ReviewProblem
import org.sakshi.core.vault.ReviewResult

/** What the screen says after an action. Plain words only; no codes and no text from the evidence. */
data class ReviewNotice(val kind: NoteKind, val text: UiText)

object ReviewMessages {
    /** [applied] is the sentence for a change that was saved. */
    fun of(result: ReviewResult, applied: UiText): ReviewNotice = when (result) {
        is ReviewResult.Applied -> ReviewNotice(NoteKind.Info, applied)
        ReviewResult.NoChange -> ReviewNotice(NoteKind.Info, res(R.string.review_no_change))
        ReviewResult.NotFound -> ReviewNotice(NoteKind.Problem, res(R.string.review_not_found))
        is ReviewResult.Invalid -> ReviewNotice(NoteKind.Problem, problemText(result.problem))
    }

    fun failed(): ReviewNotice = ReviewNotice(NoteKind.Problem, res(R.string.review_failed))

    fun problemText(problem: ReviewProblem?): UiText = res(
        when (problem) {
            ReviewProblem.EMPTY_SELECTOR -> R.string.review_problem_no_name
            ReviewProblem.ACTOR_UNKNOWN -> R.string.review_problem_person
            ReviewProblem.VALUE_NOT_ALLOWED -> R.string.review_problem_value
            ReviewProblem.MIXED_CASE -> R.string.review_problem_case
            ReviewProblem.CATEGORY_INDEX,
            ReviewProblem.REFERENCES_REQUIRED,
            ReviewProblem.REFERENCE_UNKNOWN,
            ReviewProblem.REFERENCE_DUPLICATE,
            ReviewProblem.CANONICAL_REQUIRED,
            ReviewProblem.WRONG_EVIDENCE_KIND,
            ReviewProblem.NOT_STORABLE,
            null,
            -> R.string.review_problem_generic
        },
    )
}
