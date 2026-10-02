package org.sakshi.app.importing

import kotlin.test.Test
import kotlin.test.assertEquals

class TextPreviewTest {
    @Test
    fun shortTextIsUnchanged() {
        assertEquals("synthetic", previewOf("synthetic"))
    }

    @Test
    fun longTextIsCutAtFiveHundredCharacters() {
        assertEquals(PREVIEW_CHARS, previewOf("a".repeat(PREVIEW_CHARS + 50)).length)
    }

    @Test
    fun aSurrogatePairIsNeverSplit() {
        val text = "a".repeat(PREVIEW_CHARS - 1) + "😀" + "tail"
        val preview = previewOf(text)
        assertEquals(PREVIEW_CHARS - 1, preview.length)
    }
}
