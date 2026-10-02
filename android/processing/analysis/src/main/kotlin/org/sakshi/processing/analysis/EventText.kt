package org.sakshi.processing.analysis

import org.sakshi.core.model.CodePointSpan
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.EvidenceReference
import org.sakshi.core.model.Locator
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.slice
import org.sakshi.core.vault.Vault

/**
 * Resolves text locators of events against the stored derivative. Plaintext is read per call and not kept,
 * so callers should not hold on to returned strings longer than they need them.
 */
public class EventText internal constructor(private val derivatives: TextDerivatives) {
    public constructor(vault: Vault) : this(VaultTextDerivatives(vault.derivatives))

    /** The exact slice [reference] points to, or null if the derivative is missing or the span is out of range. */
    public suspend fun quote(event: Event, reference: EvidenceReference): String? {
        if (reference !in event.evidenceReferences) return null
        val locator = reference.locator as? Locator.Text ?: return null
        val text = derivatives.text(reference.artifactId.value) ?: return null
        return try {
            CodePointSpan(locator.start, locator.end).slice(text)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** The message text of the event: its first evidence reference. */
    public suspend fun bodyOf(event: Event): String? = event.evidenceReferences.firstOrNull()?.let { quote(event, it) }

    /**
     * [bodyOf] for many events with one read per derivative and one walk over each derivative's code points. Every
     * event has an entry; it is null exactly where [bodyOf] would give null. For several revisions of one event the
     * last in the list wins.
     */
    public suspend fun bodiesOf(events: List<Event>): Map<EventId, String?> {
        val first = events.map { it.eventId to it.evidenceReferences.firstOrNull() }
        val slices = slicesOf(first.mapNotNull { it.second })
        return first.associate { (id, reference) -> id to reference?.let { slices[it] } }
    }

    /** [quote] for every evidence reference of the event, reading each derivative once. */
    public suspend fun quotesOf(event: Event): Map<ReferenceId, String?> {
        val slices = slicesOf(event.evidenceReferences)
        return event.evidenceReferences.associate { it.referenceId to slices[it] }
    }

    private suspend fun slicesOf(references: List<EvidenceReference>): Map<EvidenceReference, String?> {
        val result = HashMap<EvidenceReference, String?>(references.size)
        val byDerivative = references.filter { it.locator is Locator.Text }.groupBy { it.artifactId.value }
        for ((derivativeId, group) in byDerivative) {
            val index = derivatives.text(derivativeId)?.let { CodePointIndex(it) }
            for (reference in group) {
                val locator = reference.locator as Locator.Text
                val inRange = locator.start >= 0 && locator.start <= locator.end
                result[reference] = index?.takeIf { inRange && locator.end <= it.length }?.slice(locator.start, locator.end)
            }
        }
        return result
    }
}
