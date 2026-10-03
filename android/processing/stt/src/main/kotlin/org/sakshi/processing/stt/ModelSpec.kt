package org.sakshi.processing.stt

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** A speech model the app may load: its identity and the SHA-256 and exact size of the file that is accepted. */
public data class ModelSpec(
    public val id: String,
    public val fileName: String,
    public val sha256: String,
    public val sizeBytes: Long,
) {
    init {
        require(sha256.matches(SHA256_HEX)) { "sha256 must be 64 lower-case hex digits" }
        require(fileName.matches(SAFE_NAME)) { "fileName must be a plain file name" }
        require(sizeBytes > 0) { "sizeBytes must be positive" }
    }

    public companion object {
        private val SHA256_HEX: Regex = Regex("[0-9a-f]{64}")
        private val SAFE_NAME: Regex = Regex("[A-Za-z0-9._-]+")

        /**
         * whisper.cpp multilingual base model, 5-bit quantised (q5_1), from the `ggerganov/whisper.cpp` Hugging Face
         * repository. The hash was recorded on 3 October 2026 from the file the project's preparation step fetched.
         */
        public val WHISPER_BASE_Q5_1: ModelSpec = ModelSpec(
            id = "whisper.cpp-ggml-base-q5_1",
            fileName = "ggml-base-q5_1.bin",
            sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
            sizeBytes = 59_707_625L,
        )
    }
}

/** SHA-256 helpers. */
public object Sha256 {
    private const val BUFFER_BYTES: Int = 64 * 1024

    public fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /** Streams [input] to its end and returns the lower-case hex digest. */
    @Throws(IOException::class)
    public fun hexOf(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return hex(digest.digest())
    }

    @Throws(IOException::class)
    public fun hexOf(file: File): String = file.inputStream().use(::hexOf)
}
