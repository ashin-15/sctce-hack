package org.sakshi.processing.text

// All fixture content used by this test is synthetic. No real conversation data is involved.

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BenchParityTest {
    private val engine = RulesEngine(BenchCueList.V1, requireReviewedCues = false)

    @Test
    fun cueListMatchesBenchRulesExactly() {
        val byLabel = BenchCueList.V1.cues.groupBy({ it.label.wire }, { it.phrase })
        assertEquals(TestData.benchRules.mapValues { it.value.toSet() }, byLabel.mapValues { it.value.toSet() })
        assertEquals(TestData.benchRules.values.sumOf { it.size }, BenchCueList.V1.cues.size)
        assertEquals("bench-rules-v1", BenchCueList.V1.version)
        assertTrue(BenchCueList.V1.reviewed.isEmpty())
    }

    @Test
    fun labelsEqualPythonRuleScoresOnAllFixtureRows() {
        assertEquals(600, TestData.fixtures.size)
        for (row in TestData.fixtures) {
            val actual = engine.labels(row.text).map { it.wire }.toSet()
            assertEquals(TestData.expected.getValue(row.id).ruleLabels, actual, "row ${row.id}: ${row.text}")
        }
    }

    @Test
    fun documentsRowsWhereRulesDifferFromGoldLabelsPerLanguage() {
        val differing = TestData.fixtures.filter { engine.labels(it.text).map { l -> l.wire }.toSet() != it.gold }
        val perLanguage = differing.groupingBy { it.language }.eachCount().toSortedMap()
        println("rules differ from gold (fixture regression count, not accuracy): $perLanguage total=${differing.size}")
        val expectedDiffering = TestData.fixtures.count { TestData.expected.getValue(it.id).ruleLabels != it.gold }
        assertEquals(expectedDiffering, differing.size)
    }

    @Test
    fun foldingMatchesPythonCasefoldForSharpS() {
        // Python: "Paßword".casefold() == "password"
        assertEquals(setOf(ProducerLabel.COERCIVE_CONTROL), engine.labels("Paßword"))
    }
}
