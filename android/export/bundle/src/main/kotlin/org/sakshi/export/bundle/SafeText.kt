package org.sakshi.export.bundle

private const val MAX_SHOWN: Int = 160
private const val MAX_LISTED: Int = 5

/** Makes untrusted text printable: anything outside a conservative set becomes '?', and long text is cut. */
internal fun safe(text: String): String {
    val clean = text.take(MAX_SHOWN).map { if (it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "._-/:") it else '?' }
    return clean.joinToString("") + if (text.length > MAX_SHOWN) "..." else ""
}

/** Joins the first few problems and counts the rest. */
internal fun summarise(problems: List<String>): String {
    val shown = problems.take(MAX_LISTED).joinToString("; ")
    return if (problems.size > MAX_LISTED) "$shown; and ${problems.size - MAX_LISTED} more" else shown
}
