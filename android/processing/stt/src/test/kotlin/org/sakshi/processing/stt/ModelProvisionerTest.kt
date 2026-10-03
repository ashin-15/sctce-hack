package org.sakshi.processing.stt

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class ModelProvisionerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val spec = testSpec(TEST_MODEL_BYTES)

    private val modelsDirectory: File by lazy { File(folder.root, "models") }

    private fun provisioner(): ModelProvisioner = ModelProvisioner(modelsDirectory, spec)

    private fun filesIn(directory: File): List<String> = directory.list()?.sorted().orEmpty()

    @Test
    fun installsTheVerifiedModelAtomically() {
        val target = provisioner()
        val result = target.importFrom(ByteArrayInputStream(TEST_MODEL_BYTES))
        assertEquals(ProvisionResult.Installed(target.modelFile), result)
        assertTrue(target.isProvisioned())
        assertTrue(target.modelFile.readBytes().contentEquals(TEST_MODEL_BYTES))
        assertEquals(listOf(spec.fileName), filesIn(modelsDirectory))
    }

    @Test
    fun partialFileIsNeverTheModelFileWhileCopying() {
        val target = provisioner()
        var observedModelWhileCopying = true
        var observedPartial = false
        val watching = object : InputStream() {
            private val inner = ByteArrayInputStream(TEST_MODEL_BYTES)

            override fun read(): Int = inner.read()

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val count = inner.read(buffer, offset, minOf(length, 100))
                observedModelWhileCopying = observedModelWhileCopying && target.isProvisioned()
                observedPartial = observedPartial || File(modelsDirectory, spec.fileName + ".partial").exists()
                return count
            }
        }
        target.importFrom(watching)
        assertFalse(observedModelWhileCopying, "the model name must not exist before verification")
        assertTrue(observedPartial, "bytes go to a .partial file first")
    }

    @Test
    fun hashMismatchLeavesNothingBehind() {
        val target = provisioner()
        val wrong = TEST_MODEL_BYTES.copyOf().also { it[100] = (it[100] + 1).toByte() }
        assertEquals(ProvisionResult.HashMismatch, target.importFrom(ByteArrayInputStream(wrong)))
        assertFalse(target.isProvisioned())
        assertEquals(emptyList(), filesIn(modelsDirectory))
    }

    @Test
    fun shortAndOversizedInputsAreRefusedAndLeaveNothing() {
        val target = provisioner()
        assertEquals(ProvisionResult.HashMismatch, target.importFrom(ByteArrayInputStream(TEST_MODEL_BYTES.copyOf(10))))
        assertEquals(ProvisionResult.TooLarge, target.importFrom(ByteArrayInputStream(TEST_MODEL_BYTES + byteArrayOf(1))))
        assertEquals(emptyList(), filesIn(modelsDirectory))
    }

    @Test
    fun streamFailureMidCopyLeavesNothingBehind() {
        val target = provisioner()
        val failing = object : InputStream() {
            private var served = 0

            override fun read(): Int = throw IOException("unused")

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (served >= 500) throw IOException("stream broke")
                val count = minOf(length, 500 - served)
                System.arraycopy(TEST_MODEL_BYTES, served, buffer, offset, count)
                served += count
                return count
            }
        }
        assertEquals(ProvisionResult.IoFailed, target.importFrom(failing))
        assertEquals(emptyList(), filesIn(modelsDirectory))
    }

    @Test
    fun aFailedImportKeepsTheModelThatWasAlreadyInstalled() {
        val target = provisioner()
        target.importFrom(ByteArrayInputStream(TEST_MODEL_BYTES))
        assertEquals(ProvisionResult.HashMismatch, target.importFrom(ByteArrayInputStream(ByteArray(TEST_MODEL_BYTES.size))))
        assertTrue(target.modelFile.readBytes().contentEquals(TEST_MODEL_BYTES))
        assertEquals(listOf(spec.fileName), filesIn(modelsDirectory))
    }

    @Test
    fun removeDeletesTheModelAndAnyPartialFile() {
        val target = provisioner()
        target.importFrom(ByteArrayInputStream(TEST_MODEL_BYTES))
        File(modelsDirectory, spec.fileName + ".partial").writeBytes(byteArrayOf(1))
        assertTrue(target.remove())
        assertEquals(emptyList(), filesIn(modelsDirectory))
    }

    @Test
    fun theShippedSpecPinsTheRecordedHashAndSize() {
        assertEquals("422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898", ModelSpec.WHISPER_BASE_Q5_1.sha256)
        assertEquals(59_707_625L, ModelSpec.WHISPER_BASE_Q5_1.sizeBytes)
    }
}
