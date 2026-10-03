package org.sakshi.acquisition.projection

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EncryptedProjectionDraftStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val directory = File(context.noBackupFilesDir, "projection-drafts")

    @After
    fun clean() {
        directory.deleteRecursively()
    }

    private fun frame(bytes: ByteArray): CapturedFrame = CapturedFrame(
        frameIndex = 1,
        timestampMs = 1_234,
        width = 2,
        height = 2,
        imageBytes = bytes,
        sha256Hex = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
        kind = FrameKind.NORMAL,
        elapsedRealtimeMs = 987,
    )

    @Test
    fun `draft encrypts bytes metadata and authenticates readback`() = runBlocking {
        val bytes = "synthetic sensitive capture bytes".toByteArray()
        val store = EncryptedProjectionDraftStore(context)
        val sessionId = UUID.randomUUID().toString()
        store.beginSession(sessionId)
        val draft = store.stage(frame(bytes))
        val ciphertext = File(directory, draft.id + ".bin").readBytes()

        assertEquals(sessionId, draft.sessionId)
        assertFalse(String(ciphertext).contains(String(bytes)))
        assertContentEquals(bytes, store.read(draft.id))

        ciphertext[ciphertext.lastIndex] = (ciphertext.last().toInt() xor 1).toByte()
        File(directory, draft.id + ".bin").writeBytes(ciphertext)
        assertFails { runBlocking { store.read(draft.id) } }
        store.clear()
        assertTrue(store.drafts.value.isEmpty())
    }

    @Test
    fun `a new process cannot recover ephemeral drafts and removes ciphertext`() = runBlocking {
        val store = EncryptedProjectionDraftStore(context)
        store.beginSession(UUID.randomUUID().toString())
        val draft = store.stage(frame("capture".toByteArray()))
        val file = File(directory, draft.id + ".bin")
        assertTrue(file.exists())

        val replacementProcess = EncryptedProjectionDraftStore(context)
        assertTrue(replacementProcess.drafts.value.isEmpty())
        assertFalse(file.exists())
    }
}
