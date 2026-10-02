package org.sakshi.core.model

import java.io.File

internal object TestSupport {
    fun fixture(name: String): String =
        File(checkNotNull(System.getProperty("sakshi.fixtures")) { "sakshi.fixtures not set" }, name).readText()

    fun schemaText(): String =
        File(checkNotNull(System.getProperty("sakshi.eventSchema")) { "sakshi.eventSchema not set" }).readText()

    fun validEvent(): Event = EventSchemaAdapter.fromJson(fixture("event-valid.json"))
}
