package org.sakshi.processing.text

// All fixture content used by these tests is synthetic. No real conversation data is involved.

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class FixtureRow(
    val id: String,
    val text: String,
    val language: String,
    val split: String,
    val gold: Set<String>,
)

internal data class ExpectedRow(
    val id: String,
    val ruleLabels: Set<String>,
    val identify: String,
)

internal object TestData {
    private val repoRoot: File = File(requireNotNull(System.getProperty("sakshi.repoRoot")) { "sakshi.repoRoot not set" })

    val fixtures: List<FixtureRow> by lazy {
        File(repoRoot, "data/text.jsonl").readLines(Charsets.UTF_8).filter { it.isNotBlank() }.map {
            val row = Json.parseToJsonElement(it).jsonObject
            FixtureRow(
                id = row.getValue("id").jsonPrimitive.content,
                text = row.getValue("text").jsonPrimitive.content,
                language = row.getValue("language").jsonPrimitive.content,
                split = row.getValue("split").jsonPrimitive.content,
                gold = row.getValue("labels").jsonArray.strings(),
            )
        }
    }

    private val expectedDocument: JsonObject by lazy {
        val stream = requireNotNull(TestData::class.java.getResourceAsStream("/bench-rule-labels.json")) { "resource missing" }
        Json.parseToJsonElement(stream.readBytes().toString(Charsets.UTF_8)).jsonObject
    }

    val expected: Map<String, ExpectedRow> by lazy {
        expectedDocument.getValue("rows").jsonArray.map { it.jsonObject }.associate { row ->
            val id = row.getValue("id").jsonPrimitive.content
            id to ExpectedRow(id, row.getValue("rule_labels").jsonArray.strings(), row.getValue("identify").jsonPrimitive.content)
        }
    }

    /** Benchmark `RULES` as read by the generator script: wire label to phrases in order. */
    val benchRules: Map<String, List<String>> by lazy {
        expectedDocument.getValue("rules").jsonObject.mapValues { (_, phrases) -> phrases.jsonArray.strings().toList() }
    }

    private fun JsonArray.strings(): Set<String> = map { it.jsonPrimitive.content }.toSet()
}
