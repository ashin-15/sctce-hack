package org.sakshi.export.report

import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Event
import org.sakshi.core.vault.EvidenceDetails
import org.sakshi.core.vault.Vault

/** Looks up stored facts about the evidence a case's events cite, once per artefact id. */
internal class DetailCache(private val vault: Vault, private val caseId: CaseId) {
    private val cache = HashMap<String, EvidenceDetails?>()

    private suspend fun details(artifactId: String): EvidenceDetails? {
        if (artifactId in cache) return cache[artifactId]
        val found = vault.evidence.details(artifactId)?.takeIf { it.caseId == caseId.value }
        cache[artifactId] = found
        return found
    }

    suspend fun sha256(artifactId: String): String? = details(artifactId)?.sha256

    /** Distinct, sorted words for how the cited evidence reached the app. */
    suspend fun acquisitionKinds(events: List<Event>): List<String> =
        events.flatMap { it.evidenceReferences }
            .mapNotNull { details(it.artifactId.value)?.acquisitionKind }
            .map { ReportText.ACQUISITION[it] ?: ReportText.ACQUISITION_OTHER }
            .distinct()
            .sorted()
}
