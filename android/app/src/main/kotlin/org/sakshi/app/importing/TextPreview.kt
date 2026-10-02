package org.sakshi.app.importing

const val PREVIEW_CHARS = 500

/** The leading part of shared text for the preview box, never cutting a surrogate pair in half. */
fun previewOf(text: String, limit: Int = PREVIEW_CHARS): String {
    if (text.length <= limit) return text
    val end = if (text[limit - 1].isHighSurrogate()) limit - 1 else limit
    return text.substring(0, end)
}
