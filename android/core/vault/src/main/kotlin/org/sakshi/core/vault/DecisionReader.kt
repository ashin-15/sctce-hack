package org.sakshi.core.vault

import org.sakshi.core.database.SakshiDatabase
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.EventId

/** Reads the decisions about one event and fills in what only the finding rows know. */
internal class DecisionReader(private val database: SakshiDatabase) {
    suspend fun forEvent(eventId: EventId): List<StoredDecision> {
        val dao = database.findingDao()
        val rows = dao.getDecisionsForEvent(eventId.value, eventId.value + "/")
        if (rows.isEmpty()) return emptyList()
        val findings = dao.getForEvents(listOf(eventId.value)).associateBy { it.id }
        return rows.map { row ->
            val stored = row.toStored()
            val category = stored.target as? DecisionTargetKind.Category ?: return@map stored
            val finding = findings[row.targetId]
            val revision = row.targetRevision
            val earlier = if (revision == null || revision < 2) null else findings[RowKeys.finding(eventId.value, revision - 1, category.categoryIndex)]
            val to = finding?.let { status(it.reviewStatusAtImport) } ?: (stored.change as? DecisionChange.CategoryStatus)?.to
            stored.copy(
                target = DecisionTargetKind.Category(category.categoryIndex, finding?.let { label(it.label) }),
                change = if (to == null) stored.change else DecisionChange.CategoryStatus(earlier?.let { status(it.reviewStatusAtImport) }, to),
            )
        }
    }

    private fun status(name: String): CategoryReviewStatus? =
        try {
            Codecs.categoryReviewStatus.parse(name)
        } catch (_: IllegalStateException) {
            null
        }

    private fun label(name: String): CategoryLabel? =
        try {
            Codecs.categoryLabel.parse(name)
        } catch (_: IllegalStateException) {
            null
        }
}
