package org.sakshi.processing.stt

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

public sealed interface ProvisionResult {
    /** The model passed the hash check and is at [file]. */
    public data class Installed(public val file: File) : ProvisionResult

    /** The bytes were not the pinned model. Nothing was kept. */
    public data object HashMismatch : ProvisionResult

    /** More bytes arrived than the model has. Nothing was kept. */
    public data object TooLarge : ProvisionResult

    public data object IoFailed : ProvisionResult
}

/**
 * Puts the model file in app-private storage from a stream the user chose (for example from a file picker). The model is
 * never bundled in the APK and never downloaded by this module: provisioning is an explicit preparation step.
 *
 * The bytes go to a `.partial` file while they are hashed and are renamed into place only when the hash is the pinned
 * one, so [modelFile] never names a half-written or unverified file.
 */
public class ModelProvisioner(private val directory: File, private val spec: ModelSpec = ModelSpec.WHISPER_BASE_Q5_1) {
    /** The file a verified model lives in; it may not exist. */
    public val modelFile: File = File(directory, spec.fileName)

    private val partialFile: File = File(directory, spec.fileName + PARTIAL_SUFFIX)

    public fun isProvisioned(): Boolean = modelFile.isFile

    /** Copies [input] in, verifies it and installs it. [input] is read to its end but not closed. */
    public fun importFrom(input: InputStream): ProvisionResult {
        try {
            if (!directory.isDirectory && !directory.mkdirs()) return ProvisionResult.IoFailed
            val outcome = copyAndHash(input)
            return if (outcome == null) install() else outcome.also { partialFile.delete() }
        } catch (e: IOException) {
            partialFile.delete()
            return ProvisionResult.IoFailed
        }
    }

    /** Removes the installed model and any leftover partial file. */
    public fun remove(): Boolean {
        val partial = !partialFile.exists() || partialFile.delete()
        val model = !modelFile.exists() || modelFile.delete()
        return partial && model
    }

    /** Null when the partial file holds exactly the pinned model; otherwise the reason it must be discarded. */
    private fun copyAndHash(input: InputStream): ProvisionResult? {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        FileOutputStream(partialFile).use { out ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > spec.sizeBytes) return ProvisionResult.TooLarge
                digest.update(buffer, 0, read)
                out.write(buffer, 0, read)
            }
            out.fd.sync()
        }
        return if (total == spec.sizeBytes && Sha256.hex(digest.digest()) == spec.sha256) null else ProvisionResult.HashMismatch
    }

    private fun install(): ProvisionResult {
        try {
            Files.move(partialFile.toPath(), modelFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            partialFile.delete()
            return ProvisionResult.IoFailed
        }
        return ProvisionResult.Installed(modelFile)
    }

    public companion object {
        private const val BUFFER_BYTES: Int = 64 * 1024
        private const val PARTIAL_SUFFIX: String = ".partial"
        private const val MODELS_DIRECTORY: String = "models"

        /** The provisioner for the app's private, backup-excluded models directory (`noBackupFilesDir/models`). */
        public fun forContext(context: Context, spec: ModelSpec = ModelSpec.WHISPER_BASE_Q5_1): ModelProvisioner =
            ModelProvisioner(File(context.noBackupFilesDir, MODELS_DIRECTORY), spec)
    }
}
