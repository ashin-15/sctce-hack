package org.sakshi.app.review

import java.time.Instant
import java.time.format.DateTimeParseException
import org.sakshi.app.R
import org.sakshi.app.ui.UiText
import org.sakshi.app.ui.res
import org.sakshi.core.database.ReviewAction
import org.sakshi.core.model.CategoryAssessment
import org.sakshi.core.model.CategoryBasis
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.Direction
import org.sakshi.core.model.UnwantedContact
import org.sakshi.core.vault.DecisionChange
import org.sakshi.core.vault.DecisionTargetKind
import org.sakshi.core.vault.StoredDecision

/** One line of "History": what the person did, when, and the reason they gave when they disagreed. */
data class HistoryLine(val at: Instant?, val text: UiText, val reason: UiText?)

object HistoryRows {
    /** [category] is the category the decision was about, when it was about one. */
    fun of(decision: StoredDecision, category: CategoryAssessment? = null): HistoryLine {
        val at = try {
            Instant.parse(decision.decidedAt)
        } catch (_: DateTimeParseException) {
            null
        }
        val text = when (val target = decision.target) {
            is DecisionTargetKind.Category -> findingText(decision.action, category, target.label)
            DecisionTargetKind.Association -> res(
                if (decision.action == ReviewAction.REJECT) R.string.history_association_withdrawn else R.string.history_association_confirmed,
            )
            DecisionTargetKind.Direction -> {
                val dir = (decision.change as? DecisionChange.Direction)?.to ?: Direction.UNKNOWN
                res(R.string.history_direction, directionText(dir))
            }
            DecisionTargetKind.Wantedness -> res(
                when ((decision.change as? DecisionChange.Wantedness)?.to) {
                    UnwantedContact.USER_MARKED_UNWANTED -> R.string.history_unwanted
                    UnwantedContact.USER_MARKED_WANTED -> R.string.history_wanted
                    else -> R.string.history_wantedness_cleared
                },
            )
            DecisionTargetKind.Boundary -> res(R.string.history_boundary)
            DecisionTargetKind.Duplicate -> res(R.string.history_duplicate)
            else -> res(R.string.history_other)
        }
        val reason = decision.reasonCode?.takeIf { decision.action == ReviewAction.REJECT }?.let { reasonText(it) }
        return HistoryLine(at, text, reason)
    }

    private fun findingText(action: String, category: CategoryAssessment?, targetLabel: CategoryLabel?): UiText {
        val label = (category?.label ?: targetLabel)?.let { categoryLabelText(it) } ?: res(R.string.category_unknown)
        return when {
            category?.basis == CategoryBasis.USER_TAG -> res(R.string.history_own_tag, label)
            action == ReviewAction.ACCEPT -> res(R.string.history_agreed, label)
            action == ReviewAction.REJECT -> res(R.string.history_disagreed, label)
            else -> res(R.string.history_not_sure, label)
        }
    }
}
