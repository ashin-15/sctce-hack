package org.sakshi.export.report

import org.sakshi.core.model.Event
import org.sakshi.core.model.EvidenceReference
import org.sakshi.core.model.Locator
import org.sakshi.core.model.slice
import org.sakshi.core.vault.DerivativeKind
import org.sakshi.core.vault.Vault

/** Resolves the verbatim text an evidence reference points at. */
public fun interface QuoteSource {
    /** The exact text of [reference] within [event], or null when it cannot be resolved. */
    public suspend fun quote(event: Event, reference: EvidenceReference): String?
}

/**
 * Reads quotes from the vault's text derivatives. The reference's artifact id may be a derivative id or an
 * evidence id; for evidence the newest user edit, else the newest parsed text, is used.
 */
public class VaultQuoteSource(private val vault: Vault) : QuoteSource {
    override suspend fun quote(event: Event, reference: EvidenceReference): String? {
        val artifact = reference.artifactId.value
        val text = vault.derivatives.get(artifact)?.text
            ?: vault.derivatives.latest(artifact, DerivativeKind.USER_EDIT)?.text
            ?: vault.derivatives.latest(artifact, DerivativeKind.PARSED_TEXT)?.text
            ?: return null
        return when (val locator = reference.locator) {
            Locator.WholeArtifact -> text
            is Locator.Text -> try {
                locator.toCodePointSpan().slice(text)
            } catch (_: IllegalArgumentException) {
                null
            }
            else -> null
        }
    }
}
