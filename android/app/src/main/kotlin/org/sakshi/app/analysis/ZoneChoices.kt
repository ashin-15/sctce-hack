package org.sakshi.app.analysis

import java.time.ZoneId

/** The time zones the person can pick from, filtered by what they typed. Matching ignores case and treats "_" as a space. */
object ZoneChoices {
    val all: List<String> by lazy { ZoneId.getAvailableZoneIds().sorted() }

    fun filter(query: String, zones: List<String> = all): List<String> {
        val needle = query.trim().lowercase().replace(' ', '_')
        return if (needle.isEmpty()) zones else zones.filter { needle in it.lowercase() }
    }
}
