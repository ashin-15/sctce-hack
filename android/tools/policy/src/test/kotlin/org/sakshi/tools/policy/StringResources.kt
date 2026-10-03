package org.sakshi.tools.policy

/** One user-facing string of a `strings.xml` file, decoded. [name] is `name` for strings and `name[quantity]` or `name[index]` for items. */
internal data class NamedText(val file: String, val name: String, val line: Int, val text: String)

internal object StringResources {
    private val BLOCK = Regex("""<(string|plurals|string-array)\b([^>]*?)(?:/>|>(.*?)</\1>)""", RegexOption.DOT_MATCHES_ALL)
    private val ITEM = Regex("""<item\b([^>]*?)>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
    private val NAME = Regex("""\bname="([^"]*)"""")
    private val QUANTITY = Regex("""\bquantity="([^"]*)"""")

    fun parse(file: String, xml: String): List<NamedText> = BLOCK.findAll(xml).flatMap { block ->
        val kind = block.groupValues[1]
        val name = NAME.find(block.groupValues[2])?.groupValues?.get(1) ?: return@flatMap emptySequence()
        val bodyStart = block.groups[3]?.range?.first ?: return@flatMap emptySequence()
        if (kind == "string") {
            sequenceOf(NamedText(file, name, lineAt(xml, block.range.first), decode(block.groupValues[3])))
        } else {
            ITEM.findAll(block.groupValues[3]).mapIndexed { index, item ->
                val key = QUANTITY.find(item.groupValues[1])?.groupValues?.get(1) ?: index.toString()
                NamedText(file, "$name[$key]", lineAt(xml, bodyStart + item.range.first), decode(item.groupValues[2]))
            }
        }
    }.toList()

    fun parseAll(): List<NamedText> = Repo.mainSources
        .filter { it.extension == "xml" && Regex("""/res/values[^/]*/strings\.xml$""").containsMatchIn("/" + it.path) }
        .flatMap { parse(it.path, it.text) }

    private fun decode(raw: String): String = raw
        .replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
}
