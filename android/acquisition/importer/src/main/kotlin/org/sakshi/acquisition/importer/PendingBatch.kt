package org.sakshi.acquisition.importer

import android.net.Uri

/** How the user brought the material into Sakshi. */
public enum class ImportMechanism { SHARE_SEND, SHARE_SEND_MULTIPLE, DOCUMENT_PICKER, PHOTO_PICKER, PASTE, MANUAL_NOTE }

/** Coarse class of an item, taken from the claimed MIME type. Only used to choose limits and next steps. */
public enum class ItemKind { TEXT, IMAGE, AUDIO, VIDEO, PDF, ARCHIVE, TEXT_FILE, OTHER }

/** Why an item cannot be saved. */
public enum class Rejection { UNSUPPORTED_SCHEME, UNREADABLE, TOO_MANY_ITEMS, EMPTY_TEXT, TEXT_TOO_LONG, NOT_A_URI }

/** One thing offered for import. Nothing is stored until the caller commits. */
public sealed interface PendingItem {
    public val index: Int

    /**
     * Content behind a `content://` URI. Every claim is supplied by the sending app and is not verified.
     * The URI grant is only valid while the receiving screen is alive, so commit promptly.
     */
    public data class Stream(
        override val index: Int,
        val uri: Uri,
        val declaredMime: String?,
        val displayNameClaim: String?,
        val sizeClaim: Long?,
        val kind: ItemKind,
        val uriAuthorityClaim: String?,
    ) : PendingItem

    /** Text received directly, kept exactly as received. */
    public data class Text(
        override val index: Int,
        val text: String,
        val kind: ItemKind = ItemKind.TEXT,
    ) : PendingItem

    public data class Rejected(override val index: Int, val reason: Rejection) : PendingItem
}

/** Everything offered in one share or pick. [referrerClaim] is whatever the sender reported about itself. */
public data class PendingBatch(
    val mechanism: ImportMechanism,
    val items: List<PendingItem>,
    val referrerClaim: String?,
)
