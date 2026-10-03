package org.sakshi.processing.llm

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sakshi.processing.llm.engine.DeterministicFallbackEngine
import org.sakshi.processing.llm.engine.GenerationFailureReason
import org.sakshi.processing.llm.engine.GenerationOutcome
import org.sakshi.processing.llm.engine.LlamaCppEngine
import org.sakshi.processing.llm.engine.LlmStatus
import org.sakshi.processing.llm.engine.InferenceLock
import org.sakshi.processing.llm.engine.ModelInfo
import org.sakshi.processing.llm.engine.NativeGenerationResult
import org.sakshi.processing.llm.engine.NativeLoadResult
import org.sakshi.processing.llm.engine.NativeLlmRuntime
import org.sakshi.processing.llm.tasks.IncidentExplainer
import org.sakshi.processing.llm.tasks.IncidentSummariser
import org.sakshi.processing.llm.tasks.StructuredExtractor

public class LlmTasksTest {

    @Test
    public fun incidentExplainerEndToEndWithDeterministicEngine(): Unit = runTest {
        val engine = DeterministicFallbackEngine()
        val explainer = IncidentExplainer(engine)

        val sourceId = "src-01"
        val sourceText = "You are a complete fraud and a liar."
        val labels = listOf("insult")

        val result = explainer.explain(sourceId, sourceText, labels)

        assertTrue(result.isSuccess)
        assertEquals(sourceId, result.sourceId)
        assertTrue(result.isClassifierSuggestion)
        assertFalse(result.legalClaimsDetected)
        assertTrue(result.explanation.contains("insult"))
        assertNotNull(result.supportingQuote)
        assertTrue(sourceText.contains(result.supportingQuote.orEmpty()))
        assertTrue(result.quoteValidated)
    }

    @Test
    public fun incidentExplainerDetectsAndRedactsLegalClaims(): Unit = runTest {
        val engine = DeterministicFallbackEngine {
            GenerationOutcome.Success(
                "The sender is guilty of criminal harassment because they said \"You are a fraud\".",
            )
        }
        val explainer = IncidentExplainer(engine)

        val sourceId = "src-01"
        val sourceText = "You are a fraud."
        val result = explainer.explain(sourceId, sourceText, listOf("insult"))

        assertTrue(result.isSuccess)
        assertTrue(result.legalClaimsDetected)
        assertFalse(result.explanation.contains("is guilty of"))
        assertTrue(result.explanation.contains("[legal claim redacted]"))
    }

    @Test
    public fun incidentExplainerFlagsHallucinatedQuote(): Unit = runTest {
        val engine = DeterministicFallbackEngine {
            GenerationOutcome.Success(
                "Flagged as threat based on \"I will eliminate you\".",
            )
        }
        val explainer = IncidentExplainer(engine)

        val sourceId = "src-01"
        val sourceText = "You are annoying."
        val result = explainer.explain(sourceId, sourceText, listOf("threat"))

        assertTrue(result.isSuccess)
        assertFalse(result.quoteValidated)
        assertNull(result.supportingQuote)
    }

    @Test
    public fun incidentSummariserEndToEndWithDeterministicEngine(): Unit = runTest {
        val engine = DeterministicFallbackEngine()
        val summariser = IncidentSummariser(engine)

        val sourceId = "src-01"
        val sourceText = "Please stop messaging me immediately."

        val result = summariser.summarise(sourceId, sourceText)

        assertTrue(result.isSuccess)
        assertEquals(3, result.sentences.size)
        val rubric = result.rubricResult
        assertNotNull(rubric)
        if (rubric != null) {
            assertTrue(rubric.isValid)
            assertTrue(rubric.points.all { it.passed })
        }
    }

    @Test
    public fun incidentSummariserRejectsRubricFailure(): Unit = runTest {
        val engine = DeterministicFallbackEngine {
            GenerationOutcome.Success("Only one sentence here.")
        }
        val summariser = IncidentSummariser(engine)

        val result = summariser.summarise("src-01", "Some text")

        assertFalse(result.isSuccess)
        assertNotNull(result.failureReason)
        val rubric = result.rubricResult
        assertNotNull(rubric)
        if (rubric != null) {
            assertFalse(rubric.isValid)
        }
    }

    @Test
    public fun structuredExtractorEndToEndWithDeterministicEngine(): Unit = runTest {
        val engine = DeterministicFallbackEngine()
        val extractor = StructuredExtractor(engine)

        val sourceId = "src-01"
        val sourceText = "Leave me alone."

        val result = extractor.extract(sourceId, sourceText)

        assertTrue(result.isSuccess)
        assertEquals(listOf(sourceId), result.sourceIds)
        assertNotNull(result.quote)
        assertTrue(result.isQuoteExactSubstring)
        assertTrue(sourceText.contains(result.quote.orEmpty()))
    }

    @Test
    public fun structuredExtractorRejectsHallucinatedQuote(): Unit = runTest {
        val engine = DeterministicFallbackEngine {
            GenerationOutcome.Success(
                """
                {
                  "date": null,
                  "platform": "WhatsApp",
                  "sender": "Unknown",
                  "threat_type": "insult",
                  "quote": "completely fabricated quote",
                  "source_ids": ["src-01"]
                }
                """.trimIndent(),
            )
        }
        val extractor = StructuredExtractor(engine)

        val result = extractor.extract("src-01", "Real text from source.")

        assertTrue(result.isSuccess)
        assertNull(result.quote)
        assertFalse(result.isQuoteExactSubstring)
        assertNotNull(result.failureReason)
    }

    @Test
    public fun tasksHandleEngineFailureGracefully(): Unit = runTest {
        val engine = DeterministicFallbackEngine()
        engine.close()
        assertEquals(LlmStatus.Unloaded, engine.status)

        val explainer = IncidentExplainer(engine)
        val summariser = IncidentSummariser(engine)
        val extractor = StructuredExtractor(engine)

        val explainResult = explainer.explain("src-01", "Text", listOf("insult"))
        assertFalse(explainResult.isSuccess)
        assertEquals("MODEL_NOT_READY", explainResult.failureReason)

        val summaryResult = summariser.summarise("src-01", "Text")
        assertFalse(summaryResult.isSuccess)
        assertEquals("MODEL_NOT_READY", summaryResult.failureReason)

        val extractResult = extractor.extract("src-01", "Text")
        assertFalse(extractResult.isSuccess)
        assertEquals("MODEL_NOT_READY", extractResult.failureReason)
    }

    @Test
    public fun llamaCppEngineReturnsModelNotReadyWhenLibraryUnavailable(): Unit = runTest {
        val engine = LlamaCppEngine()
        assertEquals(LlmStatus.Error, engine.status)

        val outcome = engine.generate(
            org.sakshi.processing.llm.engine.GenerationRequest(
                systemPrompt = "sys",
                userPrompt = "usr",
            ),
        )

        assertTrue(outcome is GenerationOutcome.Failed)
        assertEquals(GenerationFailureReason.MODEL_NOT_READY, (outcome as GenerationOutcome.Failed).reason)
        engine.close()
    }

    @Test
    public fun cancellationKeepsInferenceLockUntilNativeWorkerReturns(): Unit = runTest {
        val nativeStarted = CountDownLatch(1)
        val allowNativeReturn = CountDownLatch(1)
        val cancellationRequested = CountDownLatch(1)
        val runtime = object : NativeLlmRuntime {
            override val isAvailable: Boolean = true

            override fun initModel(modelPath: String, contextSize: Int) = NativeLoadResult(17L, NativeLoadResult.STATUS_OK)

            override fun freeModel(handle: Long): Unit = Unit

            override fun cancel(handle: Long, requestId: String): Unit {
                cancellationRequested.countDown()
            }

            override fun generate(
                handle: Long,
                systemPrompt: String,
                userPrompt: String,
                requestId: String,
                maxTokens: Int,
                grammar: String?,
                stopSequences: Array<String>,
            ): NativeGenerationResult {
                nativeStarted.countDown()
                check(allowNativeReturn.await(5, TimeUnit.SECONDS)) { "Test did not release native worker" }
                return NativeGenerationResult(NativeGenerationResult.STATUS_CANCELLED, "")
            }
        }
        val engine = LlamaCppEngine(
            modelPath = "synthetic-model.gguf",
            modelInfo = ModelInfo("synthetic-qwen", 1_500_000_000L, "Q4_K_M", 2048, "0".repeat(64)),
            bridge = runtime,
        )
        val first = async(Dispatchers.Default) {
            InferenceLock.withLock {
                engine.generate(
                    org.sakshi.processing.llm.engine.GenerationRequest("system", "user", maxTokens = 32),
                )
            }
        }
        try {
            assertTrue(nativeStarted.await(5, TimeUnit.SECONDS))
            first.cancel()
            assertTrue(cancellationRequested.await(5, TimeUnit.SECONDS))

            val secondLockAcquired = CompletableDeferred<Unit>()
            val second = async(Dispatchers.Default) {
                InferenceLock.withLock { secondLockAcquired.complete(Unit) }
            }
            assertFalse(secondLockAcquired.isCompleted)

            allowNativeReturn.countDown()
            first.join()
            second.await()
            assertTrue(secondLockAcquired.isCompleted)
        } finally {
            allowNativeReturn.countDown()
            engine.close()
        }
    }
}
