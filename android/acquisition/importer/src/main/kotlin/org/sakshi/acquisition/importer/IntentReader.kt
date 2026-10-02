package org.sakshi.acquisition.importer

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

/**
 * Parses a share intent into a [PendingBatch]. The intent may have been built by any app, so every extra is
 * read defensively: a wrong type or a malformed parcel becomes a [Rejection], never an exception.
 */
public object IntentReader {
    /** Returns null for an action other than `ACTION_SEND` or `ACTION_SEND_MULTIPLE`. */
    public fun read(
        intent: Intent,
        resolver: ContentResolver,
        referrer: String?,
        limits: ImportLimits = ImportLimits(),
    ): PendingBatch? {
        val mechanism = when (intent.action) {
            Intent.ACTION_SEND -> ImportMechanism.SHARE_SEND
            Intent.ACTION_SEND_MULTIPLE -> ImportMechanism.SHARE_SEND_MULTIPLE
            else -> return null
        }
        val drafts = ArrayList<Draft>()
        val seenUris = HashSet<Uri>()
        for (draft in streamExtras(intent) + clipUris(intent)) {
            if (draft !is Draft.FromUri || seenUris.add(draft.uri)) drafts += draft
        }
        val sharedText = textExtra(intent)
        sharedText?.let { drafts += it }
        for (text in clipTexts(intent)) {
            if (text.text != (sharedText as? Draft.FromText)?.text) drafts += text
        }
        return assembleBatch(mechanism, drafts, resolver, intent.type, referrer, limits)
    }

    private fun streamExtras(intent: Intent): List<Draft> {
        val single = safely { ParcelableExtras.uri(intent, Intent.EXTRA_STREAM) }
        if (single != null) return listOf(Draft.FromUri(single))
        val list = safely { ParcelableExtras.uriList(intent, Intent.EXTRA_STREAM) }
        if (list != null) {
            return list.map { it.asUriDraft() }
        }
        val unreadable = safely { ParcelableExtras.isPresent(intent, Intent.EXTRA_STREAM) } == true
        return if (unreadable) {
            listOf(Draft.Invalid(Rejection.NOT_A_URI))
        } else {
            emptyList()
        }
    }

    private fun Any?.asUriDraft(): Draft =
        if (this is Uri) Draft.FromUri(this) else Draft.Invalid(Rejection.NOT_A_URI)

    private fun clipUris(intent: Intent): List<Draft> {
        val clip = safely { intent.clipData } ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { index ->
            safely { clip.getItemAt(index).uri }?.let { Draft.FromUri(it) }
        }
    }

    private fun clipTexts(intent: Intent): List<Draft.FromText> {
        val clip = safely { intent.clipData } ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { index ->
            safely { clip.getItemAt(index).text?.toString() }?.let { Draft.FromText(it) }
        }
    }

    private fun textExtra(intent: Intent): Draft? {
        if (safely { intent.hasExtra(Intent.EXTRA_TEXT) } != true) return null
        val text = safely { intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() }
        return if (text == null) Draft.Invalid(Rejection.EMPTY_TEXT) else Draft.FromText(text)
    }

    /** Extras are parcelled by another process, so even reading them can throw. */
    private inline fun <T> safely(block: () -> T): T? = try {
        block()
    } catch (_: RuntimeException) {
        null
    }
}
