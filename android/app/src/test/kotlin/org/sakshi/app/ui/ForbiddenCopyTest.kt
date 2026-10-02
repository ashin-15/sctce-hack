package org.sakshi.app.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.sakshi.app.support.ForbiddenWords
import org.w3c.dom.Element

class ForbiddenCopyTest {
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
    fun theListHoldsEveryWordFromTheBrief() {
        listOf("harasser", "abuser", "stalker", "stalking", "guilty", "danger", "risk score", "threat detected", "harassment detected", "all clear", "nothing found", "harmless")
            .forEach { assertTrue(it in ForbiddenWords.phrases, it) }
    }

    @Test
    fun readsTheStringResources() {
        assertTrue(strings().size > 20)
    }

    @Test
    fun noForbiddenWordsOrDashes() {
        val violations = strings().flatMap { (name, text) ->
            ForbiddenWords.found(text).map { "$name: $it" } + listOfNotNull("$name: dash".takeIf { ForbiddenWords.hasDash(text) })
        }
        assertEquals(emptyList(), violations)
    }
}
