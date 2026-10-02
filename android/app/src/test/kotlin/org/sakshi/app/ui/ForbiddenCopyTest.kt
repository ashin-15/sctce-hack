package org.sakshi.app.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element

class ForbiddenCopyTest {
    private val forbidden = listOf(
        "safe", "protected from", "guaranteed", "court", "admissible", "proves", "proof",
        "secure forever", "nobody can", "first",
    ).map { Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE) }

    private fun strings(): List<Pair<String, String>> {
        val file = File(requireNotNull(System.getProperty("sakshi.stringsXml")) { "sakshi.stringsXml is not set" })
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        return buildList {
            for (tag in listOf("string", "item")) {
                val nodes = root.getElementsByTagName(tag)
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i) as Element
                    add((node.getAttribute("name").ifEmpty { node.getAttribute("quantity") }) to node.textContent)
                }
            }
        }
    }

    @Test
    fun readsTheStringResources() {
        assertTrue(strings().size > 20)
    }

    @Test
    fun noForbiddenWordsOrDashes() {
        val violations = strings().flatMap { (name, text) ->
            forbidden.filter { it.containsMatchIn(text) }.map { "$name: ${it.pattern}" } +
                listOfNotNull("$name: dash".takeIf { text.any { c -> c.code == EM_DASH || c.code == EN_DASH } })
        }
        assertEquals(emptyList(), violations)
    }

    private companion object {
        const val EM_DASH = 0x2014
        const val EN_DASH = 0x2013
    }
}
