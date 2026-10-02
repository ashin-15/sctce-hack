package org.sakshi.app.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/** Formats an ISO-8601 instant as a medium date in [locale] and [zone], or returns null if it cannot be parsed. */
fun formatCreatedDate(iso: String, locale: Locale, zone: ZoneId): String? = try {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(zone).format(Instant.parse(iso))
} catch (e: DateTimeParseException) {
    null
}
