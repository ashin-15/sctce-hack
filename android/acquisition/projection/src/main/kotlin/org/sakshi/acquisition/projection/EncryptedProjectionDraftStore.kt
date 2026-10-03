package org.sakshi.acquisition.projection

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import android.os.SystemClock
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Session-key-only encrypted staging. The key is never persisted or included in Android state. */
public class EncryptedProjectionDraftStore(
    context: Context,
    public val limits: ProjectionCaptureLimits = ProjectionCaptureLimits(),
    private val random: SecureRandom = SecureRandom(),
) {
    private val directory: File = File(context.applicationContext.noBackupFilesDir, "projection-drafts")
    private val lock: Any = Any()
    private val mutableDrafts = MutableStateFlow<List<ProjectionDraft>>(emptyList())
    public val drafts: StateFlow<List<ProjectionDraft>> = mutableDrafts.asStateFlow()
    private var sessionId: String? = null
    private var keyBytes: ByteArray? = null
    private var totalBytes: Long = 0

    init {
        directory.listFiles()?.forEach(File::delete)
        directory.mkdirs()
    }

    public fun beginSession(id: String): Unit = synchronized(lock) {
        check(mutableDrafts.value.isEmpty() && keyBytes == null) { "Review or discard the previous capture before starting again" }
        UUID.fromString(id)
        sessionId = id
        keyBytes = ByteArray(32).also(random::nextBytes)
        totalBytes = 0
    }

    public suspend fun stage(frame: CapturedFrame): ProjectionDraft = withContext(Dispatchers.IO) {
        val png = frame.imageBytes
        require(frame.width > 0 && frame.height > 0) { "Invalid frame dimensions" }
        require(frame.width.toLong() * frame.height <= limits.maxPixels) { "Frame exceeds pixel limit" }
        require(png.size <= limits.maxFrameBytes) { "Frame exceeds encoded byte limit" }
        val digest = sha256(png)
        require(digest.equals(frame.sha256Hex, ignoreCase = true)) { "Frame digest mismatch" }
        synchronized(lock) {
            val activeSession = checkNotNull(sessionId) { "No active capture session" }
            require(mutableDrafts.value.size < limits.maxFrames) { "Frame count limit reached" }
            require(totalBytes + png.size <= limits.maxSessionBytes) { "Session byte limit reached" }
            val draft = ProjectionDraft(
                UUID.randomUUID().toString(), activeSession, frame.frameIndex, frame.timestampMs,
                frame.width, frame.height, digest, png.size, frame.kind == FrameKind.BLANK,
                frame.elapsedRealtimeMs.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            )
            writeEncrypted(draft, png, checkNotNull(keyBytes))
            totalBytes += png.size
            mutableDrafts.value = mutableDrafts.value + draft
            draft
        }
    }

    public suspend fun read(id: String): ByteArray = withContext(Dispatchers.IO) {
        val draft: ProjectionDraft
        val key: ByteArray
        synchronized(lock) {
            draft = checkNotNull(mutableDrafts.value.firstOrNull { it.id == id }) { "Unknown or expired draft" }
            key = checkNotNull(keyBytes) { "Draft key is unavailable" }.copyOf()
        }
        try {
            val encrypted = File(directory, id + ".bin").readBytes()
            val input = DataInputStream(ByteArrayInputStream(encrypted))
            val nonceSize = input.readInt()
            require(nonceSize == NONCE_BYTES) { "Invalid draft envelope" }
            val nonce = ByteArray(nonceSize).also(input::readFully)
            val ciphertext = ByteArray(input.available()).also(input::readFully)
            val clear = cipher(Cipher.DECRYPT_MODE, key, nonce, aad(draft)).doFinal(ciphertext)
            DataInputStream(ByteArrayInputStream(clear)).use { payload ->
                val metadataSize = payload.readInt()
                require(metadataSize in 1..MAX_METADATA_BYTES) { "Invalid draft metadata" }
                val metadata = ByteArray(metadataSize).also(payload::readFully)
                require(String(metadata, Charsets.UTF_8) == metadataJson(draft)) { "Draft metadata mismatch" }
                val png = ByteArray(payload.available()).also(payload::readFully)
                require(png.size == draft.byteSize && sha256(png) == draft.sha256) { "Draft image integrity check failed" }
                png
            }
        } finally {
            key.fill(0)
        }
    }

    public fun discard(id: String): Unit = synchronized(lock) {
        val draft = mutableDrafts.value.firstOrNull { it.id == id } ?: return@synchronized
        File(directory, id + ".bin").delete()
        totalBytes = (totalBytes - draft.byteSize).coerceAtLeast(0)
        mutableDrafts.value = mutableDrafts.value.filterNot { it.id == id }
        if (mutableDrafts.value.isEmpty()) discardSessionLocked()
    }

    public fun clear(): Unit = synchronized(lock) { discardSessionLocked() }

    public fun activeSessionId(): String? = synchronized(lock) { sessionId }

    private fun writeEncrypted(draft: ProjectionDraft, png: ByteArray, secret: ByteArray) {
        val metadata = metadataJson(draft).toByteArray(Charsets.UTF_8)
        val clear = ByteArrayOutputStream(metadata.size + png.size + 4)
        DataOutputStream(clear).use { output -> output.writeInt(metadata.size); output.write(metadata); output.write(png) }
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val ciphertext = cipher(Cipher.ENCRYPT_MODE, secret, nonce, aad(draft)).doFinal(clear.toByteArray())
        val temporary = File(directory, draft.id + ".partial")
        val destination = File(directory, draft.id + ".bin")
        DataOutputStream(temporary.outputStream()).use { output ->
            output.writeInt(nonce.size)
            output.write(nonce)
            output.write(ciphertext)
        }
        check(temporary.renameTo(destination)) { "Could not publish encrypted draft" }
    }

    private fun discardSessionLocked() {
        keyBytes?.fill(0)
        keyBytes = null
        sessionId = null
        totalBytes = 0
        directory.listFiles()?.forEach(File::delete)
        mutableDrafts.value = emptyList()
    }

    private fun metadataJson(draft: ProjectionDraft): String = JSONObject()
        .put("version", VERSION).put("id", draft.id).put("session", draft.sessionId)
        .put("frame", draft.frameIndex).put("observed_at_ms", draft.observedAtMs)
        .put("width", draft.width).put("height", draft.height).put("sha256", draft.sha256)
        .put("size", draft.byteSize).put("blank_or_unavailable", draft.blankOrUnavailable)
        .put("elapsed_realtime_ms", draft.elapsedRealtimeMs).toString()

    private fun aad(draft: ProjectionDraft): ByteArray =
        ("sakshi-projection-draft:" + VERSION + ":" + draft.id + ":" + draft.sessionId).toByteArray()

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(aad)
        }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val VERSION = 1
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val MAX_METADATA_BYTES = 4096
    }
}
