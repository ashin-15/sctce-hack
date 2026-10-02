package org.sakshi.app.ui

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

private const val STEP = 1024L

/** Formats a size as B, KB or MB using steps of 1024, with at most one decimal place in [locale]. */
fun formatByteSize(bytes: Long, locale: Locale): String {
    val size = bytes.coerceAtLeast(0)
    if (size < STEP) return NumberFormat.getIntegerInstance(locale).format(size) + " B"
    val decimal = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }
    val kilobytes = size.toDouble() / STEP
    // 1023.96 KB would print as 1,024 KB, so it moves up a unit instead.
    if ((kilobytes * 10).roundToLong() < STEP * 10) return decimal.format(kilobytes) + " KB"
    return decimal.format(kilobytes / STEP) + " MB"
}
