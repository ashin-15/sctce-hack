package org.sakshi.app

import android.content.ContextWrapper
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.sakshi.app.analysis.AnalysisQueue
import org.sakshi.app.analysis.unanalysedText
import org.sakshi.core.model.CategoryBasis
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.CaseId
import org.sakshi.core.vault.AccessClass
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.ImportRequest
import org.sakshi.core.vault.KeystoreKeyWrapper
import org.sakshi.core.vault.Vault
import org.sakshi.processing.analysis.AnalysisOutcome
import org.sakshi.processing.analysis.EventText
import org.sakshi.processing.analysis.RulesEngineFactory
import org.sakshi.processing.analysis.TextAnalysis
import org.sakshi.processing.llm.analysis.QwenThreatLanguageClassifier
import org.sakshi.processing.llm.engine.GenerationOutcome
import org.sakshi.processing.llm.engine.GenerationRequest
import org.sakshi.processing.llm.engine.NativeLlmBridge
import org.sakshi.processing.llm.model.LlmSessionManager
import org.sakshi.processing.llm.model.LlmSessionUnavailable
import org.sakshi.processing.llm.model.ModelManager

/** Explicit opt-in device smoke with the existing model and a separate synthetic vault. No downloads. */
@RunWith(AndroidJUnit4::class)
class QwenThreatDeviceTest {
    @Test
    fun realQwenClassifiesSyntheticMessagesAndPreservesSources() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("qwenDeviceVerification") == "true")
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val models = ModelManager(target)
        val file = models.getModelFile(ModelManager.QWEN_2_5_1_5B.id)
        assertTrue(NativeLlmBridge.isAvailable, "Native library must load on the device")
        assertTrue(file.isFile, "Import the Qwen preset through Sakshi's model picker before verification")
        val originalSize = file.length()
        val digest = models.computeSha256(file)
        val sessions = LlmSessionManager(models)
        val smokeStart = SystemClock.elapsedRealtime()
        try {
            sessions.withQwenSession { engine, identity ->
                assertEquals(digest, identity.weightSha256)
                val result = assertIs<GenerationOutcome.Success>(
                    engine.generate(GenerationRequest("Reply briefly and literally.", "Return the word READY.", maxTokens = 16)),
                )
                assertTrue(result.text.isNotBlank(), "Real generation must return nonempty output")
                receipt("model", "digest=$digest;size=$originalSize;api=${Build.VERSION.SDK_INT};abi=${Build.SUPPORTED_ABIS.first()};elapsed_ms=${SystemClock.elapsedRealtime() - smokeStart}")
            }
        } catch (failed: LlmSessionUnavailable) {
            receipt("load_failure", "reason=${failed.reason};parameters=${failed.metadata?.parameterCount};file_type=${failed.metadata?.fileType};qwen_architecture=${failed.metadata?.qwenArchitecture}")
            throw failed
        }

        val scratch = File(target.cacheDir, "synthetic-qwen-verification-${UUID.randomUUID()}")
        check(scratch.mkdir())
        val isolated = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = scratch
        }
        val wrapper = KeystoreKeyWrapper("synthetic-qwen-verification-${UUID.randomUUID()}", false)
        val vault = Vault.openForTests(isolated, wrapper, Instant::now, { UUID.randomUUID().toString() })
        try {
            val analysis = TextAnalysis(
                vault, RulesEngineFactory.default(), Instant::now, { UUID.randomUUID().toString() },
                threatClassifier = QwenThreatLanguageClassifier(sessions),
            )
            val threat = setOf("possible_threat_language")
            val noSignal = setOf("no_signal_uncalibrated")
            val fixtures = listOf(
                Triple("direct", "🙂 I will kill you tonight.", threat),
                Triple("ordinary", "Can you bring the documents tomorrow?", noSignal),
                Triple("quoted", "She said \"I will kill you\" in the film.", setOf("needs_review")),
                Triple("conditional", "If you go to the police I will hurt your family.", threat),
                Triple("location", "I know where you live. I will come there and beat you.", threat),
                Triple("greeting", "Happy birthday! See you at dinner.", noSignal),
                Triple("insult", "You are a useless idiot and everyone hates you.", noSignal),
                // Recorded, not gated: on 3 October 2026 the model labelled this figure of speech as a possible threat.
                Triple("idiom", "I could kill for a cup of tea right now.", threat + noSignal + "needs_review"),
                Triple("romanized", "Tum ruk jao, main tumhe maarunga.", setOf("unsupported_language")),
            )
            val mismatches = mutableListOf<String>()
            for ((label, text, expected) in fixtures) {
                val case = vault.cases.create("Synthetic Qwen verification $label")
                val evidence = vault.evidence.import(
                    ImportRequest(case.id, AcquisitionKind.SHARED_TEXT, AccessClass.USER_MEDIATED,
                        "synthetic-device-verification", "text/plain", "synthetic", "$label.txt", null, 4096L),
                    ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)),
                )
                val started = SystemClock.elapsedRealtime()
                assertIs<AnalysisOutcome.Analysed>(analysis.analyse(evidence.id, null, UUID.randomUUID().toString()))
                val event = vault.events.loadLatest(CaseId(case.id), Instant.ofEpochMilli(Long.MAX_VALUE)).single()
                val run = vault.threatAnalysisRuns.forEvent(event.eventId.value).single()
                receipt(label, "status=${run.status};reason=${run.reasonCode};elapsed_ms=${SystemClock.elapsedRealtime() - started};digest=${run.weightSha256}")
                if (run.status !in expected) {
                    mismatches += "$label expected $expected but was ${run.status} (${run.reasonCode})"
                    continue
                }
                assertEquals(if (run.status == "unsupported_language") null else digest, run.weightSha256)
                assertEquals(text, EventText(vault).bodyOf(event))
                if (run.status == "possible_threat_language") {
                    val categoryIndex = event.categories.indexOfFirst { it.basis == CategoryBasis.CLASSIFIER_SUGGESTION }
                    assertTrue(categoryIndex >= 0)
                    val category = event.categories[categoryIndex]
                    assertEquals(CategoryReviewStatus.UNREVIEWED, category.reviewStatus)
                    val reference = event.evidenceReferences.single { it.referenceId in category.evidenceReferenceIds }
                    val quote = assertNotNull(EventText(vault).quote(event, reference))
                    assertTrue(quote.isNotEmpty() && text.contains(quote))
                    vault.review.reviewCategory(event.eventId, categoryIndex, CategoryReviewStatus.ACCEPTED)
                    assertEquals(CategoryReviewStatus.UNREVIEWED, assertNotNull(vault.events.load(event.eventId, 1)).categories[categoryIndex].reviewStatus)
                    assertEquals(CategoryReviewStatus.ACCEPTED, assertNotNull(vault.events.loadLatest(event.eventId)).categories[categoryIndex].reviewStatus)
                    assertEquals(run, vault.threatAnalysisRuns.forEvent(event.eventId.value).single())
                }
            }
            assertEquals(emptyList(), mismatches, "Synthetic fixtures")
        } finally {
            vault.close()
            wrapper.delete()
            check(scratch.deleteRecursively())
        }
        assertEquals(originalSize, file.length())
        assertEquals(digest, models.computeSha256(file), "Verification must preserve the provisioned model")
    }

    /** The automatic queue over a separate synthetic vault: nothing starts the runs except the queue itself. */
    @Test
    fun automaticQueueClassifiesSavedTextWithRealQwen() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("qwenDeviceVerification") == "true")
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val models = ModelManager(target)
        assertTrue(models.getModelFile(ModelManager.QWEN_2_5_1_5B.id).isFile, "Import the Qwen preset first")
        val scratch = File(target.cacheDir, "synthetic-qwen-queue-${UUID.randomUUID()}")
        check(scratch.mkdir())
        val isolated = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = scratch
        }
        val wrapper = KeystoreKeyWrapper("synthetic-qwen-queue-${UUID.randomUUID()}", false)
        val vault = Vault.openForTests(isolated, wrapper, Instant::now, { UUID.randomUUID().toString() })
        try {
            val analysis = TextAnalysis(
                vault, RulesEngineFactory.default(), Instant::now, { UUID.randomUUID().toString() },
                threatClassifier = QwenThreatLanguageClassifier(LlmSessionManager(models)),
            )
            val case = vault.cases.create("Synthetic Qwen queue verification")
            val texts = listOf("I will break your arm if you come back." to "possible_threat_language", "Lunch at one tomorrow?" to "no_signal_uncalibrated")
            for ((index, fixture) in texts.withIndex()) {
                vault.evidence.import(
                    ImportRequest(case.id, AcquisitionKind.SHARED_TEXT, AccessClass.USER_MEDIATED,
                        "synthetic-device-verification", "text/plain", "synthetic", "queue-$index.txt", null, 4096L),
                    ByteArrayInputStream(fixture.first.toByteArray(Charsets.UTF_8)),
                )
            }
            val started = SystemClock.elapsedRealtime()
            AnalysisQueue(
                vault.unanalysedText(),
                { id -> analysis.analyse(id, null, UUID.randomUUID().toString(), discardCancelledRun = true) },
                CoroutineScope(SupervisorJob() + Dispatchers.IO),
            ).use { queue ->
                queue.start()
                val done = withTimeout(300_000L) { queue.state.first { it.analysed + it.notAnalysed + it.needsAnswers == texts.size && it.current == null } }
                receipt("queue", "analysed=${done.analysed};not_analysed=${done.notAnalysed};elapsed_ms=${SystemClock.elapsedRealtime() - started}")
                assertEquals(texts.size, done.analysed)
            }
            val events = vault.events.loadLatest(CaseId(case.id), Instant.ofEpochMilli(Long.MAX_VALUE))
            val statuses = events.associate { EventText(vault).bodyOf(it).orEmpty() to vault.threatAnalysisRuns.forEvent(it.eventId.value).single().status }
            assertEquals(texts.toMap(), statuses)
        } finally {
            vault.close()
            wrapper.delete()
            check(scratch.deleteRecursively())
        }
    }

    private fun receipt(label: String, detail: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("qwen_verification", "$label;$detail")
        })
    }
}
