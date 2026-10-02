package org.sakshi.app.report

import kotlin.test.Test
import kotlin.test.assertEquals

class KeyIdFormatTest {
    @Test
    fun aSha256KeyIdIsShownInSixteenGroupsOfFour() {
        val id = "0123456789abcdef".repeat(4)
        val grouped = KeyIdFormat.grouped(id)
        val groups = grouped.split(" ")
        assertEquals(16, groups.size)
        assertEquals(true, groups.all { it.length == 4 })
        assertEquals(id, groups.joinToString(""))
    }

    @Test
    fun aLastShortGroupKeepsItsCharacters() {
        assertEquals("abcd efgh ij", KeyIdFormat.grouped("abcdefghij"))
    }

    @Test
    fun anEmptyIdStaysEmpty() {
        assertEquals("", KeyIdFormat.grouped(""))
    }
}
