package org.sakshi.processing.text

// All fixture content used by this test is synthetic. No real conversation data is involved.

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.sakshi.core.model.TimePrecision

class ExportTimeTest {
    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val newYork = ZoneId.of("America/New_York")

    private fun record(date: String, time: String): ParsedRecord =
        WhatsAppExportParser.parse("$date, $time - A: x").records.single()

    private fun resolve(date: String, time: String, order: DateOrder, zone: ZoneId): TimeBoundsCandidate? =
        ExportTime.resolve(record(date, time), order, zone)

    @Test
    fun dayMonthAndMonthDayGiveDifferentInstants() {
        val dayFirst = requireNotNull(resolve("03/04/25", "10:30", DateOrder.DAY_MONTH, kolkata))
        val monthFirst = requireNotNull(resolve("03/04/25", "10:30", DateOrder.MONTH_DAY, kolkata))
        assertEquals(Instant.parse("2025-04-03T05:00:00Z"), dayFirst.earliest)
        assertEquals(Instant.parse("2025-03-04T05:00:00Z"), monthFirst.earliest)
        assertEquals(Instant.parse("2025-04-03T05:00:59.999Z"), dayFirst.latest)
        assertEquals(TimePrecision.MINUTE, dayFirst.precision)
        assertEquals(kolkata, dayFirst.zone)
    }

    @Test
    fun secondsGiveSecondPrecision() {
        val bounds = requireNotNull(resolve("13/01/2025", "21:05:07", DateOrder.DAY_MONTH, kolkata))
        assertEquals(Instant.parse("2025-01-13T15:35:07Z"), bounds.earliest)
        assertEquals(Instant.parse("2025-01-13T15:35:07.999Z"), bounds.latest)
        assertEquals(TimePrecision.SECOND, bounds.precision)
    }

    @Test
    fun twelveHourMidnightAndNoon() {
        val midnight = requireNotNull(resolve("13/01/2025", "12:15 AM", DateOrder.DAY_MONTH, ZoneId.of("UTC")))
        assertEquals(Instant.parse("2025-01-13T00:15:00Z"), midnight.earliest)
        val noon = requireNotNull(resolve("13/01/2025", "12:15 PM", DateOrder.DAY_MONTH, ZoneId.of("UTC")))
        assertEquals(Instant.parse("2025-01-13T12:15:00Z"), noon.earliest)
        val evening = requireNotNull(resolve("13/01/2025", "9:05 pm", DateOrder.DAY_MONTH, ZoneId.of("UTC")))
        assertEquals(Instant.parse("2025-01-13T21:05:00Z"), evening.earliest)
    }

    @Test
    fun twoDigitYearMapsInto2000s() {
        val bounds = requireNotNull(resolve("01/02/99", "10:00", DateOrder.DAY_MONTH, ZoneId.of("UTC")))
        assertEquals(Instant.parse("2099-02-01T10:00:00Z"), bounds.earliest)
    }

    @Test
    fun invalidDatesAndTimesReturnNull() {
        assertNull(resolve("31/02/2025", "10:00", DateOrder.DAY_MONTH, kolkata))
        assertNull(resolve("13/13/2025", "10:00", DateOrder.DAY_MONTH, kolkata))
        assertNull(resolve("13/01/2025", "13:00 PM", DateOrder.DAY_MONTH, kolkata))
        assertNull(resolve("13/01/2025", "0:30 AM", DateOrder.DAY_MONTH, kolkata))
        assertNull(resolve("13/01/2025", "24:00", DateOrder.DAY_MONTH, kolkata))
        assertNull(resolve("13/01/2025", "10:61", DateOrder.DAY_MONTH, kolkata))
    }

    @Test
    fun zoneComesFromTheSuppliedZoneId() {
        val summer = requireNotNull(resolve("01/07/2025", "12:00", DateOrder.DAY_MONTH, newYork))
        assertEquals(Instant.parse("2025-07-01T16:00:00Z"), summer.earliest)
        val winter = requireNotNull(resolve("01/12/2025", "12:00", DateOrder.DAY_MONTH, newYork))
        assertEquals(Instant.parse("2025-12-01T17:00:00Z"), winter.earliest)
    }

    @Test
    fun dstGapReturnsNullAndOverlapSpansBothOccurrences() {
        assertNull(resolve("09/03/2025", "02:30", DateOrder.DAY_MONTH, newYork))
        val overlap = requireNotNull(resolve("02/11/2025", "01:30", DateOrder.DAY_MONTH, newYork))
        assertEquals(Instant.parse("2025-11-02T05:30:00Z"), overlap.earliest)
        assertEquals(Instant.parse("2025-11-02T06:30:59.999Z"), overlap.latest)
    }

    @Test
    fun ambiguousOrderIsRejected() {
        assertFailsWith<IllegalArgumentException> { resolve("01/02/2025", "10:00", DateOrder.AMBIGUOUS, kolkata) }
    }
}
