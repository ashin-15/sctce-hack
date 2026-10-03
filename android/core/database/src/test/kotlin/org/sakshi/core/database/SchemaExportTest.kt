package org.sakshi.core.database

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

class SchemaExportTest {
    @Test
    fun exportedSchemaListsEveryTable() {
        val dir = File(checkNotNull(System.getProperty("sakshi.schemaDir")))
        val file = File(dir, "org.sakshi.core.database.SakshiDatabase/2.json")
        assertTrue(file.isFile, "missing $file")
        val json = file.readText()
        SakshiSchema.allTables.forEach { table ->
            assertTrue(json.contains("\"tableName\": \"$table\""), "schema export lacks $table")
        }
    }
}
