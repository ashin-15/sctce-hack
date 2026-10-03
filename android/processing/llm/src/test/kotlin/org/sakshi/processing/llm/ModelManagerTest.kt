package org.sakshi.processing.llm

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sakshi.core.integrity.Sha256
import org.sakshi.processing.llm.engine.DeterministicFallbackEngine
import org.sakshi.processing.llm.engine.LlmStatus
import org.sakshi.processing.llm.model.ModelManager

public class ModelManagerTest {

    @Test
    public fun directoryManagementAndFileLookup(): Unit {
        val tempDir = Files.createTempDirectory("models_test").toFile()
        try {
            val manager = ModelManager(tempDir)
            val modelId = "tinyllm-q4.gguf"
            val modelFile = manager.getModelFile(modelId)

            assertFalse(manager.hasModel(modelId))
            modelFile.writeText("fake-model-binary-content")
            assertTrue(manager.hasModel(modelId))

            val files = manager.listModelFiles()
            assertEquals(1, files.size)
            assertEquals(modelFile.absolutePath, files[0].absolutePath)

            assertTrue(manager.deleteModel(modelId))
            assertFalse(manager.hasModel(modelId))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    public fun sha256VerificationWithKnownContent(): Unit {
        val tempDir = Files.createTempDirectory("checksum_test").toFile()
        try {
            val manager = ModelManager(tempDir)
            val modelFile = File(tempDir, "sample.bin")
            val content = "sakshi-test-model-bytes".toByteArray(Charsets.UTF_8)
            modelFile.writeBytes(content)

            val expectedHash = Sha256.hex(Sha256.digest(content))
            val computedHash = manager.computeSha256(modelFile)

            assertEquals(expectedHash, computedHash)
            assertTrue(manager.verifyChecksum(modelFile, expectedHash))
            assertFalse(manager.verifyChecksum(modelFile, "0000000000000000000000000000000000000000000000000000000000000000"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    public fun activeContextEvictionOnTrimMemoryCritical(): Unit {
        val tempDir = Files.createTempDirectory("trim_test").toFile()
        try {
            val manager = ModelManager(tempDir)
            val engine = DeterministicFallbackEngine()
            assertEquals(LlmStatus.Ready, engine.status)

            manager.registerActiveEngine(engine)

            var callbackInvoked = false
            manager.addEvictionCallback { callbackInvoked = true }

            // Non-critical trim level (10) does not evict
            val lowTrimHandled = manager.onTrimMemory(10)
            assertFalse(lowTrimHandled)
            assertEquals(LlmStatus.Ready, engine.status)
            assertFalse(callbackInvoked)

            // Critical trim level evicts active context
            val criticalTrimHandled = manager.onTrimMemory(ModelManager.TRIM_MEMORY_RUNNING_CRITICAL)
            assertTrue(criticalTrimHandled)
            assertEquals(LlmStatus.Unloaded, engine.status)
            assertTrue(callbackInvoked)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    public fun importModelStreamSavesAndReportsProgress(): Unit {
        val tempDir = Files.createTempDirectory("import_test").toFile()
        try {
            val manager = ModelManager(tempDir)
            val sampleBytes = "test-gguf-model-payload-content".toByteArray(Charsets.UTF_8)
            val inputStream = java.io.ByteArrayInputStream(sampleBytes)
            var reportedProgress = 0L

            val importedFile = manager.importModelStream(
                sourceStream = inputStream,
                targetFileName = "imported.gguf",
                onProgress = { reportedProgress = it },
            )

            assertTrue(importedFile.exists())
            assertEquals("imported.gguf", importedFile.name)
            assertEquals(sampleBytes.size.toLong(), importedFile.length())
            assertEquals(sampleBytes.size.toLong(), reportedProgress)
            assertTrue(manager.hasModel("imported.gguf"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    public fun modelPresetsAreConfiguredCorrectly(): Unit {
        val presets = ModelManager.PRESETS
        assertEquals(2, presets.size)
        val qwen = presets.first { it.id == ModelManager.QWEN_2_5_1_5B.id }
        assertTrue(qwen.isRecommended)
        assertTrue(qwen.downloadUrl.startsWith("https://"))
        assertTrue(qwen.filename.endsWith(".gguf"))
    }
}
