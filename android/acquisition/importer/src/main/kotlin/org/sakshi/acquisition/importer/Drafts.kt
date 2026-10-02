package org.sakshi.acquisition.importer

import android.content.ContentResolver
import android.net.Uri

/** An item before indexes are assigned and the item cap is applied. */
internal sealed interface Draft {
    data class FromUri(val uri: Uri) : Draft

    data class FromText(val text: String) : Draft

    data class Invalid(val reason: Rejection) : Draft
}

/** Turns drafts into a batch: assigns indexes in order, keeps the first [ImportLimits.maxItems], reads claims. */
internal fun assembleBatch(
    mechanism: ImportMechanism,
    drafts: List<Draft>,
    resolver: ContentResolver,
    fallbackMime: String?,
    referrer: String?,
    limits: ImportLimits,
): PendingBatch {
    val items = drafts.take(limits.maxItems).mapIndexed { index, draft ->
        when (draft) {
            is Draft.FromUri -> ProviderClaims.streamItem(index, draft.uri, fallbackMime, resolver)
            is Draft.FromText -> textItem(index, draft.text, limits)
            is Draft.Invalid -> PendingItem.Rejected(index, draft.reason)
        }
    }
    val capped = if (drafts.size > limits.maxItems) {
        items + PendingItem.Rejected(items.size, Rejection.TOO_MANY_ITEMS)
    } else {
        items
    }
    return PendingBatch(mechanism, capped, referrer?.let { ProviderClaims.cap(it) })
}

internal fun textItem(index: Int, text: String, limits: ImportLimits): PendingItem = when {
    text.isBlank() -> PendingItem.Rejected(index, Rejection.EMPTY_TEXT)
    text.length > limits.maxTextChars -> PendingItem.Rejected(index, Rejection.TEXT_TOO_LONG)
    else -> PendingItem.Text(index, text)
}
