package org.sakshi.app.ui

import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DateFormatTest {
    @Test
    fun usesTheGivenZoneAndLocale() {
        val iso = "2026-10-02T23:30:00Z"
        assertEquals("Oct 2, 2026", formatCreatedDate(iso, Locale.US, ZoneId.of("UTC")))
        assertEquals("Oct 3, 2026", formatCreatedDate(iso, Locale.US, ZoneId.of("Asia/Kolkata")))
    }

    @Test
    fun unparseableInputGivesNull() {
        assertNull(formatCreatedDate("not a date", Locale.US, ZoneId.of("UTC")))
    }
}
