package org.sakshi.processing.analysis

import org.sakshi.core.vault.DerivativeKind
import org.sakshi.core.vault.DerivativeStore

/** [TextDerivatives] over the vault's [DerivativeStore]; saved text is the decoder output, stored unchanged. */
public class VaultTextDerivatives(private val store: DerivativeStore) : TextDerivatives {
    override suspend fun latestParsedText(evidenceId: String): DerivativeText? =
        store.latest(evidenceId, DerivativeKind.PARSED_TEXT)?.let { DerivativeText(it.id, it.text) }

    override suspend fun saveParsedText(evidenceId: String, text: String): DerivativeText {
        val saved = store.save(evidenceId, DerivativeKind.PARSED_TEXT, text, DecoderTool.ID, DecoderTool.VERSION)
        return DerivativeText(saved.id, saved.text)
    }

    override suspend fun text(derivativeId: String): String? = store.get(derivativeId)?.text
}
