package org.sakshi.app.review

import org.sakshi.app.R
import org.sakshi.app.ui.UiText
import org.sakshi.app.ui.res
import org.sakshi.core.model.CategoryBasis
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.Direction
import org.sakshi.core.vault.ReviewReason

/** Short neutral phrases for what a suggestion or tag is about. They describe wording, not a person or an offence. */
fun categoryLabelText(label: CategoryLabel): UiText = res(
    when (label) {
        CategoryLabel.VERBAL_ABUSE -> R.string.category_verbal_abuse
        CategoryLabel.EXPLICIT_THREAT -> R.string.category_explicit_threat
        CategoryLabel.IMPLIED_THREAT -> R.string.category_implied_threat
        CategoryLabel.INTIMIDATION -> R.string.category_intimidation
        CategoryLabel.CONTROLLING_REQUEST -> R.string.category_controlling_request
        CategoryLabel.SEXUAL_PRESSURE -> R.string.category_sexual_pressure
        CategoryLabel.PRIVACY_EXPOSURE_INDICATOR -> R.string.category_privacy_exposure
        CategoryLabel.CONTACT_REQUEST -> R.string.category_contact_request
        CategoryLabel.ORDINARY -> R.string.category_ordinary
        CategoryLabel.UNKNOWN -> R.string.category_unknown
    },
)

/** Where a category came from, in the form of the sentence that goes under it. */
fun categoryBasisText(basis: CategoryBasis, cues: List<String>): UiText = when (basis) {
    CategoryBasis.RULE_SUGGESTION ->
        if (cues.isEmpty()) {
            res(R.string.category_basis_rule_no_words)
        } else {
            res(R.string.category_basis_rule, cues.joinToString(", ") { "'$it'" })
        }
    CategoryBasis.CLASSIFIER_SUGGESTION -> res(R.string.category_basis_classifier)
    CategoryBasis.LLM_SUGGESTION -> res(R.string.category_basis_llm)
    CategoryBasis.USER_TAG -> res(R.string.category_basis_user)
}

fun reviewStatusText(status: CategoryReviewStatus): UiText = res(
    when (status) {
        CategoryReviewStatus.UNREVIEWED -> R.string.review_status_unreviewed
        CategoryReviewStatus.ACCEPTED -> R.string.review_status_accepted
        CategoryReviewStatus.REJECTED -> R.string.review_status_rejected
        CategoryReviewStatus.UNCERTAIN -> R.string.review_status_uncertain
    },
)

/** The reasons offered when the person disagrees, in the order of megaplan 18.2. */
val DISAGREE_REASONS: List<String> = listOf(
    ReviewReason.WRONG_SENDER_OR_QUOTE,
    ReviewReason.EXTRACTION_ERROR,
    ReviewReason.DUPLICATE,
    ReviewReason.INSUFFICIENT_CONTEXT,
    ReviewReason.SIGNAL_ABSENT,
)

fun reasonText(code: String): UiText = res(
    when (code) {
        ReviewReason.WRONG_SENDER_OR_QUOTE -> R.string.reason_wrong_sender
        ReviewReason.EXTRACTION_ERROR -> R.string.reason_extraction
        ReviewReason.DUPLICATE -> R.string.reason_duplicate
        ReviewReason.INSUFFICIENT_CONTEXT -> R.string.reason_context
        ReviewReason.SIGNAL_ABSENT -> R.string.reason_absent
        else -> R.string.reason_other
    },
)

fun directionText(direction: Direction): UiText = res(
    when (direction) {
        Direction.INCOMING -> R.string.direction_incoming
        Direction.OUTGOING -> R.string.direction_outgoing
        Direction.SYSTEM -> R.string.direction_system
        Direction.UNKNOWN -> R.string.direction_unknown
    },
)

/** Labels the person can pick for a tag of their own. */
val OWN_TAG_LABELS: List<CategoryLabel> = CategoryLabel.entries.toList()
