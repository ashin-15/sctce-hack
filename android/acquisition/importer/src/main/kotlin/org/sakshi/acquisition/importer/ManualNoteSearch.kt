package org.sakshi.acquisition.importer

import java.io.IOException
import java.security.GeneralSecurityException
import kotlinx.coroutines.flow.first
import org.sakshi.core.model.CaseId
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.NoteText
import org.sakshi.core.vault.NoteTextSource
import org.sakshi.core.vault.Vault

/**
 * Reads the text of the manual notes of a case for search. Only the note's `text` field is returned, which is the
 * person's own statement and recalled wording; the metadata fields (claimed sender, source app, time wording) are
 * not searchable. Each note is decrypted once per [texts] call and its bytes are not kept. A note whose blob fails
 * authentication, is missing, cannot be unwrapped or does not decode is left out; the evidence integrity check is
 * what reports such a note.
 *
 * Note text is user-reported. It is searched like any other text, but it never becomes an event or a finding here.
 */
public class VaultNoteTextSource(private val vault: Vault) : NoteTextSource {
    override suspend fun texts(caseId: CaseId): List<NoteText> =
        vault.evidence.observeForCase(caseId.value).first()
            .filter { it.acquisitionKind == AcquisitionKind.MANUAL_NOTE }
            .mapNotNull { item -> readText(item.id) }

    private suspend fun readText(evidenceId: String): NoteText? = try {
        val bytes = vault.evidence.openOriginal(evidenceId).use { it.inputStream().readBytes() }
        NoteText(evidenceId, ManualNoteCodec.decode(bytes).note.text)
    } catch (_: IOException) {
        null
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

/** Makes the text of manual notes searchable in this vault. Call once after opening it; see [VaultNoteTextSource]. */
public fun Vault.enableNoteSearch() {
    useNoteTextSource(VaultNoteTextSource(this))
}
