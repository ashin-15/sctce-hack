package org.sakshi.acquisition.importer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/** Reads what a content provider claims about a URI. Every value is untrusted and every failure becomes null. */
internal object ProviderClaims {
    private const val MAX_CLAIM_CHARS = 255
    private val ARCHIVE_TYPES = setOf(
        "application/zip",
        "application/x-zip-compressed",
        "application/x-7z-compressed",
        "application/x-rar-compressed",
        "application/vnd.rar",
        "application/gzip",
        "application/x-gzip",
        "application/x-tar",
    )

    fun streamItem(index: Int, uri: Uri, fallbackMime: String?, resolver: ContentResolver): PendingItem {
        if (uri.scheme != "content") return PendingItem.Rejected(index, Rejection.UNSUPPORTED_SCHEME)
        val mime = cleanMime(typeOf(uri, resolver)) ?: cleanMime(fallbackMime)
        val (name, size) = openableColumns(uri, resolver)
        return PendingItem.Stream(
            index = index,
            uri = uri,
            declaredMime = mime,
            displayNameClaim = name?.let(::cap),
            sizeClaim = size,
            kind = kindOf(mime),
            uriAuthorityClaim = uri.authority?.let(::cap),
        )
    }

    fun kindOf(mime: String?): ItemKind {
        val base = mime?.substringBefore(';')?.trim()?.lowercase() ?: return ItemKind.OTHER
        return when {
            base.startsWith("image/") -> ItemKind.IMAGE
            base.startsWith("audio/") -> ItemKind.AUDIO
            base.startsWith("video/") -> ItemKind.VIDEO
            base == "application/pdf" -> ItemKind.PDF
            base in ARCHIVE_TYPES -> ItemKind.ARCHIVE
            base.startsWith("text/") -> ItemKind.TEXT_FILE
            else -> ItemKind.OTHER
        }
    }

    /** Truncates to [MAX_CLAIM_CHARS] without leaving half of a surrogate pair. */
    fun cap(value: String): String {
        if (value.length <= MAX_CLAIM_CHARS) return value
        val end = if (value[MAX_CLAIM_CHARS - 1].isHighSurrogate()) MAX_CLAIM_CHARS - 1 else MAX_CLAIM_CHARS
        return value.substring(0, end)
    }

    private fun cleanMime(mime: String?): String? = mime?.takeIf { it.isNotBlank() && it != "*/*" }?.let(::cap)

    private fun typeOf(uri: Uri, resolver: ContentResolver): String? = try {
        resolver.getType(uri)
    } catch (_: RuntimeException) {
        null
    }

    private fun openableColumns(uri: Uri, resolver: ContentResolver): Pair<String?, Long?> = try {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null to null
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameColumn >= 0 && !cursor.isNull(nameColumn)) cursor.getString(nameColumn) else null
            val size = if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) cursor.getLong(sizeColumn) else null
            name to size?.takeIf { it >= 0 }
        } ?: (null to null)
    } catch (_: RuntimeException) {
        null to null
    }
}
