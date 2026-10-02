package org.sakshi.acquisition.importer

import android.content.ContentResolver
import android.net.Uri

/** Builds a batch from the result of the Storage Access Framework or the Photo Picker. */
public object PickerReader {
    /** @throws IllegalArgumentException if [mechanism] is not a picker. */
    public fun fromPickedUris(
        uris: List<Uri>,
        mechanism: ImportMechanism,
        resolver: ContentResolver,
        limits: ImportLimits = ImportLimits(),
    ): PendingBatch {
        require(mechanism == ImportMechanism.DOCUMENT_PICKER || mechanism == ImportMechanism.PHOTO_PICKER) {
            "Not a picker mechanism"
        }
        val drafts = uris.distinct().map { Draft.FromUri(it) }
        return assembleBatch(mechanism, drafts, resolver, fallbackMime = null, referrer = null, limits = limits)
    }
}
