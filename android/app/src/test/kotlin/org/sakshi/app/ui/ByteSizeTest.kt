package org.sakshi.app.ui

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class ByteSizeTest {
    @Test
    fun smallSizesAreBytes() {
        assertEquals("0 B", formatByteSize(0, Locale.US))
        assertEquals("1,023 B", formatByteSize(1023, Locale.US))
    }

    @Test
    fun usesStepsOf1024() {
        assertEquals("1 KB", formatByteSize(1024, Locale.US))
        assertEquals("1.5 KB", formatByteSize(1536, Locale.US))
        assertEquals("1 MB", formatByteSize(1024L * 1024, Locale.US))
        assertEquals("100 MB", formatByteSize(100L * 1024 * 1024, Locale.US))
    }

    @Test
    fun roundingNeverShowsMoreThanTheUnitAllows() {
        assertEquals("1 MB", formatByteSize(1024L * 1024 - 1, Locale.US))
    }

    @Test
    fun numbersFollowTheLocale() {
        assertEquals("1,5 KB", formatByteSize(1536, Locale.GERMANY))
        assertEquals("1.023 B", formatByteSize(1023, Locale.GERMANY))
    }

    @Test
    fun aNegativeSizeIsTreatedAsZero() {
        assertEquals("0 B", formatByteSize(-5, Locale.US))
    }
}
